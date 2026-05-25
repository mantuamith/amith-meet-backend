package com.algomeet.groupservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.controller.swagger.InternalGroupControllerDoc;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.service.GroupService;
import com.algomeet.groupservice.util.SecurityUtil;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/internal/groups")
public class InternalGroupController implements InternalGroupControllerDoc{
	private final GroupService groupService;

	@GetMapping("/{groupId}")
	public GroupResponse getGroup(@PathVariable UUID groupId) {
		return groupService.getGroupById(groupId);
	}

	@GetMapping("/member/username/{username}")
	public List<GroupResponse> getGroupsForUsername(@PathVariable String username) {
		return groupService.getGroupsByUsername(username);
	}
	
	@GetMapping("/member/userkey/{userkey}")
	public List<GroupResponse> getGroupsForUserKey(@PathVariable String userkey) {
		return groupService.getGroupsByUserKey(userkey);
	}
	
	/**
	 * Clears the requesting member's visible group message history timeline.
	 *
	 * This operation updates the member-specific history cutoff timestamp,
	 * causing historical messages generated before the cutoff to be excluded
	 * from future synchronization and conversation timeline fetch operations.
	 *
	 * The operation only affects the requesting member visibility context
	 * and does not physically delete group messages from the server.
	 *
	 * @param groupId target group identifier
	 * @param historyCutoff optional Unix epoch timestamp (in milliseconds) threshold; 
	 * if null, defaults to the current server system time
	 * @param authentication authenticated user session
	 * @return success response containing {@code true} when the history
	 * cutoff was successfully updated
	 *
	 * @throws GroupNotFoundException if the target group does not exist
	 * @throws IllegalStateException if the requesting user is not a group member
	 */
	@PostMapping("/{groupId}/members/{userKey}/clear-history")
	public Boolean clearMemberHistoryTimeline(
			@PathVariable UUID groupId,
			@PathVariable(name = "userKey") UUID targetUserKey,
			@RequestParam(name = "historyCutoff", required = false) Long historyCutoff) {

		// Fallback to current server time if the parameter is missing or null
		long finalCutoff = (historyCutoff != null) ? historyCutoff : Instant.now().toEpochMilli();

		boolean cleared = groupService.clearMemberHistoryTimeline(
				groupId,
				targetUserKey.toString(),
				finalCutoff);

		return cleared;
	}
}
