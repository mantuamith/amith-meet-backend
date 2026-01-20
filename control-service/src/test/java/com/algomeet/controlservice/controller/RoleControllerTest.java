package com.algomeet.controlservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.controlservice.config.LocalizationConfig;
import com.algomeet.controlservice.controller.RoleController;
import com.algomeet.controlservice.dto.CreateRoleRequest;
import com.algomeet.controlservice.dto.RoleRequest;
import com.algomeet.controlservice.dto.RoleResponse;
import com.algomeet.controlservice.enums.ResponseCode;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.RoleIdAlreadyExistsException;
import com.algomeet.controlservice.filter.JwtAuthenticationFilter;
import com.algomeet.controlservice.service.RoleService;
import com.algomeet.controlservice.util.MessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;


@WebMvcTest(
		controllers = RoleController.class,
		excludeFilters = {
				@ComponentScan.Filter(
						type = FilterType.ASSIGNABLE_TYPE,
						classes = JwtAuthenticationFilter.class
						)
		}
		)
@Import(LocalizationConfig.class)
class RoleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private RoleService roleService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	MessageSource messageSource;

	@BeforeEach
	void init() {
		new MessageUtil(messageSource);
	}

	/* -------------------------------------------------
	 * CREATE ROLE
	 * ------------------------------------------------- */

	@Test
	@WithMockUser(roles = "SA")
	void createRole_success() throws Exception {
		CreateRoleRequest request = new CreateRoleRequest();
		request.setId("ROLE_ADMIN");
		request.setName("Administrator");

		RoleResponse response = new RoleResponse();
		response.setId("ADMIN");

		when(roleService.createRole(any()))
		.thenReturn(response);

		mockMvc.perform(post("/control/roles")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.ADD_ROLE_SUCCESS.name()))
		.andExpect(jsonPath("$.data.id").value("ADMIN"));
	}

	@Test
	@WithMockUser(roles = "SA")
	void createRole_roleIdAlreadyExists() throws Exception {
		when(roleService.createRole(any()))
		.thenThrow(new RoleIdAlreadyExistsException("exists"));
		
		CreateRoleRequest request = new CreateRoleRequest();
		request.setId("ROLE_ADMIN");
		request.setName("Administrator");

		mockMvc.perform(post("/control/roles")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isBadRequest())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.ROLE_ID_ALREADY_EXISTS.name()));
	}

	/* -------------------------------------------------
	 * GET ROLE
	 * ------------------------------------------------- */

	@Test
	@WithMockUser(roles = "SA")
	void getRole_success() throws Exception {
		RoleResponse response = new RoleResponse();
		response.setId("ADMIN");

		when(roleService.getRole("ADMIN")).thenReturn(response);

		mockMvc.perform(get("/control/roles/ADMIN"))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.id").value("ADMIN"));
	}

	@Test
	@WithMockUser(roles = "SA")
	void getRole_notFound() throws Exception {
	    when(roleService.getRole("ADMIN"))
	        .thenThrow(new RoleIdAlreadyExistsException("not found"));

	    mockMvc.perform(get("/control/roles/ADMIN"))
	        .andExpect(status().isNotFound())
	        .andExpect(jsonPath("$.code")
	            .value(ResponseCode.ROLE_ID_NOT_FOUND.name()));
	}

	/* -------------------------------------------------
	 * GET ALL ROLES
	 * ------------------------------------------------- */

	@Test
	@WithMockUser(roles = "SA")
	void getAllRoles_success() throws Exception {
		RoleResponse admin = new RoleResponse();
		admin.setId("ADMIN");

		RoleResponse user = new RoleResponse();
		user.setId("USER");

		when(roleService.getAllRoles())
		.thenReturn(List.of(admin, user));

		mockMvc.perform(get("/control/roles"))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.length()").value(2));
	}

	/* -------------------------------------------------
	 * UPDATE ROLE
	 * ------------------------------------------------- */

	@Test
	@WithMockUser(roles = "SA")
	void updateRole_success() throws Exception {
		RoleRequest request = new RoleRequest();
		request.setName("Updated");

		RoleResponse response = new RoleResponse();
		response.setId("ADMIN");

		when(roleService.updateRole(eq("ADMIN"), any()))
		.thenReturn(response);

		mockMvc.perform(put("/control/roles/ADMIN")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.UPDATE_ROLE_SUCCESS.name()));
	}

	@Test
	@WithMockUser(roles = "SA")
	void updateRole_notFound() throws Exception {
		when(roleService.updateRole(eq("ADMIN"), any()))
		.thenThrow(new RecordNotFoundException("not found"));

		mockMvc.perform(put("/control/roles/ADMIN")				 
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.ROLE_ID_NOT_FOUND.name()));
	}

	/* -------------------------------------------------
	 * DELETE ROLE
	 * ------------------------------------------------- */

	@Test
	@WithMockUser(roles = "SA")
	void deleteRole_success() throws Exception {
		doNothing().when(roleService).deleteRole("ADMIN");

		mockMvc.perform(delete("/control/roles/ADMIN")				 
				.with(csrf()))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.DELETE_ROLE_SUCCESS.name()));
	}

	@Test
	@WithMockUser(roles = "SA")
	void deleteRole_notFound() throws Exception {
		doThrow(new RecordNotFoundException("not found"))
		.when(roleService).deleteRole("ADMIN");

		mockMvc.perform(delete("/control/roles/ADMIN") 
				.with(csrf()))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.ROLE_ID_NOT_FOUND.name()));
	}
}
