package org.nexary.messaging.activemqclassic;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetrySignal;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageGovernanceSupport;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;

/** ActiveMQ Classic publisher using Jakarta JMS without exposing JMS types in Nexary APIs. */
public class ActiveMqClassicMessagePublisher implements MessagePublisher {
    private final ConnectionFactory connectionFactory;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;
    private final GovernanceExecution governanceExecution;

    public ActiveMqClassicMessagePublisher(ConnectionFactory connectionFactory, MessageSerializer serializer) {
        this(connectionFactory, serializer, NexaryObservationPublisher.noop());
    }

    public ActiveMqClassicMessagePublisher(
            ConnectionFactory connectionFactory,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this(connectionFactory, serializer, observationPublisher, GovernanceExecution.direct());
    }

    public ActiveMqClassicMessagePublisher(
            ConnectionFactory connectionFactory,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher,
            GovernanceExecution governanceExecution) {
        this.connectionFactory = connectionFactory;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.governanceExecution = governanceExecution == null ? GovernanceExecution.direct() : governanceExecution;
    }

    @Override
    public CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope) {
        return MessageGovernanceSupport.executeGovernedPublish(
                envelope, "activemq_classic", observationPublisher, governanceExecution, () -> publishDirect(envelope));
    }

    private CompletionStage<MessagePublishResult> publishDirect(MessageEnvelope<?> envelope) {
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(queue(session, envelope.topic()))) {
            Message message = ActiveMqClassicMessageCodec.toMessage(session, serializer, envelope);
            producer.send(message);
            MessageObservationSupport.publish(observationPublisher, "publish", "activemq_classic", "success");
            return CompletableFuture.completedFuture(MessagePublishResult.success(message.getJMSMessageID()));
        } catch (JMSException ex) {
            MessageObservationSupport.publish(
                    observationPublisher, "publish", "activemq_classic", "failure", Collections.emptyMap(), ex);
            MessagePublishResult result = MessagePublishResult.failed(ex.getMessage(), RetrySignal.stop(ex.getMessage()));
            MessageGovernanceSupport.publishRetryStoppedIfStop(
                    envelope, "activemq_classic", "publish", result.retrySignal(), observationPublisher);
            return CompletableFuture.completedFuture(
                    result);
        }
    }

    private Queue queue(Session session, String topic) throws JMSException {
        return session.createQueue(topic);
    }
}
