package com.algomeet.chatservice.repository;

import com.algomeet.chatservice.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    // Direct chat hybrid support
    List<Message> findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(
            String sender1, String receiver1, String sender2, String receiver2
    );

    List<Message> findPagedBySenderAndReceiver(String sender1, String receiver1, String sender2, String receiver2, Pageable pageable);

    // Group chat hybrid support
    List<Message> findTop100ByReceiverOrderByTimestampDesc(String receiver);

    List<Message> findByReceiver(String receiver, Pageable pageable);
}
