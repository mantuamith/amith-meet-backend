package com.algomeet.chatservice.repository;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.model.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

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

    @Query("{ '$or': [ { 'sender': ?0, 'receiver': ?1 }, { 'sender': ?1, 'receiver': ?0 } ] }")
    Page<MessageDocument> findConversation(String userA, String userB, Pageable pageable);

    // Non-paged (if you really want a list):
    @Query(value = "{ '$or': [ { 'sender': ?0, 'receiver': ?1 }, { 'sender': ?1, 'receiver': ?0 } ] }")
    List<MessageDocument> findConversationAll(String userA, String userB, Sort sort);


    @Query("""
       {
         $and: [
           { deletedForAll: { $ne: true } },
           { $or: [
                { sender: ?0, receiver: ?1 },
                { sender: ?1, receiver: ?0 }
           ]},
           { $or: [
                { deletedForUsers: { $exists: false } },
                { deletedForUsers: { $nin: [ ?2 ] } }
           ]}
         ]
       }
    """)
    List<MessageDocument> findVisibleConversation(String userA, String userB, String viewer,
                                                  Pageable pageable);

    @Query(value = """
    {
      $and: [
        { deletedForAll: { $ne: true } },
        {
          $or: [
            { sender: ?0, receiver: ?1 },
            { sender: ?1, receiver: ?0 }
          ]
        },
        {
          $or: [
            { deletedForUsers: { $exists: false } },
            { deletedForUsers: { $nin: [ ?2 ] } }
          ]
        }
      ]
    }
    """)
    List<MessageDocument> findVisibleConversationAll(String userA, String userB, String viewer, Sort sort);


    @Query("""
{
  $and: [
    { deletedForAll: { $ne: true } },
    { $or: [ { sender: ?0 }, { receiver: ?0 } ] },
    { $or: [
        { deletedForUsers: { $exists: false } },
        { deletedForUsers: { $nin: [ ?0 ] } }
    ]}
  ]
}
""")
    List<MessageDocument> findVisibleForViewer(String viewer);

    @Query("""
{
  $and: [
    { deletedForAll: { $ne: true } },
    { $or: [ { sender: ?0 }, { receiver: ?0 } ] },
    { $or: [
        { deletedForUsers: { $exists: false } },
        { deletedForUsers: { $nin: [ ?0 ] } }
    ]}
  ]
}
""")
    List<MessageDocument> findVisibleForViewer(String viewer, Sort sort);



    // For a single message load (and checks)
    Optional<MessageDocument> findById(String id);
}
