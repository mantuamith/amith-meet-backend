package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucUnreadCount;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;
import com.algomeet.xmpp.chatservice.util.MucMemberUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@AllArgsConstructor
public class MucUnreadCountService {
	private final MucRoomReadCursorRepository mucRoomReadCursorRepository;
	private final MucMessageRepository mucMessageRepository;
	private final MucUserGroupsCacheService mucUserGroupsCacheService;
	private final GroupCacheService groupCacheService;
	
	/**
	 * Aggregates and returns the active unread counts across all rooms for a specific user as a standard list.
	 * 
	 * <p>This version explicitly blocks the underlying asynchronous stream at the termination edge, 
	 * collecting all parallel index counts into a synchronous {@link List}.</p>
	 *
	 * @param userKey The unique identifier of the user requesting badge counts.
	 * @return A {@link List} containing {@link MucUnreadCount} payloads for each group.
	 */
	public List<MucUnreadCount> getUnreadCountsByUser(UUID userKey) {
		// Step 1: Fetch the user's groups from your external client service
		
		List<String> roomIds = mucUserGroupsCacheService.getCachedGroupIds(userKey.toString());
		if (CollectionUtils.isEmpty(roomIds)) {
			return Collections.emptyList();
		}
		
		
		// Step 2: Assemble the reactive data pipeline
		return mucRoomReadCursorRepository.findByUserKey(userKey)
				.collectList()
				.flatMapIterable(cursorList -> {
					// Convert the cursor list into an optimized O(1) lookup Map
					Map<UUID, MucRoomReadCursor> cursorMap = cursorList.stream()
							.collect(Collectors.toMap(
									MucRoomReadCursor::getRoomId,
									cursor -> cursor,
									(existing, replacement) -> existing
									));

					// Pair each room with its respective read cursor context
					return roomIds.stream()
							.map(roomId -> new RoomWithCursorContext(roomId, cursorMap.get(UUID.fromString(roomId))))
							.collect(Collectors.toList());
				})
				// Step 3: Concurrently execute the covered index scans across the room batch
				.flatMap(context -> {
					String roomId = context.roomId;
					UUID lastReadMid = context.cursor != null ? context.cursor.getLastReadMid() : Constants.SMALLEST_UUID_V7;
					
					// Retrieve group info
					Group room = groupCacheService.getCachedGroup(roomId);
					Optional<GroupMember> member = SearchUtil.findMember(room, userKey.toString());
					if (member.isEmpty()) {
						return Mono.empty();
					}

					Instant historyCutoff = MucMemberUtil.getHistoryCutoff(room, member.get());

					return mucMessageRepository.countUnreadMessages(UUID.fromString(roomId), lastReadMid, userKey, historyCutoff)
							.map(count -> {
								MucUnreadCount unreadCountDto = new MucUnreadCount();
								unreadCountDto.setId(String.format("%s_%s", userKey, roomId));
								unreadCountDto.setUserKey(userKey.toString());
								unreadCountDto.setRoomId(roomId);
								unreadCountDto.setUnreadCount(count.intValue());
								unreadCountDto.setLastReadMid(context.cursor != null ? context.cursor.getLastReadMid().toString() : null);
								unreadCountDto.setLastReadSid(context.cursor != null ? context.cursor.getLastReadSid().toString() : null);
								return unreadCountDto;
							});
				})
				// Step 4: Collect all the items emitted by the Flux back into a Mono<List<MucUnreadCount>>
				.collectList()
				// Step 5: Safely block and extract the concrete List value out of the reactive thread layer
				.block();
	}

	/**
	 * Private internal wrapper class used to cleanly pass room and cursor state 
	 * across the reactive functional boundaries.
	 */
	private static class RoomWithCursorContext {
		final String roomId;
		final MucRoomReadCursor cursor;

		RoomWithCursorContext(String roomId, MucRoomReadCursor cursor) {
			this.roomId = roomId;
			this.cursor = cursor;
		}
	}

	/**
	 * Computes the unread message count for a single specified room reactively.
	 * 
	 * <p>This method maintains a non-blocking pipeline throughout, shifting from a 
	 * cursor lookup to an index-covered count query, and safely falling back to 
	 * counting from the beginning of time if no cursor exists yet.</p>
	 *
	 * @param userKey The unique identifier of the user checking their badge.
	 * @param roomId  The target group chat room identifier.
	 * @return A {@link Mono} emitting the total unread integer count.
	 */
	public Integer getUnreadCount(UUID userKey, UUID roomId) {

		/* TODO: Value should be coming from GroupMember class, returned from Group-service API.
		public class GroupMember {		    
		    // The magic field: Anything before this timestamp is "deleted" for this user
		    private Instant historyCutoffAt; 
		}*/
		// Used for delete group chat conversation for a particular user
		Instant historyCutoff = Instant.EPOCH;	
		
		// Step 1: Look up the single cursor document for this specific user and room
		return mucRoomReadCursorRepository.findByUserKeyAndRoomId(userKey, roomId)
				// Step 2: Extract the last read message ID if the cursor exists
				.map(MucRoomReadCursor::getLastReadMid)
				// Step 3: Fall back to an empty string (beginning of time) if no cursor is found
				.defaultIfEmpty(Constants.SMALLEST_UUID_V7)
				// Step 4: Switch to the asynchronous index-covered count query
				.flatMap(lastReadMid -> mucMessageRepository.countUnreadMessages(roomId, lastReadMid, userKey, historyCutoff))
				// Step 5: Downcast the Long count from MongoDB cleanly to an Integer
				.map(Long::intValue)
				.block();
	}
}