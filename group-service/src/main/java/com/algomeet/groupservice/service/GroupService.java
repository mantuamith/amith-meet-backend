package com.algomeet.groupservice.service;

import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.GroupInviteLinkResponse;
import com.algomeet.groupservice.dto.GroupPermissionsPatchRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.RolePermissionsPatchRequest;
import com.algomeet.groupservice.dto.UpdateGroupRequest;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.model.RolePermissions;
import com.algomeet.groupservice.repository.GroupRepository;

import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class GroupService {

	private static final SecureRandom INVITE_CODE_RANDOM = new SecureRandom();
	private static final Set<GroupRole> MANAGED_PERMISSION_ROLES = EnumSet.of(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MEMBER);

	private final GroupRepository groupRepository;
	private final GroupInviteLinkFactory groupInviteLinkFactory;

	public GroupResponse createGroup(GroupRequest request, String username, String userKey) {
		Group group = GroupMapper.toEntity(request);
		initializeRolePermissions(group);

		if(request.isEmptyGroup()) {
			//Ignore member list
			group.setMembers(null);
		}
		group.setOwnerUserKey(userKey);
		// Use for audit
		group.setCreatedBy(userKey);
		return GroupMapper.toResponse(groupRepository.save(group));
	}

	public GroupPermissionsResponse patchGroupPermissions(UUID groupId, GroupPermissionsPatchRequest request, String userKey) {
		Group group = getGroupOrThrow(groupId);
		initializeRolePermissions(group);

		GroupRole actorRole = resolveActorRole(group, userKey);
		if (actorRole == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
		}

		if (!isPermissionManagementAllowed(actorRole)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update role permissions");
		}

		validateManagedRoles(request.getRolePermissions());
		request.getRolePermissions().forEach((role, patch) -> {
			RolePermissions permissions = group.getRolePermissions()
					.computeIfAbsent(role, this::defaultPermissionsForRole);
			mergeRolePermissions(permissions, patch);
			normalizeRolePermissions(role, permissions);
		});

		Group savedGroup = groupRepository.save(group);
		return GroupMapper.toPermissionsResponse(savedGroup);
	}

	public void removeGroup(UUID groupId) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);
		groupRepository.delete(group);
	}

	public GroupResponse joinGroup(UUID groupId, String username, String userKey, String nickname) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member member = new Member(userKey, username, nickname);
		member.setMemberStartDate(Instant.now().toEpochMilli());
		if (group.getMembers().contains(member)) {
			throw new IllegalStateException(ResponseCode.USER_ALREADY_GROUP_MEMBER.name());
		}

		group.getMembers().add(member);
		return GroupMapper.toResponse(groupRepository.save(group));
	}

	public GroupResponse joinGroupByInviteCode(UUID groupId, String inviteCode, String username, String userKey, String nickname) {
		Group group = getGroupOrThrow(groupId);

		if (!StringUtils.hasText(inviteCode) || !inviteCode.equals(group.getInviteCode())) {
			throw new IllegalArgumentException(ResponseCode.GROUP_INVITE_CODE_INVALID.name());
		}

		return joinGroup(groupId, username, userKey, nickname);
	}

	public GroupResponse addGroupMembers(UUID groupId, AddGroupMembersRequest request, String userKey) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member user = findMember(group.getMembers(), userKey);

		for(MemberRequest memberReq: request.getMembers()) {
			if(!isUserAllowedToAddMemberWithRole(user.getRole(), memberReq.getRole())) {			
				throw new RuntimeException("User not authorize to add user with role.");
			}
		}

		Set<Member> newMembers = request.getMembers().stream()
				.map(m -> {
					long memberStartDate = Instant.now().toEpochMilli();
					if(m.getRole() != null) {
						return new Member(m.getUserKey(), m.getUsername(), m.getNikname(), m.getRole(), memberStartDate);
					} else {
						return new Member(m.getUserKey(), m.getUsername(), m.getNikname(), null, memberStartDate);
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

	public void leaveGroup(UUID groupId, String userKey) throws GroupNotFoundException {
		Group group = getGroupOrThrow(groupId);

		Member member = new Member(userKey, null);
		if (!group.getMembers().remove(member)) {
			throw new IllegalStateException(ResponseCode.GROUP_MEMBER_NOT_FOUND.name());
		}

		groupRepository.save(group);
	}

	public void removeGroupMember(UUID groupId, String userKey) throws GroupNotFoundException {
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

	public List<GroupResponse> getGroupsByUsername(String username) {
		List<Group> groups = groupRepository.findByMembers_Username(username);

		if (CollectionUtils.isEmpty(groups)) {
			return List.of();
		}

		return groups.stream()
				.map(GroupMapper::toResponse)
				.collect(Collectors.toList());
	}
	
	public List<GroupResponse> getGroupsByUserKey(String userkey) {
		List<Group> groups = groupRepository.findByMembers_UserKey(userkey);

		if (CollectionUtils.isEmpty(groups)) {
			return List.of();
		}

		return groups.stream()
				.map(GroupMapper::toResponse)
				.collect(Collectors.toList());
	}

	private Group getGroupOrThrow(UUID groupId) throws GroupNotFoundException {
		return groupRepository.findById(groupId)
				.orElseThrow(() ->
				new GroupNotFoundException("Group Id not found"));
	}

	public GroupResponse getGroupById(UUID groupId) {
		Group group = getGroupOrThrow(groupId);

		return GroupMapper.toResponse(group);
	}

	public GroupInviteLinkResponse getOrCreateInviteLink(UUID groupId, String userKey) {
		Group group = getGroupOrThrow(groupId);
		getInviteLinkAuthorizedMember(group, userKey);

		if (!StringUtils.hasText(group.getInviteCode())) {
			group.setInviteCode(generateInviteCode());
			group = groupRepository.save(group);
		}

		return new GroupInviteLinkResponse(groupInviteLinkFactory.build(group.getId(), group.getInviteCode()));
	}

	public GroupInviteLinkResponse resetInviteLink(UUID groupId, String userKey) {
		Group group = getGroupOrThrow(groupId);
		getInviteLinkAuthorizedMember(group, userKey);

		group.setInviteCode(generateInviteCode());
		Group updatedGroup = groupRepository.save(group);

		return new GroupInviteLinkResponse(groupInviteLinkFactory.build(updatedGroup.getId(), updatedGroup.getInviteCode()));
	}

	public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, String userKey) {
		Group group = getGroupOrThrow(groupId);

		if (StringUtils.hasText(request.getName())) {
			group.setName(request.getName());
		}

		if (request.getDescription() != null) {
			group.setDescription(request.getDescription());
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

	private Member getInviteLinkAuthorizedMember(Group group, String userKey) {
//		if (StringUtils.hasText(group.getOwnerUserKey()) && group.getOwnerUserKey().equals(userKey)) {
//			return new Member(userKey, null, null, GroupRole.OWNER);
//		}

		if (CollectionUtils.isEmpty(group.getMembers())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
		}

		Member member = findMember(group.getMembers(), userKey);
		if (member == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
		}

		if (!isInviteLinkAllowed(member.getRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to manage this invite link");
		}

		return member;
	}

	private boolean isInviteLinkAllowed(GroupRole userRole) {
		return userRole == GroupRole.OWNER
				|| userRole == GroupRole.ADMIN
				|| userRole == GroupRole.MEMBER;
	}

	private boolean isPermissionManagementAllowed(GroupRole userRole) {
		return userRole == GroupRole.OWNER || userRole == GroupRole.ADMIN;
	}

	private void initializeRolePermissions(Group group) {
		if (group.getRolePermissions() == null) {
			group.setRolePermissions(new EnumMap<>(GroupRole.class));
		}

		for (GroupRole role : MANAGED_PERMISSION_ROLES) {
			group.getRolePermissions().computeIfAbsent(role, this::defaultPermissionsForRole);
		}

		group.getRolePermissions().entrySet().removeIf(entry -> !MANAGED_PERMISSION_ROLES.contains(entry.getKey()));
		group.getRolePermissions().forEach(this::normalizeRolePermissions);
	}

	private GroupRole resolveActorRole(Group group, String userKey) {
		if (StringUtils.hasText(group.getOwnerUserKey()) && group.getOwnerUserKey().equals(userKey)) {
			return GroupRole.OWNER;
		}

		if (CollectionUtils.isEmpty(group.getMembers())) {
			return null;
		}

		Member member = findMember(group.getMembers(), userKey);
		return member != null ? member.getRole() : null;
	}

	private void validateManagedRoles(Map<GroupRole, RolePermissionsPatchRequest> rolePermissions) {
		if (rolePermissions == null) {
			return;
		}

		for (GroupRole role : rolePermissions.keySet()) {
			if (!MANAGED_PERMISSION_ROLES.contains(role)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role for permission update: " + role);
			}
		}
	}

	private RolePermissions defaultPermissionsForRole(GroupRole role) {
		return switch (role) {
		case OWNER -> RolePermissions.ownerDefaults();
		case ADMIN -> RolePermissions.adminDefaults();
		case MEMBER -> RolePermissions.memberDefaults();
		default -> RolePermissions.memberDefaults();
		};
	}

	private void mergeRolePermissions(RolePermissions target, RolePermissionsPatchRequest patch) {
		if (patch == null) {
			return;
		}

		if (patch.getEditGroupSettings() != null) {
			target.setEditGroupSettings(patch.getEditGroupSettings());
		}
		if (patch.getSendNewMessages() != null) {
			target.setSendNewMessages(patch.getSendNewMessages());
		}
		if (patch.getAddOtherMembers() != null) {
			target.setAddOtherMembers(patch.getAddOtherMembers());
		}
		if (patch.getSendMessageHistory() != null) {
			target.setSendMessageHistory(patch.getSendMessageHistory());
		}
		if (patch.getInviteViaLinkOrQrCode() != null) {
			target.setInviteViaLinkOrQrCode(patch.getInviteViaLinkOrQrCode());
		}
		if (patch.getApproveNewMembers() != null) {
			target.setApproveNewMembers(patch.getApproveNewMembers());
		}
		if (patch.getEditGroupAdmins() != null) {
			target.setEditGroupAdmins(patch.getEditGroupAdmins());
		}
		if (patch.getRemoveMembers() != null) {
			target.setRemoveMembers(patch.getRemoveMembers());
		}
		if (patch.getDisableChatForMembers() != null) {
			target.setDisableChatForMembers(patch.getDisableChatForMembers());
		}
		if (patch.getDeleteGroup() != null) {
			target.setDeleteGroup(patch.getDeleteGroup());
		}
	}

	private void normalizeRolePermissions(GroupRole role, RolePermissions permissions) {
		if (role == GroupRole.OWNER) {
			permissions.setDeleteGroup(true);
			return;
		}

		permissions.setDeleteGroup(false);
	}

	private String generateInviteCode() {
		byte[] bytes = new byte[18];
		INVITE_CODE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
