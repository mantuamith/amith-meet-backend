package com.algomeet.chatservice.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.client.MediaClient;
import com.algomeet.chatservice.document.GroupDto;
import com.algomeet.chatservice.document.MediaItem;
import com.algomeet.chatservice.document.MessageDocument;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

	private final MediaClient mediaClient;
	private final GroupClient groupClient;

	private void share(String mediaId, String userKey, List<String> shareWithUserKeys) {
		if (!StringUtils.hasText(mediaId)) {
			throw new RuntimeException("Media ID has empty value " + mediaId);
		}

		try {
			mediaClient.share(mediaId, userKey, shareWithUserKeys);
		} catch (FeignException.NotFound ex) {
			log.error("Media {} not found while sharing", mediaId, ex);
			throw new RuntimeException(mediaId);
		} catch (FeignException.Forbidden ex) {
			log.error("Access denied while sharing media {}", mediaId, ex);
			throw new RuntimeException(mediaId);
		} catch (FeignException ex) {
			log.error("Media-service error while sharing media {}", mediaId, ex);
			throw new RuntimeException("Failed to share media", ex);
		} catch (Exception ex) {
			log.error("Unexpected error while sharing media {}", mediaId, ex);
			throw new RuntimeException("Unexpected error", ex);
		}
	}

	private void delete(String mediaId, String userKey, List<String> deleteWithUserKeys) {
		if (!StringUtils.hasText(mediaId)) {
			throw new RuntimeException("Media ID has empty value " + mediaId);
		}

		try {
			mediaClient.delete(mediaId, userKey, deleteWithUserKeys);
		} catch (FeignException.NotFound ex) {
			log.error("Media {} not found while deleting", mediaId, ex);
			throw new RuntimeException(mediaId);
		} catch (FeignException.Forbidden ex) {
			log.error("Access denied while deleting media {}", mediaId, ex);
			throw new RuntimeException(mediaId);
		} catch (FeignException ex) {
			log.error("Media-service error while deleting media {}", mediaId, ex);
			throw new RuntimeException("Failed to delete media", ex);
		} catch (Exception ex) {
			log.error("Unexpected error while deleting media {}", mediaId, ex);
			throw new RuntimeException("Unexpected error", ex);
		}
	}

	public void share(MessageDocument message) {
		share(message, null);
	}

	public void share(MessageDocument message, GroupDto group) {
		Set<String> shareWithUserKeys = new HashSet<>();
		// Check if message has media group
		if (group != null) {
			// Add logic for group message
			shareWithUserKeys = group.members.stream().map(m -> m.getUserKey()).collect(Collectors.toSet());
		} else {
			shareWithUserKeys = Set.of(message.getReceiverKey());
		}

		// Delete
		for (MediaItem item : message.getMediaGroup()) {
			share(item.getMediaId(), message.getSenderKey(), new ArrayList<>(shareWithUserKeys));
		}
	}

	public void delete(MessageDocument message, String requesterKey) {
		// Check if has media group
		if (!CollectionUtils.isEmpty(message.getMediaGroup())) {
			for (MediaItem item : message.getMediaGroup()) {
				delete(item.getMediaId(), requesterKey, List.of(requesterKey));
			}
		}
	}

	public void deleteAll(MessageDocument message, String requesterKey) {
		// Check if message has media group
		if (!CollectionUtils.isEmpty(message.getMediaGroup())) {
			Set<String> deleteWithUserKeys = new HashSet<>();

			if (message.isGroupMessage()) {
				// Add logic for group message
				GroupDto group = groupClient.getGroupById(Long.parseLong(message.getGroupId()));
				deleteWithUserKeys = group.members.stream().map(m -> m.getUserKey()).collect(Collectors.toSet());
			} else {
				deleteWithUserKeys = Set.of(message.getSenderKey(), message.getReceiverKey());
			}

			// Delete
			for (MediaItem item : message.getMediaGroup()) {
				delete(item.getMediaId(), requesterKey, new ArrayList<>(deleteWithUserKeys));
			}
		}
	}
}
