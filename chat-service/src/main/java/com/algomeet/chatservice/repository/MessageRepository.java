package com.algomeet.chatservice.repository;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.model.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<MessageDocument, String> {
    // Direct chat hybrid support
    List<MessageDocument> findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(
            String sender1, String receiver1, String sender2, String receiver2
    );

    List<MessageDocument> findPagedBySenderAndReceiver(String sender1, String receiver1, String sender2, String receiver2, Pageable pageable);

    // Group chat hybrid support
    List<MessageDocument> findTop100ByReceiverOrderByTimestampDesc(String receiver);

    List<MessageDocument> findByReceiver(String receiver, Pageable pageable);

    List<MessageDocument> findByReceiver(String receiver);

    List<MessageDocument> findBySenderAndReceiverAndStatusNot(String sender, String receiver, MessageStatus messageStatus);

    int countBySenderAndReceiverAndStatusNot(String sender, String receiver, MessageStatus messageStatus);

    List<MessageDocument> findByReceiverAndStatusNot(String userId, MessageStatus messageStatus);

    List<MessageDocument> findBySenderOrReceiver(String userId, String userId1);
}
