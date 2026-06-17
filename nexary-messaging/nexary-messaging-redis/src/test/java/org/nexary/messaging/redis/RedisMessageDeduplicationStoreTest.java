package org.nexary.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.MessageDeduplicationClaim;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisMessageDeduplicationStoreTest {
    @Test
    void returnsClaimWhenSetIfAbsentSucceeds() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("processing"), any(Duration.class))).thenReturn(true);

        RedisMessagingProperties properties = new RedisMessagingProperties();
        RedisMessageDeduplicationStore store = new RedisMessageDeduplicationStore(template, properties);

        MessageDeduplicationClaim claim = store.claim("42", Duration.ofMinutes(1)).orElseThrow();
        claim.complete();
        claim.close();

        verify(valueOperations).set(eq("nexary:mq:dedup:42"), eq("done"), eq(Duration.ofHours(1)));
    }

    @Test
    void rejectsDuplicateClaim() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("processing"), any(Duration.class))).thenReturn(false);

        RedisMessageDeduplicationStore store = new RedisMessageDeduplicationStore(template, new RedisMessagingProperties());

        assertThat(store.claim("42", Duration.ofMinutes(1))).isEmpty();
    }
}
