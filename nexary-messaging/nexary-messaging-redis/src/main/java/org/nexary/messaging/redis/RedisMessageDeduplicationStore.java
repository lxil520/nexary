package org.nexary.messaging.redis;

import java.time.Duration;
import java.util.Optional;
import org.nexary.messaging.MessageDeduplicationClaim;
import org.nexary.messaging.MessageDeduplicationStore;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed duplicate consumption store shared by broker adapters. */
public class RedisMessageDeduplicationStore implements MessageDeduplicationStore {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessagingProperties properties;

    public RedisMessageDeduplicationStore(StringRedisTemplate stringRedisTemplate, RedisMessagingProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<MessageDeduplicationClaim> claim(String messageId, Duration ttl) {
        String redisKey = properties.getDeduplicationPrefix() + messageId;
        Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "processing", normalize(ttl));
        if (!Boolean.TRUE.equals(claimed)) {
            return Optional.empty();
        }
        return Optional.of(new Claim(redisKey, messageId));
    }

    private Duration normalize(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? properties.getDeduplicationTtl() : ttl;
    }

    private final class Claim implements MessageDeduplicationClaim {
        private final String redisKey;
        private final String messageId;
        private boolean completed;

        private Claim(String redisKey, String messageId) {
            this.redisKey = redisKey;
            this.messageId = messageId;
        }

        @Override
        public String messageId() {
            return messageId;
        }

        @Override
        public void complete() {
            completed = true;
            stringRedisTemplate.opsForValue().set(redisKey, "done", properties.getDeduplicationTtl());
        }

        @Override
        public void close() {
            if (!completed) {
                stringRedisTemplate.delete(redisKey);
            }
        }
    }
}
