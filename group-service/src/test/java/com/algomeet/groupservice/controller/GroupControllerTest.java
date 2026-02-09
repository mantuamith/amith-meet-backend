package com.algomeet.groupservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.groupservice.config.LocalizationConfig;
import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.service.GroupService;
import com.algomeet.groupservice.util.MessageUtil;
import com.algomeet.groupservice.util.SecurityUtil;
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

	private MockedStatic<SecurityUtil> securityUtilMock;
	
	@MockBean
	private MessageSource messageSource;

	private static final String USER_KEY = "2fc35cae-e0b7-40a5-b2aa-e86206730e88";
	private static final String USERNAME = "john";

	@BeforeEach
	void setup() {
		securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
		securityUtilMock.when(SecurityUtil::getUserKey).thenReturn(USER_KEY);

		when(authentication.getName()).thenReturn(USERNAME);
		
		new MessageUtil(messageSource);
	}

	@AfterEach
	void tearDown() {
		securityUtilMock.close();
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
	    mockMvc.perform(post("/groups/create")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(objectMapper.writeValueAsString(request))
	            .principal(authentication))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

	    // Verify service interaction
	    verify(groupService).createGroup(any(), eq(USERNAME), eq(USER_KEY));
	}


	/*
	 * ========================= DELETE GROUP =========================
	 */

	@Test
	void removeGroup_success() throws Exception {
		mockMvc.perform(delete("/groups/{id}", 1L))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).removeGroup(1L);
	}

	@Test
	void removeGroup_notFound() throws Exception {
		doThrow(new GroupNotFoundException("not found"))
		.when(groupService).removeGroup(1L);

		mockMvc.perform(delete("/groups/{id}", 1L))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.GROUP_ID_NOT_FOUND.name()));
	}

	/*
	 * ========================= JOIN =========================
	 */

	@Test
	void joinGroup_success() throws Exception {
		when(groupService.joinGroup(1L, USERNAME, USER_KEY))
		.thenReturn(new GroupResponse());

		mockMvc.perform(post("/groups/{id}/join", 1L)
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void joinGroup_alreadyMember() throws Exception {
		when(groupService.joinGroup(any(), any(), any()))
		.thenThrow(new IllegalStateException());

		mockMvc.perform(post("/groups/{id}/join", 1L)
				.principal(authentication))
		.andExpect(status().isConflict())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_ALREADY_GROUP_MEMBER.name()));
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
	    when(groupService.addGroupMembers(eq(1L), any()))
	        .thenReturn(new GroupResponse());

	    // Perform the POST request
	    mockMvc.perform(post("/groups/{id}/add", 1L)
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(objectMapper.writeValueAsString(request)))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

	    // Verify service call
	    verify(groupService).addGroupMembers(eq(1L), any());
	}


	/*
	 * ========================= LEAVE =========================
	 */

	@Test
	void leaveGroup_success() throws Exception {
		mockMvc.perform(delete("/groups/{id}/leave", 1L)
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).leaveGroup(1L, USER_KEY);
	}

	@Test
	void leaveGroup_memberNotFound() throws Exception {
		doThrow(new IllegalStateException())
		.when(groupService).leaveGroup(any(), any());

		mockMvc.perform(delete("/groups/{id}/leave", 1L)
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
		mockMvc.perform(delete("/groups/{id}/remove", 1L)
				.param("userKey", "u2"))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

		verify(groupService).removeGroupMember(1L, "u2");
	}

	/*
	 * ========================= MY GROUPS =========================
	 */

	@Test
	void getMyGroups_success() throws Exception {
		when(groupService.getMyGroups(USER_KEY))
		.thenReturn(List.of(new GroupResponse()));

		mockMvc.perform(get("/groups/mine")
				.principal(authentication))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
	}
}
