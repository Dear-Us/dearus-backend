package com.dearusback.family.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearusback.family.domain.model.BabyInvitation;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class BabyInvitationPersistenceAdapterTest {

    @Test
    void tokenCollisionDoesNotExposeHashThroughApplicationException() {
        SpringDataBabyInvitationRepository repository = mock(SpringDataBabyInvitationRepository.class);
        String hash = "a".repeat(64);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(hash));

        assertThatThrownBy(() -> new BabyInvitationPersistenceAdapter(repository)
                .save(BabyInvitation.create(1L, 2L, hash, Instant.parse("2026-10-05T00:00:00Z"))))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).isEqualTo("Invitation persistence failed");
                    assertThat(error.getCause()).isNull();
                    assertThat(error.toString()).doesNotContain(hash);
                });
    }
}
