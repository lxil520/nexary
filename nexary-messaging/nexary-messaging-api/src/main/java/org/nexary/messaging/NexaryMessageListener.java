package org.nexary.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a business message consumer for Nexary-managed subscription. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NexaryMessageListener {
    /** Topic name or property placeholder. */
    String topic();

    /** Consumer group name or property placeholder. */
    String consumerGroup();

    /** Payload type delivered to the business consumer. */
    Class<?> payloadType() default String.class;
}
