package org.nexary.messaging.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSubscriber;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisBoot2MessagingAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisBoot2MessagingAutoConfiguration.class))
            .withUserConfiguration(RedisInfrastructureConfiguration.class)
            .withPropertyValues("nexary.messaging.redis.enabled=true");

    @Test
    void redisQueueBeansAreCreatedForBoot2RedisProviderLine() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisBoot2MessageQueue.class);
            assertThat(context).hasSingleBean(MessagePublisher.class);
            assertThat(context).hasSingleBean(MessageSubscriber.class);
            assertThat(context.getBean(MessagePublisher.class)).isSameAs(context.getBean(RedisBoot2MessageQueue.class));
            assertThat(context.getBean(MessageSubscriber.class)).isSameAs(context.getBean(RedisBoot2MessageQueue.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisInfrastructureConfiguration {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
