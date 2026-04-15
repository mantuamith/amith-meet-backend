package com.algomeet.groupservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.groupservice.dto.GroupInviteLinkResponse;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceInviteLinkTest {

	@Mock
	private GroupRepository groupRepository;

	@Mock
	private GroupInviteLinkFactory groupInviteLinkFactory;

	@InjectMocks
	private GroupService groupService;

	@Captor
	private ArgumentCaptor<Group> groupCaptor;

	@Test
	void getOrCreateInviteLink_generatesAndPersistsCodeWhenMissing() {
		Group group = groupWithMember("group-user", GroupRole.MEMBER);
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(groupInviteLinkFactory.build(org.mockito.ArgumentMatchers.eq(1L), anyString()))
				.thenReturn("https://yourapp.com/invite?groupId=1&inviteCode=generated");

		GroupInviteLinkResponse response = groupService.getOrCreateInviteLink(1L, "group-user");

		verify(groupRepository).save(groupCaptor.capture());
		assertThat(groupCaptor.getValue().getInviteCode()).isNotBlank();
		assertThat(response.getInviteLink()).isEqualTo("https://yourapp.com/invite?groupId=1&inviteCode=generated");
	}

	@Test
	void getOrCreateInviteLink_reusesExistingCode() {
		Group group = groupWithMember("group-user", GroupRole.ADMIN);
		group.setInviteCode("existing-code");
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
		when(groupInviteLinkFactory.build(1L, "existing-code")).thenReturn("https://yourapp.com/invite?groupId=1&inviteCode=existing-code");

		GroupInviteLinkResponse response = groupService.getOrCreateInviteLink(1L, "group-user");

		verify(groupRepository, never()).save(any(Group.class));
		assertThat(response.getInviteLink()).isEqualTo("https://yourapp.com/invite?groupId=1&inviteCode=existing-code");
	}

	@Test
	void resetInviteLink_rotatesCode() {
		Group group = groupWithMember("group-user", GroupRole.OWNER);
		group.setInviteCode("old-code");
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(groupInviteLinkFactory.build(org.mockito.ArgumentMatchers.eq(1L), anyString()))
				.thenReturn("https://yourapp.com/invite?groupId=1&inviteCode=new-code");

		GroupInviteLinkResponse response = groupService.resetInviteLink(1L, "group-user");

		verify(groupRepository).save(groupCaptor.capture());
		assertThat(groupCaptor.getValue().getInviteCode()).isNotBlank().isNotEqualTo("old-code");
		assertThat(response.getInviteLink()).isEqualTo("https://yourapp.com/invite?groupId=1&inviteCode=new-code");
	}

	@Test
	void getOrCreateInviteLink_rejectsNonMember() {
		Group group = groupWithMember("someone-else", GroupRole.MEMBER);
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> groupService.getOrCreateInviteLink(1L, "missing-user"))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void joinGroupByInviteCode_rejectsInvalidCode() {
		Group group = groupWithMember("someone-else", GroupRole.MEMBER);
		group.setInviteCode("valid-code");
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> groupService.joinGroupByInviteCode(1L, "bad-code", "john", "new-user", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(ResponseCode.GROUP_INVITE_CODE_INVALID.name());
	}

	@Test
	void joinGroupByInviteCode_joinsWhenCodeMatches() {
		Group group = groupWithMember("someone-else", GroupRole.MEMBER);
		group.setInviteCode("valid-code");
		when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

		groupService.joinGroupByInviteCode(1L, "valid-code", "john", "new-user", "Nick");

		verify(groupRepository).save(groupCaptor.capture());
		assertThat(groupCaptor.getValue().getMembers())
				.extracting(Member::getUserKey)
				.contains("new-user");
	}

	private Group groupWithMember(String userKey, GroupRole role) {
		Group group = new Group();
		group.setId(1L);
		Set<Member> members = new HashSet<>();
		members.add(new Member(userKey, "john", "John", role));
		group.setMembers(members);
		return group;
	}
}
