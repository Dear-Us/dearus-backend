#!/usr/bin/env python3
"""Run a disposable MariaDB and exercise guest authentication over real HTTP."""

import base64
import json
import os
import secrets
import signal
import subprocess
import tempfile
import time
import urllib.error
import urllib.request


def http(path, method="GET", body=None, token=None, content_type=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(
        "http://127.0.0.1:18082" + path,
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def main():
    name = "dearus-guest-qa-" + secrets.token_hex(4)
    password = secrets.token_urlsafe(20)
    java = os.environ.get("DEARUS_JAVA_BIN", "java")
    container_started = False
    app = None
    with tempfile.TemporaryFile(mode="w+t") as log:
        try:
            docker_env = os.environ.copy()
            docker_env["MARIADB_ROOT_PASSWORD"] = password
            run = subprocess.run(
                ["docker", "run", "--rm", "-d", "--name", name,
                 "-e", "MARIADB_ROOT_PASSWORD", "-e", "MARIADB_DATABASE=dearus",
                 "-p", "127.0.0.1::3306", "mariadb:11.4"],
                env=docker_env, capture_output=True, text=True, timeout=30,
            )
            if run.returncode:
                raise RuntimeError("Docker start failed: " + run.stderr.strip()[:300])
            container_started = True
            port_output = subprocess.check_output(
                ["docker", "port", name, "3306/tcp"], text=True, timeout=5,
            ).strip()
            db_port = int(port_output.rsplit(":", 1)[-1])
            db_env = os.environ.copy()
            db_env["MYSQL_PWD"] = password
            for _ in range(40):
                ready = subprocess.run(
                    ["mariadb", "--protocol=tcp", "--connect-timeout=2", "-h", "127.0.0.1",
                     "-P", str(db_port), "-u", "root", "-D", "dearus", "-e", "SELECT 1"],
                    env=db_env, capture_output=True, timeout=4,
                )
                if ready.returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("MariaDB did not accept SQL connections")

            app_env = os.environ.copy()
            app_env.update({
                "DB_URL": f"jdbc:mariadb://127.0.0.1:{db_port}/dearus",
                "DB_USERNAME": "root",
                "DB_PASSWORD": password,
                "DEARUS_JWT_SECRET_BASE64": base64.b64encode(secrets.token_bytes(32)).decode(),
                "KAKAO_APP_ID": "1",
                "PORT": "18082",
                "SWAGGER_ENABLED": "false",
            })
            app = subprocess.Popen(
                [java, "-jar", "build/libs/DearUsBack-0.0.1-SNAPSHOT.jar",
                 "--spring.main.banner-mode=off"],
                env=app_env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
            )
            for _ in range(45):
                if app.poll() is not None:
                    raise RuntimeError("Application exited before health check")
                try:
                    status, health = http("/actuator/health")
                    if status == 200 and health["status"] == "UP":
                        break
                except Exception:
                    time.sleep(1)
            else:
                raise RuntimeError("Application health check timed out")
            print("health: 200 UP")

            status, guest = http("/api/v1/auth/guest", method="POST", body=b"")
            assert status == 200 and guest["success"] and guest["data"]["isNewMember"]
            member_id = guest["data"]["member"]["memberId"]
            access = guest["data"]["accessToken"]
            refresh = guest["data"]["refreshToken"]
            print("guest_login: 200")

            status, profile = http("/api/v1/members/me", token=access)
            assert status == 200 and profile["data"]["memberId"] == member_id
            assert profile["data"]["email"] is None and profile["data"]["socialAccounts"] == []
            print("protected_profile: 200")

            status, babies = http("/api/v1/babies", token=access)
            assert status == 200 and babies["data"] == []
            boundary = "dearusqa" + secrets.token_hex(6)
            baby_json = json.dumps({"babyName": "QA 아기", "birthDate": "2026-01-01",
                                    "gender": "FEMALE"}, ensure_ascii=False).encode()
            multipart = (
                (f'--{boundary}\r\nContent-Disposition: form-data; name="request"\r\n'
                 'Content-Type: application/json\r\n\r\n').encode()
                + baby_json + f'\r\n--{boundary}--\r\n'.encode()
            )
            status, created = http(
                "/api/v1/babies", method="POST", body=multipart, token=access,
                content_type="multipart/form-data; boundary=" + boundary,
            )
            assert status == 201 and created["success"]
            status, babies = http("/api/v1/babies", token=access)
            assert status == 200 and len(babies["data"]) == 1
            print("baby_create_and_list: 201, 200")

            refresh_body = json.dumps({"refreshToken": refresh}).encode()
            status, rotated = http(
                "/api/v1/auth/reissue", method="POST", body=refresh_body,
                content_type="application/json",
            )
            assert status == 200 and rotated["data"]["refreshToken"] != refresh
            status, _ = http(
                "/api/v1/auth/reissue", method="POST", body=refresh_body,
                content_type="application/json",
            )
            assert status == 401
            print("refresh_rotation_and_reuse: 200, 401")

            status, second = http("/api/v1/auth/guest", method="POST", body=b"")
            assert status == 200 and second["data"]["member"]["memberId"] != member_id
            print("second_guest_is_distinct: yes")
        except Exception:
            log.seek(0)
            for line in [line for line in log.read().splitlines()
                         if any(term in line for term in ("ERROR", "Caused by:", "Exception"))][-12:]:
                print(line[:300])
            raise
        finally:
            if app is not None and app.poll() is None:
                os.killpg(app.pid, signal.SIGTERM)
                try:
                    app.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(app.pid, signal.SIGKILL)
                    app.wait()
            if container_started:
                subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=15)


if __name__ == "__main__":
    main()
