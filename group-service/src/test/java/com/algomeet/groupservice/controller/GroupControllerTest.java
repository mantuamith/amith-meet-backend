package com.algomeet.groupservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.groupservice.config.LocalizationConfig;
import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.GroupInviteLinkResponse;
import com.algomeet.groupservice.dto.GroupPermissionsPatchRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.RolePermissionsPatchRequest;
import com.algomeet.groupservice.dto.RolePermissionsResponse;
import com.algomeet.groupservice.enums.GroupRole;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.service.GroupService;
import com.algomeet.groupservice.util.MessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;


@WebMvcTest(controllers = GroupController.class, excludeFilters = {
		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {}) })
@ContextConfiguration(classes = GroupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private GroupService groupService;

	@MockBean
	private Authentication authentication;

	@Autowired
	private ObjectMapper objectMapper;
	
	@MockBean
	private MessageSource messageSource;

	private static final String USER_KEY = "2fc35cae-e0b7-40a5-b2aa-e86206730e88";
	private static final String USERNAME = "john";
	private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeEach
	void setup() {
		when(authentication.getName()).thenReturn(USERNAME);
		when(authentication.getDetails()).thenReturn(Map.of("user_key", USER_KEY));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		
		new MessageUtil(messageSource);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	/*
	 * ========================= CREATE =========================
	 */
	@Test
	void createGroup_success() throws Exception {
	    // Prepare GroupRequest with test data
	    GroupRequest request = new GroupRequest();
	    request.setName("Test Group");

	    Set<MemberRequest> members = new HashSet<>();

	    MemberRequest m1 = new MemberRequest();
	    m1.setUserKey("2fc35cae-e0b7-40a5-b2aa-e86206730e99");
	    m1.setUsername("User One");
	    members.add(m1);

	    MemberRequest m2 = new MemberRequest();
	    m2.setUserKey("2fc35cae-e0b7-40a5-b2aa-e86206730e6d");
	    m2.setUsername("User Two");
	    members.add(m2);

	    request.setMembers(members);

	    // Mock service response
	    GroupResponse response = new GroupResponse();
	    when(groupService.createGroup(any(), eq(USERNAME), eq(USER_KEY)))
	        .thenReturn(response);

	    // Perform POST request
	    mockMvc.perform(post("/api/groups/create")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(objectMapper.writeValueAsString(request))
	            .principal(authentication))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

	    // Verify service interaction
	    verify(groupService).createGroup(any(), eq(USERNAME), eq(USER_KEY));
	}

	@Test
	void patchGroupPermissions_success() throws Exception {
		GroupPermissionsPatchRequest request = new GroupPermissionsPatchRequest();
		RolePermissionsPatchRequest adminPatch = new RolePermissionsPatchRequest();
		adminPatch.setApproveNewMembers(true);
		request.setRolePermissions(Map.of(GroupRole.ADMIN, adminPatch));

		GroupPermissionsResponse response = new GroupPermissionsResponse();
		response.setGroupId(GROUP_ID);
		RolePermissionsResponse adminPermissions = new RolePermissionsResponse();
		adminPermissions.setApproveNewMembers(true);
		response.setRolePermissions(Map.of(GroupRole.ADMIN, adminPermissions));

		when(groupService.patchGroupPermissions(eq(GROUP_ID), any(), eq(USER_KEY)))
				.thenReturn(response);

		mockMvc.perform(patch("/api/groups/{id}/permissions", GROUP_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.groupId").value(GROUP_ID.toString()))
		.andExpect(jsonPath("$.data.rolePermissions.ADMIN.approveNewMembers").value(true));

		verify(groupService).patchGroupPermissions(eq(GROUP_ID), any(), eq(USER_KEY));
	}


	/*
	 * ========================= DELETE GROUP =========================
	 */

	@Test
	void removeGroup_success() throws Exception {
		mockMvc.perform(delete("/api/groups/{id}", GROUP_ID))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).removeGroup(GROUP_ID);
	}

	@Test
	void removeGroup_notFound() throws Exception {
		doThrow(new GroupNotFoundException("not found"))
		.when(groupService).removeGroup(GROUP_ID);

		mockMvc.perform(delete("/api/groups/{id}", GROUP_ID))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.GROUP_ID_NOT_FOUND.name()));
	}

	/*
	 * ========================= JOIN =========================
	 */

	@Test
	void joinGroup_success() throws Exception {
		when(groupService.joinGroup(GROUP_ID, USERNAME, USER_KEY, null))
		.thenReturn(new GroupResponse());

		mockMvc.perform(post("/api/groups/{id}/join", GROUP_ID)
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void joinGroup_alreadyMember() throws Exception {
		when(groupService.joinGroup(any(), any(), any(), any()))
		.thenThrow(new IllegalStateException());

		mockMvc.perform(post("/api/groups/{id}/join", GROUP_ID)
				.principal(authentication))
		.andExpect(status().isConflict())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_ALREADY_GROUP_MEMBER.name()));
	}

	@Test
	void joinGroupByInvite_success() throws Exception {
		when(groupService.joinGroupByInviteCode(GROUP_ID, "abc123", USERNAME, USER_KEY, null))
		.thenReturn(new GroupResponse());

		mockMvc.perform(post("/api/groups/{id}/join-by-invite", GROUP_ID)
				.param("inviteCode", "abc123")
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void joinGroupByInvite_invalidCode() throws Exception {
		when(groupService.joinGroupByInviteCode(GROUP_ID, "bad-code", USERNAME, USER_KEY, null))
		.thenThrow(new IllegalArgumentException());

		mockMvc.perform(post("/api/groups/{id}/join-by-invite", GROUP_ID)
				.param("inviteCode", "bad-code")
				.principal(authentication))
		.andExpect(status().isBadRequest())
		.andExpect(jsonPath("$.code").value(ResponseCode.GROUP_INVITE_CODE_INVALID.name()));
	}

	/*
	 * ========================= ADD MEMBERS =========================
	 */

	@Test
	void addGroupMembers_success() throws Exception {
	    // Prepare the request with members
	    AddGroupMembersRequest request = new AddGroupMembersRequest();
	    Set<MemberRequest> members = new HashSet<>();
	    
	    MemberRequest m1 = new MemberRequest();
	    m1.setUserKey("u1");
	    m1.setUsername("User One");
	    members.add(m1);
	    
	    MemberRequest m2 = new MemberRequest();
	    m2.setUserKey("u2");
	    m2.setUsername("User Two");
	    members.add(m2);
	    
	    request.setMembers(members);

	    // Mock service behavior
	    when(groupService.addGroupMembers(eq(GROUP_ID), any(), any()))
	        .thenReturn(new GroupResponse());

	    // Perform the POST request
	    mockMvc.perform(post("/api/groups/{id}/add", GROUP_ID)
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(objectMapper.writeValueAsString(request)))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

	    // Verify service call
	    verify(groupService).addGroupMembers(eq(GROUP_ID), any(), any());
	}


	/*
	 * ========================= LEAVE =========================
	 */

	@Test
	void leaveGroup_success() throws Exception {
		mockMvc.perform(delete("/api/groups/{id}/leave", GROUP_ID)
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).leaveGroup(GROUP_ID, USER_KEY);
	}

	@Test
	void leaveGroup_memberNotFound() throws Exception {
		doThrow(new IllegalStateException())
		.when(groupService).leaveGroup(any(), any());

		mockMvc.perform(delete("/api/groups/{id}/leave", GROUP_ID)
				.principal(authentication))
		.andExpect(status().isConflict())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.GROUP_MEMBER_NOT_FOUND.name()));
	}

	/*
	 * ========================= REMOVE MEMBER =========================
	 */

	@Test
	void removeGroupMember_success() throws Exception {
		mockMvc.perform(delete("/api/groups/{id}/remove", GROUP_ID)
				.param("userKey", "u2"))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).removeGroupMember(GROUP_ID, "u2");
	}

	/*
	 * ========================= MY GROUPS =========================
	 */

	@Test
	void getMyGroups_success() throws Exception {
		when(groupService.getMyGroups(USER_KEY))
		.thenReturn(List.of(new GroupResponse()));

		mockMvc.perform(get("/api/groups/mine")
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void getInviteLink_success() throws Exception {
		when(groupService.getOrCreateInviteLink(GROUP_ID, USER_KEY))
		.thenReturn(new GroupInviteLinkResponse("https://yourapp.com/invite?groupId=11111111-1111-1111-1111-111111111111&inviteCode=abc"));

		mockMvc.perform(get("/api/groups/{id}/invite-link", GROUP_ID))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.inviteLink").value("https://yourapp.com/invite?groupId=11111111-1111-1111-1111-111111111111&inviteCode=abc"));

		verify(groupService).getOrCreateInviteLink(GROUP_ID, USER_KEY);
	}

	@Test
	void getInviteLink_notFound() throws Exception {
		when(groupService.getOrCreateInviteLink(GROUP_ID, USER_KEY))
		.thenThrow(new GroupNotFoundException("not found"));

		mockMvc.perform(get("/api/groups/{id}/invite-link", GROUP_ID))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code").value(ResponseCode.GROUP_ID_NOT_FOUND.name()));
	}

	@Test
	void resetInviteLink_success() throws Exception {
		when(groupService.resetInviteLink(GROUP_ID, USER_KEY))
		.thenReturn(new GroupInviteLinkResponse("https://yourapp.com/invite?groupId=11111111-1111-1111-1111-111111111111&inviteCode=def"));

		mockMvc.perform(post("/api/groups/{id}/invite-link/reset", GROUP_ID))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.inviteLink").value("https://yourapp.com/invite?groupId=11111111-1111-1111-1111-111111111111&inviteCode=def"));

		verify(groupService).resetInviteLink(GROUP_ID, USER_KEY);
	}

	@Test
	void resetInviteLink_notFound() throws Exception {
		when(groupService.resetInviteLink(GROUP_ID, USER_KEY))
		.thenThrow(new GroupNotFoundException("not found"));

		mockMvc.perform(post("/api/groups/{id}/invite-link/reset", GROUP_ID))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code").value(ResponseCode.GROUP_ID_NOT_FOUND.name()));
	}
}
