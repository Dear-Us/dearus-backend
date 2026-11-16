package com.dearusback.care.infrastructure.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.voice.domain.model.VoiceAction;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VoiceCareCommandAdapterTest {

    @Test
    void rejectsRecordIdOutsideLongRange() {
        VoiceCareCommandAdapter adapter = new VoiceCareCommandAdapter(
                null, null, null, new ObjectMapper(), Clock.systemUTC());

        assertThat(adapter.validate(1L, 1L, VoiceAction.END_RECORD,
                "{\"recordId\":18446744073709551617}").valid()).isFalse();
    }
}
