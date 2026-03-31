package com.algomeet.groupservice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.UpdateGroupRequest;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

	private final GroupRepository groupRepository;

	public GroupResponse createGroup(GroupRequest request, String username, String userKey) {
		Group group = GroupMapper.toEntity(request);

		if(request.isEmptyGroup()) {
			//Ignore member list
			group.setMembers(null);

			// Assigned owner, this allowed us to assign group owner even if the group is empty.
			if (StringUtils.hasText(request.getOwnerUserKey())) {
				group.setOwnerUserKey(request.getOwnerUserKey());   
			} else {
				group.setOwnerUserKey(userKey);  
			}
		} 

		// Use for audit
		group.setCreatedBy(userKey);
		return GroupMapper.toResponse(groupRepository.save(group));
	}

	public void removeGroup(Long groupId) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);
		groupRepository.delete(group);
	}

	public GroupResponse joinGroup(Long groupId, String username, String userKey, String nickname) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member member = new Member(userKey, username, nickname);
		if (group.getMembers().contains(member)) {
			throw new IllegalStateException(ResponseCode.USER_ALREADY_GROUP_MEMBER.name());
		}

		group.getMembers().add(member);
		return GroupMapper.toResponse(groupRepository.save(group));
	}

	public GroupResponse addGroupMembers(Long groupId, AddGroupMembersRequest request, String userKey) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member user = findMember(group.getMembers(), userKey);

		for(MemberRequest memberReq: request.getMembers()) {
			if(!isUserAllowedToAddMemberWithRole(user.getRole(), memberReq.getRole())) {			
				throw new RuntimeException("User not authorize to add user with role.");
			}
		}

		Set<Member> newMembers = request.getMembers().stream()
				.map(m -> {
					if(m.getRole() != null) {
						return new Member(m.getUserKey(), m.getUsername(), m.getNikname(), m.getRole());
					} else {
						return new Member(m.getUserKey(), m.getUsername(), m.getNikname());
					}
				}                
						)
				.filter(m -> !group.getMembers().contains(m))
				.collect(Collectors.toSet());

		if (newMembers.isEmpty()) {
			throw new IllegalStateException(ResponseCode.USER_ALREADY_GROUP_MEMBER.name());
		}

		group.getMembers().addAll(newMembers);
		return GroupMapper.toResponse(groupRepository.save(group));
	}

	public void leaveGroup(Long groupId, String userKey) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member member = new Member(userKey, null);
		if (!group.getMembers().remove(member)) {
			throw new IllegalStateException(ResponseCode.GROUP_MEMBER_NOT_FOUND.name());
		}

		groupRepository.save(group);
	}

	public void removeGroupMember(Long groupId, String userKey) throws GroupNotFoundException {
		leaveGroup(groupId, userKey);
	}

	public List<GroupResponse> getMyGroups(String userKey) {
		List<Group> groups = groupRepository.findByMembers_UserKey(userKey);

		if (CollectionUtils.isEmpty(groups)) {
			return List.of();
		}

		return groups.stream()
				.map(GroupMapper::toResponse)
				.collect(Collectors.toList());
	}

	private Group getGroupOrThrow(Long groupId) throws GroupNotFoundException {
		return groupRepository.findById(groupId)
				.orElseThrow(() ->
				new GroupNotFoundException("Group Id not found"));
	}

	public GroupResponse getGroupById(Long groupId) {
		Group group = getGroupOrThrow(groupId);

		return GroupMapper.toResponse(group);
	}

	public GroupResponse updateGroup(Long groupId, UpdateGroupRequest request, String userKey) {
		Group group = getGroupOrThrow(groupId);

		if (StringUtils.hasText(request.getName())) {
			group.setName(request.getName());
		}

		Member user = findMember(group.getMembers(), userKey);

		if(!CollectionUtils.isEmpty(request.getMembers())) {   		

			for(MemberRequest memberReq: request.getMembers()) {
				Member updateMember = findMember(group.getMembers(), memberReq.getUserKey());

				if (updateMember == null) {
					throw new RuntimeException("Member not found with ID " + memberReq.getUserKey());
				}

				if(memberReq.getRole() != null) {
					if(isUserAllowedToUpdateMemberWithRole(user.getRole(), memberReq.getRole(), updateMember.getRole())) {
						throw new RuntimeException("User not authorize to change user role.");
					}

					updateMember.setRole(memberReq.getRole());
				}

				if(StringUtils.hasText(memberReq.getNikname())) {
					updateMember.setNickname(memberReq.getNikname());
				}	    			
			}    		
		}

		return GroupMapper.toResponse(groupRepository.save(group));    	    	
	}   

	public Member findMember(Set<Member> members, String targetKey) {
		for (Member member : members) {
			if (member.getUserKey().equals(targetKey)) {
				return member; // Found it, exit early
			}
		}
		return null; // Not found
	}

	private boolean isUserAllowedToAddMemberWithRole(GroupRole userRole, GroupRole memberNewRole) {
		return isUserAllowedToAddOrUpdateMemberWithRole(userRole, memberNewRole, null);
	}

	private boolean isUserAllowedToUpdateMemberWithRole(GroupRole userRole, GroupRole memberNewRole, GroupRole memberOldRole) {
		return isUserAllowedToAddOrUpdateMemberWithRole(userRole, memberNewRole, memberOldRole);
	}

	private boolean isUserAllowedToAddOrUpdateMemberWithRole(GroupRole userRole, GroupRole memberNewRole, GroupRole memberOldRole) {
		if(memberNewRole != null) {
			// validate new role
			if(memberNewRole == GroupRole.OWNER 
					&& (userRole != GroupRole.OWNER)) {
				return false;
			}

			// validate old role
			if(memberOldRole != null && memberOldRole == GroupRole.OWNER 
					&& (userRole != GroupRole.OWNER)) {
				return false;
			}

			// validate new role
			if(memberNewRole == GroupRole.ADMIN 
					&& (userRole != GroupRole.OWNER && userRole != GroupRole.ADMIN)) {
				return false;
			}	

			// validate old role
			if(memberOldRole != null && memberOldRole == GroupRole.ADMIN 
					&& (userRole != GroupRole.OWNER && userRole != GroupRole.ADMIN)) {
				return false;
			}
		}

		return true;
	}    
}
