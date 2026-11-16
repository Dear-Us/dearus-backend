# Backend configuration

The tracked `.env.example` lists every external variable currently referenced by `src/main/resources/application.properties`. Fill in the ignored `.env` for local use. Never commit `.env`, tokens, passwords, or a populated copy of this template.

The AWS RDS **instance identifier** is `dearus-dev` in `ap-northeast-2`; it is not the JDBC database name or Endpoint. The configured JDBC database name is `dearus`, and MariaDB listens on port `3306`. Replace `{RDS_ENDPOINT}` with the exact RDS Endpoint hostname. The JDBC URL uses `sslMode=verify-full` because this instance requires TLS; it verifies both the server certificate and hostname. Because public access is disabled, a local computer cannot be assumed to reach this DB; use approved VPC connectivity or run the backend inside the VPC after its network access is configured. The `dearus` database and an account with the required application/Flyway privileges must exist before startup.

Required startup values in `.env`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DEARUS_JWT_SECRET_BASE64`, and numeric `KAKAO_APP_ID`. The JWT value must be Base64 for a key of at least 32 bytes; enter your own value. `GOOGLE_CLIENT_ID` is required to make Google login work. Current Naver token validation does not read a separate Naver environment variable. The application already provides the JWT issuer and token lifetimes internally; no additional JWT variable is needed.

`SWAGGER_ENABLED=true` exposes Swagger UI and `/v3/api-docs`; set it to `false` or restrict access in production. `DEARUS_OPENAPI_SERVER_URL` can stay empty to use the request host. Set `DEARUS_INVITATION_BASE_URL` to the finalized HTTPS/app-link base URL before using invitation creation. Both image storage integrations default to disabled. If enabled, configure the corresponding bucket, public base URL, region, and IAM permissions; leave the endpoint empty for AWS S3 or use it only for an intentional compatible storage endpoint. On ECS, provide AWS access through a task role, not static keys in `.env`.

The `.env` format is Java `.properties`, not a shell script. Spring Boot does not load it automatically. On macOS, use the project launcher after filling the placeholders. It selects Java 26 for this process, checks required values, and checks database TCP connectivity before starting Spring Boot:

```sh
./scripts/run-local.sh --check
./scripts/run-local.sh
```

The launcher uses `SPRING_CONFIG_IMPORT=file:.env[.properties]`, so the local file is required and its keys are available to `application.properties`. It does not change your global Java configuration. If the RDS DNS or TCP check fails, do not assume a credential problem: verify the exact Endpoint and provide approved VPC connectivity. For a packaged JAR, use Java 26 and the same `SPRING_CONFIG_IMPORT` value. ECS should instead inject these names as environment variables/secrets; do not package `.env` into the image. A build and test run does not need local secrets:

```sh
./gradlew clean test bootJar
```

Build success does not prove access to the private RDS or readiness of social-provider credentials. Verify those separately after the endpoint, network path, and secrets have been supplied.
