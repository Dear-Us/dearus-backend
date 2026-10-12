package com.dearusback.family.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class InvitationTokenAndUrlTest {

    @Test
    void createsIndependentUrlSafe256BitTokensAndSha256Hashes() throws Exception {
        var generator = new SecureInvitationTokenGenerator();
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(second)).hasSize(32);
        assertThat(new Sha256InvitationTokenHasher().hash(first)).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(first.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void invitationUrlRequiresConfiguredSafeBaseUrl() {
        assertThat(new InvitationUrlAdapter("https://app.example.com/invite").createUrl("abc_123"))
                .isEqualTo("https://app.example.com/invite?token=abc_123");
        for (String invalid : new String[]{"", "not-a-url", "https://app.example.com/invite?x=1",
                "https://app.example.com/invite#fragment", "https://user@app.example.com/invite"}) {
            assertThatThrownBy(() -> new InvitationUrlAdapter(invalid).createUrl("abc_123"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
