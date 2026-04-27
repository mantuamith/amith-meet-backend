package com.algomeet.xmpp.chatservice.repository;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MucMessageRepository extends ReactiveMongoRepository<MucMessage, String> {

    /**
     * Retrieve a page of messages for a room starting AFTER a specific sequential ID.
     * Ideal for infinite scroll / MAM 'after' queries.
     * 
     * Version with a limit to satisfy MAM 'max' requests (XEP-0059)
     */
    Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(
        String roomId, String afterId, Pageable pageable
    );
    
    /**
    * Retrieves older messages (scrolling up).
    * Maps to MAM 'before' logic: get 'max' messages where ID < beforeId.
    */
   @Query("SELECT * FROM muc_messages WHERE room_id = :roomId AND id < :beforeId ORDER BY id DESC")
   Flux<MucMessage> findByRoomIdAndIdLessThanOrderByIdDesc(
       String roomId, String beforeId, Pageable pageable
   );
        
    /**
     * Efficiently counts unread messages using the {roomId: 1, id: 1} compound index.
     */
    Mono<Long> countByRoomIdAndIdGreaterThanAndFromNot(String roomId, String lastReadId, String userJid);
    
    
    /**
     * Fetches messages for a specific room that occurred after the given ULID/ID.
     * Sorted Ascending so the client receives them in chronological order.
     */
    Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, String afterId);
}