package org.nexary.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrafficTagTest {
    @Test
    void normalizesEmptyValues() {
        TrafficTag tag = new TrafficTag(null, null, "", "", null);

        assertThat(tag.channel()).isEqualTo(TrafficTag.Channel.ONLINE);
        assertThat(tag.priority()).isEqualTo(TrafficTag.Priority.NORMAL);
        assertThat(tag.tenant()).isEqualTo("default");
        assertThat(tag.bizKey()).isEqualTo("default");
        assertThat(tag.attributes()).isEmpty();
    }
}
