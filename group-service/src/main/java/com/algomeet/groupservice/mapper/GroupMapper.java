package com.algomeet.groupservice.mapper;

import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.MemberResponse;
import com.algomeet.groupservice.dto.RolePermissionsResponse;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.model.RolePermissions;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GroupMapper {

	private GroupMapper() {
		// utility class
	}

	public static Group toEntity(GroupRequest req) {
		if (req == null) {
			return null;
		}

		Group group = new Group();
		group.setName(req.getName());
		group.setDescription(req.getDescription());
		group.setMessageRetentionDays(req.getMessageRetentionDays());

		Set<Member> members = new HashSet<>();
		long memberStartDate = Instant.now().toEpochMilli();
		if (req.getMembers() != null) {
			for (MemberRequest memberReq : req.getMembers()) {
				Member member = null;
				
				if(memberReq.getRole() == null) {
					member = new Member(memberReq.getUserKey(), 
							memberReq.getUsername(), memberReq.getNikname(), null, memberStartDate);
				} else {
					member = new Member(memberReq.getUserKey(), 
							memberReq.getUsername(), memberReq.getNikname(), memberReq.getRole(), memberStartDate);
				}
				
				members.add(member);
			}
		}

		group.setMembers(members);
		return group;
	}

	/*
	 * ========================= Entity → Response =========================
	 */
	public static GroupResponse toResponse(Group entity) {
		if (entity == null) {
			return null;
		}

		GroupResponse dto = new GroupResponse();
		dto.setId(entity.getId());
		dto.setName(entity.getName());
		dto.setDescription(entity.getDescription());
		dto.setOwnerUserKey(entity.getOwnerUserKey());
		dto.setCreatedAt(entity.getDateCreated().toEpochMilli());
		dto.setMessageRetentionDays(entity.getMessageRetentionDays());

		Set<MemberResponse> members = new HashSet<>();
		if (entity.getMembers() != null) {
			for (Member member : entity.getMembers()) {
				MemberResponse memberResp = new MemberResponse();
				memberResp.setUserKey(member.getUserKey());
				memberResp.setUsername(member.getUsername());
				memberResp.setNickname(member.getNickname());

				memberResp.setRole(member.getRole());
				memberResp.setMemberStartDate(member.getMemberStartDate());
				memberResp.setMessageHistoryCutoff(member.getMessageHistoryCutoff());
				members.add(memberResp);
			}
		}

		dto.setMembers(members);

		Map<GroupRole, RolePermissionsResponse> rolePermissions = new EnumMap<>(GroupRole.class);
		if (entity.getRolePermissions() != null) {
			entity.getRolePermissions().forEach((role, permissions) ->
					rolePermissions.put(role, toPermissionsResponse(permissions)));
		}
		dto.setRolePermissions(rolePermissions);

		return dto;
	}

	public static GroupPermissionsResponse toPermissionsResponse(Group entity) {
		if (entity == null) {
			return null;
		}

		GroupPermissionsResponse response = new GroupPermissionsResponse();
		response.setGroupId(entity.getId());

		Map<GroupRole, RolePermissionsResponse> rolePermissions = new EnumMap<>(GroupRole.class);
		if (entity.getRolePermissions() != null) {
			entity.getRolePermissions().forEach((role, permissions) ->
					rolePermissions.put(role, toPermissionsResponse(permissions)));
		}

		response.setRolePermissions(rolePermissions);
		return response;
	}

	private static RolePermissionsResponse toPermissionsResponse(RolePermissions permissions) {
		RolePermissionsResponse response = new RolePermissionsResponse();
		if (permissions == null) {
			return response;
		}

		response.setEditGroupSettings(permissions.isEditGroupSettings());
		response.setSendNewMessages(permissions.isSendNewMessages());
		response.setAddOtherMembers(permissions.isAddOtherMembers());
		response.setSendMessageHistory(permissions.isSendMessageHistory());
		response.setInviteViaLinkOrQrCode(permissions.isInviteViaLinkOrQrCode());
		response.setApproveNewMembers(permissions.isApproveNewMembers());
		response.setEditGroupAdmins(permissions.isEditGroupAdmins());
		response.setRemoveMembers(permissions.isRemoveMembers());
		response.setDisableChatForMembers(permissions.isDisableChatForMembers());
		response.setDeleteGroup(permissions.isDeleteGroup());
		return response;
	}
}
