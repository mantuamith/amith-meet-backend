package com.algomeet.groupservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.groupservice.dto.GroupPermissionsPatchRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.RolePermissionsPatchRequest;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;

@ExtendWith(MockitoExtension.class)
class GroupServicePermissionsTest {

	private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private GroupRepository groupRepository;

	@Mock
	private GroupInviteLinkFactory groupInviteLinkFactory;

	@InjectMocks
	private GroupService groupService;

	@Captor
	private ArgumentCaptor<Group> groupCaptor;

	@Test
	void patchGroupPermissions_mergesOnlyProvidedRolesAndFields() {
		Group group = groupWithMember("admin-user", GroupRole.ADMIN);
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

		GroupPermissionsPatchRequest request = new GroupPermissionsPatchRequest();
		RolePermissionsPatchRequest adminPatch = new RolePermissionsPatchRequest();
		adminPatch.setApproveNewMembers(false);
		RolePermissionsPatchRequest memberPatch = new RolePermissionsPatchRequest();
		memberPatch.setSendNewMessages(true);
		request.setRolePermissions(Map.of(
				GroupRole.ADMIN, adminPatch,
				GroupRole.MEMBER, memberPatch));

		GroupPermissionsResponse response = groupService.patchGroupPermissions(GROUP_ID, request, "admin-user");

		verify(groupRepository).save(groupCaptor.capture());
		Group savedGroup = groupCaptor.getValue();
		assertThat(savedGroup.getRolePermissions().get(GroupRole.ADMIN).isApproveNewMembers()).isFalse();
		assertThat(savedGroup.getRolePermissions().get(GroupRole.ADMIN).isDeleteGroup()).isFalse();
		assertThat(savedGroup.getRolePermissions().get(GroupRole.MEMBER).isSendNewMessages()).isTrue();
		assertThat(savedGroup.getRolePermissions().get(GroupRole.OWNER).isDeleteGroup()).isTrue();
		assertThat(response.getRolePermissions().get(GroupRole.MEMBER).isSendNewMessages()).isTrue();
	}

	@Test
	void patchGroupPermissions_rejectsNonPrivilegedMember() {
		Group group = groupWithMember("member-user", GroupRole.MEMBER);
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

		GroupPermissionsPatchRequest request = new GroupPermissionsPatchRequest();
		RolePermissionsPatchRequest adminPatch = new RolePermissionsPatchRequest();
		adminPatch.setApproveNewMembers(true);
		request.setRolePermissions(Map.of(GroupRole.ADMIN, adminPatch));

		assertThatThrownBy(() -> groupService.patchGroupPermissions(GROUP_ID, request, "member-user"))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void patchGroupPermissions_rejectsUnsupportedRole() {
		Group group = groupWithMember("owner-user", GroupRole.OWNER);
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

		GroupPermissionsPatchRequest request = new GroupPermissionsPatchRequest();
		RolePermissionsPatchRequest visitorPatch = new RolePermissionsPatchRequest();
		visitorPatch.setSendNewMessages(true);
		request.setRolePermissions(Map.of(GroupRole.VISITOR, visitorPatch));

		assertThatThrownBy(() -> groupService.patchGroupPermissions(GROUP_ID, request, "owner-user"))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private Group groupWithMember(String userKey, GroupRole role) {
		Group group = new Group();
		group.setId(GROUP_ID);
		Set<Member> members = new HashSet<>();
		members.add(new Member(userKey, "john", "John", role));
		group.setMembers(members);
		group.setOwnerUserKey(role == GroupRole.OWNER ? userKey : "owner-user");
		return group;
	}
}
