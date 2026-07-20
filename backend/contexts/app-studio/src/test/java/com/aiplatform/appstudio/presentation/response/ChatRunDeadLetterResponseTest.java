package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChatRunDeadLetterResponseTest {

    @Test
    void responseNeverExposesRetainedFinalizationOrReplayReason() {
        assertThat(Arrays.stream(ChatRunDeadLetterResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList())
                .noneMatch(name -> name.contains("finalization") && name.contains("json"))
                .doesNotContain("lastreplayreason", "replayreason", "payload");
    }
}
