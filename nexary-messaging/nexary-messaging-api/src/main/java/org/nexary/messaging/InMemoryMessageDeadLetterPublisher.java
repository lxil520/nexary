package org.nexary.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory dead-letter publisher for local demos, tests, and starter defaults. */
public class InMemoryMessageDeadLetterPublisher implements MessageDeadLetterPublisher {
    private final List<MessageDeadLetterRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void publish(MessageDeadLetterRecord record) {
        records.add(record);
    }

    /** Returns terminal failure records captured by this publisher. */
    public List<MessageDeadLetterRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }
}
