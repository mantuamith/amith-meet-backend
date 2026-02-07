package com.algomeet.groupservice.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.controller.swagger.GroupControllerDoc;
import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.CommonResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;
import com.algomeet.groupservice.util.SecurityUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/groups")
public class GroupController implements GroupControllerDoc{
	@Autowired
	private GroupRepository groupRepository;

	@PostMapping("/create")
	public ResponseEntity<CommonResponse<GroupResponse>> createGroup(@Valid @RequestBody GroupRequest groupRequest,
			Authentication authentication) {
		MemberRequest member = new MemberRequest();
		member.setUsername(authentication.getName());
		member.setUserKey(SecurityUtil.getUserKey());

		groupRequest.getMembers().add(member);

		Group group = GroupMapper.toEntity(groupRequest);

		return ResponseEntity
				.ok(CommonResponse.from(ResponseCode.SUCCESS, GroupMapper.toResponse(groupRepository.save(group))));
	}
	
	@DeleteMapping("/{groupId}")
	public ResponseEntity<CommonResponse<?>> removeGroup(@PathVariable Long groupId, Authentication authentication) {
		Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
		if (groupOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}
		
		groupRepository.deleteById(groupId);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}
	

	@PostMapping("/{groupId}/join")
	public ResponseEntity<CommonResponse<?>> joinGroup(@PathVariable String groupId, Authentication authentication) {
		Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
		if (groupOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}

		Group group = groupOpt.get();
		Member member = new Member();

		member.setUsername(authentication.getName());
		member.setUserKey(SecurityUtil.getUserKey());

		if (group.getMembers().contains(member)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.USER_ALREADY_GROUP_MEMBER));
		}

		group.getMembers().add(member);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, groupRepository.save(group)));
	}
	
	@PostMapping("/{groupId}/add")
	public ResponseEntity<CommonResponse<?>> addGroupMembers(
	        @PathVariable Long groupId,
	        @RequestBody @Valid AddGroupMembersRequest request,
	        Authentication authentication) {

	    Optional<Group> groupOpt = groupRepository.findById(groupId);
	    if (groupOpt.isEmpty()) {
	        return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                .body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
	    }

	    Group group = groupOpt.get();

	    Set<Member> existingMembers = group.getMembers();

	    Set<Member> newMembers = request.getMembers().stream()
	            .map(m -> {
	                Member member = new Member();
	                member.setUserKey(m.getUserKey());
	                member.setUsername(m.getUsername());
	                return member;
	            })
	            .filter(member -> !existingMembers.contains(member))
	            .collect(Collectors.toSet());

	    if (newMembers.isEmpty()) {
	        return ResponseEntity.status(HttpStatus.CONFLICT)
	                .body(CommonResponse.from(ResponseCode.USER_ALREADY_GROUP_MEMBER));
	    }

	    existingMembers.addAll(newMembers);
	    groupRepository.save(group);

	    return ResponseEntity.ok(
	            CommonResponse.from(ResponseCode.SUCCESS, group)
	    );
	}
	
	@DeleteMapping("/{groupId}/leave")
	public ResponseEntity<CommonResponse<?>> leaveGroup(@PathVariable String groupId, Authentication authentication) {
		Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
		if (groupOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}

		Group group = groupOpt.get();
		String userKey = SecurityUtil.getUserKey();
		Member removeMember = new Member(userKey, null);

		if (!group.getMembers().contains(removeMember)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_MEMBER_NOT_FOUND));
		}

		group.getMembers().remove(removeMember);
		groupRepository.save(group);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}
	
	@DeleteMapping("/{groupId}/remove")
	public ResponseEntity<CommonResponse<?>> removeGroupMember(@PathVariable String groupId, 
			@RequestParam String userKey, Authentication authentication) {
		Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
		if (groupOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_ID_NOT_FOUND));
		}

		Group group = groupOpt.get();
		Member removeMember = new Member(userKey, null);

		if (!group.getMembers().contains(removeMember)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_MEMBER_NOT_FOUND));
		}

		group.getMembers().remove(removeMember);
		groupRepository.save(group);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}


	@GetMapping("/groups/mine")
	public ResponseEntity<CommonResponse<?>> getMyGroups(Authentication authentication) {
		List<Group> myGroups = groupRepository.findByMembersContaining(authentication.getName());

		List<GroupResponse> myGroupDtos = new ArrayList<>();
		if (!CollectionUtils.isEmpty(myGroups)) {
			myGroups.forEach(g -> {
				myGroupDtos.add(GroupMapper.toResponse(g));
			});
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, myGroupDtos));
	}
}
