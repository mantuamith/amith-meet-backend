package com.algomeet.groupservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.controller.swagger.GroupControllerDoc;
import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.CommonResponse;
import com.algomeet.groupservice.dto.GroupInviteLinkResponse;
import com.algomeet.groupservice.dto.GroupPermissionsPatchRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.UpdateGroupRequest;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.service.GroupService;
import com.algomeet.groupservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController implements GroupControllerDoc {

	private final GroupService groupService;

	@PostMapping("/create")
	public ResponseEntity<CommonResponse<GroupResponse>> createGroup(
			@Valid @RequestBody GroupRequest request,
			Authentication authentication) {

		GroupResponse response = groupService.createGroup(
				request,
				authentication.getName(),
				SecurityUtil.getUserKey()
				);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
	}
	
	@GetMapping("/{groupId}")
	public ResponseEntity<CommonResponse<GroupResponse>> getGroup(@PathVariable UUID groupId) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, groupService.getGroupById(groupId)));
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
	}
	
	@PutMapping("/{groupId}")
	public ResponseEntity<CommonResponse<GroupResponse>> updateGroup(@PathVariable UUID groupId, @Valid @RequestBody UpdateGroupRequest request) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, groupService.updateGroup(groupId, request, SecurityUtil.getUserKey())));
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
	}

	@PatchMapping("/{groupId}/permissions")
	public ResponseEntity<CommonResponse<GroupPermissionsResponse>> patchGroupPermissions(
			@PathVariable UUID groupId,
			@Valid @RequestBody GroupPermissionsPatchRequest request) {
		try {
			GroupPermissionsResponse response = groupService.patchGroupPermissions(groupId, request, SecurityUtil.getUserKey());
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
	}
	
	@DeleteMapping("/{groupId}")
	public ResponseEntity<CommonResponse<?>> removeGroup(@PathVariable UUID groupId) {
		try {
			groupService.removeGroup(groupId);
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	@PostMapping("/{groupId}/join")
	public ResponseEntity<CommonResponse<?>> joinGroup(
			@PathVariable UUID groupId,
			@RequestParam Optional<String> nickname,
			Authentication authentication) {

		GroupResponse response = null;

		try {
			response = groupService.joinGroup(
					groupId,
					authentication.getName(),
					SecurityUtil.getUserKey(),
					nickname.orElse(null)
					);
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		} catch(IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.USER_ALREADY_GROUP_MEMBER));
		} 

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
	}

	@PostMapping("/{groupId}/join-by-invite")
	public ResponseEntity<CommonResponse<?>> joinGroupByInvite(
			@PathVariable UUID groupId,
			@RequestParam String inviteCode,
			@RequestParam Optional<String> nickname,
			Authentication authentication) {

		GroupResponse response = null;

		try {
			response = groupService.joinGroupByInviteCode(
					groupId,
					inviteCode,
					authentication.getName(),
					SecurityUtil.getUserKey(),
					nickname.orElse(null)
			);
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		} catch(IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.USER_ALREADY_GROUP_MEMBER));
		} catch(IllegalArgumentException ex) {
			return ResponseEntity.badRequest()
					.body(CommonResponse.from(ResponseCode.GROUP_INVITE_CODE_INVALID));
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
	}

	@PostMapping("/{groupId}/add")
	public ResponseEntity<CommonResponse<?>> addGroupMembers(
			@PathVariable UUID groupId,
			@Valid @RequestBody AddGroupMembersRequest request) {

		GroupResponse response = null;

		try {
			response = groupService.addGroupMembers(groupId, request, SecurityUtil.getUserKey());

		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		} catch(IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.USER_ALREADY_GROUP_MEMBER));
		} 

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
	}

	@GetMapping("/{groupId}/invite-link")
	public ResponseEntity<CommonResponse<GroupInviteLinkResponse>> getInviteLink(@PathVariable UUID groupId) {
		try {
			GroupInviteLinkResponse response = groupService.getOrCreateInviteLink(groupId, SecurityUtil.getUserKey());
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
	}

	@PostMapping("/{groupId}/invite-link/reset")
	public ResponseEntity<CommonResponse<GroupInviteLinkResponse>> resetInviteLink(@PathVariable UUID groupId) {
		try {
			GroupInviteLinkResponse response = groupService.resetInviteLink(groupId, SecurityUtil.getUserKey());
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
	}

	@DeleteMapping("/{groupId}/leave")
	public ResponseEntity<CommonResponse<?>> leaveGroup(
			@PathVariable UUID groupId,
			Authentication authentication) {     

		try {
			groupService.leaveGroup(groupId, SecurityUtil.getUserKey());

		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		} catch(IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.GROUP_MEMBER_NOT_FOUND));
		} 

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	@DeleteMapping("/{groupId}/remove")
	public ResponseEntity<CommonResponse<?>> removeGroupMember(
			@PathVariable UUID groupId,
			@RequestParam String userKey) {

		try {
			groupService.removeGroupMember(groupId, userKey);

		} catch(GroupNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		} catch(IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.GROUP_MEMBER_NOT_FOUND));
		} 

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	@GetMapping("/mine")
	public ResponseEntity<CommonResponse<?>> getMyGroups(Authentication authentication) {
		List<GroupResponse> groups =
				groupService.getMyGroups(SecurityUtil.getUserKey());

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, groups));
	}
	
	
	
}
