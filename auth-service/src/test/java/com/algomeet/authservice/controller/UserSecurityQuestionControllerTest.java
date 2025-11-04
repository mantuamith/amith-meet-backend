package com.algomeet.authservice.controller;

import com.algomeet.authservice.config.LocalizationConfig;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.algomeet.authservice.service.UserSecurityQuestionService;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.authservice.util.MessageUtil;
import com.algomeet.authservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserSecurityQuestionController.class)
@Import(LocalizationConfig.class) // include your config
class UserSecurityQuestionControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper objectMapper;

	@MockBean UserSecurityQuestionService service;

	@Autowired MessageSource messageSource;

	// Mock beans often required by your security/config; keep them to avoid context load issues
	@MockBean
	JwtUtil jwtUtil;
	@MockBean
	SidCache sidCache;
	@MockBean
	OtpRepository otpRepository;
	@MockBean
	PendingPasswordResetRepository pendingPasswordResetRepository;
	@MockBean
	PendingRegistrationRepository pendingRegistrationRepository;

	private static UserSecurityQuestionRequest req(String userProfileId, String qid, String answer) {
		UserSecurityQuestionRequest r = new UserSecurityQuestionRequest();
		r.setUserProfileId(userProfileId);
		r.setSecurityQuestionId(qid);
		r.setAnswer(answer);
		return r;
	}

	private static UserSecurityQuestionResponse resp(UUID userProfileId, String qid, String answer) {
		UserSecurityQuestionResponse r = new UserSecurityQuestionResponse();
		r.setUserProfileId(userProfileId);
		r.setSecurityQuestionId(qid);
		r.setAnswer(answer);
		return r;
	}

	@BeforeEach
	void init() {
		// Initialize messageSource into the MessageUtil constructor
		new MessageUtil(messageSource);
	}

	@Test
	@WithMockUser
	void create_duplicate_returns409() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var request = req(up.toString(), "Q1", "secret");
			when(service.getByUserProfileIdAndQuestionId(eq(up), eq("Q1")))
			.thenReturn(resp(up, "Q1", "already"));

			mvc.perform(post("/auth/user-security-questions")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ResponseCode.USER_SECURITY_QUESTION_ID_EXISTS.getCode()));

			verify(service, never()).create(any());
		}
	}

	@Test
	@WithMockUser
	void create_success_masksAnswer() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var request = req(up.toString(), "Q1", "secret");
			when(service.getByUserProfileIdAndQuestionId(eq(up), eq("Q1")))
			.thenReturn(null);

			var svcResp = resp(up, "Q1", "secret"); // service returns real answer
			when(service.create(any())).thenReturn(svcResp);

			mvc.perform(post("/auth/user-security-questions")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answer").value("***"));
		}
	}

	@Test
	@WithMockUser
	void batch_empty_returns400() throws Exception {
		mvc.perform(post("/auth/user-security-questions/batch")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("[]"))
		.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	void batch_duplicate_any_returns409() throws Exception {
		UUID up = UUID.randomUUID();
		// Mock the static method
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var r1 = req(up.toString(), "Q1", "a1");
			var r2 = req(up.toString(), "Q2", "a2");

			when(service.getByUserProfileIdAndQuestionId(eq(up), eq("Q1")))
			.thenReturn(resp(up, "Q1", "x"));

			mvc.perform(post("/auth/user-security-questions/batch")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(List.of(r1, r2))))
			.andExpect(status().isConflict());

			verify(service, never()).create(any());
		}
	}

	@Test
	@WithMockUser
	void batch_success_masksAll() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var r1 = req(up.toString(), "Q1", "a1");
			var r2 = req(up.toString(), "Q2", "a2");

			when(service.getByUserProfileIdAndQuestionId(eq(up), anyString())).thenReturn(null);
			when(service.create(ArgumentMatchers.any()))
			.thenReturn(resp(up, "QX", "plain"));

			mvc.perform(post("/auth/user-security-questions/batch")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(List.of(r1, r2))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].answer", everyItem(is("***"))));
		}
	}

	@Test
	@WithMockUser
	void getByUserProfileIdAndQuestionId_notFound_returns404() throws Exception {
		UUID up = UUID.randomUUID();
		when(service.getByUserProfileIdAndQuestionId(up, "Q1")).thenReturn(null);

		mvc.perform(get("/auth/user-security-questions/{userProfileId}/{qid}", up, "Q1"))
		.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void getByUserProfileIdAndQuestionId_found_masksAnswer() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);
			when(service.getByUserProfileIdAndQuestionId(up, "Q1"))
			.thenReturn(resp(up, "Q1", "secret"));

			mvc.perform(get("/auth/user-security-questions/{userProfileId}/{qid}", up, "Q1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answer").value("***"));

		}
	}

	@Test
	@WithMockUser
	void verifyAnswer_notFound_returns404() throws Exception {
		UUID up = UUID.randomUUID();
		when(service.getByUserProfileIdAndQuestionId(up, "Q1")).thenReturn(null);

		var verifyReq = new VerifySecurityQuestionRequest();
		verifyReq.setAnswer("x");

		mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(verifyReq)))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.data.valid").value(false));
	}

	@Test
	@WithMockUser
	void verifyAnswer_correct_returnsTrue() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			when(service.getByUserProfileIdAndQuestionId(up, "Q1"))
			.thenReturn(resp(up, "Q1", "HeLLo"));

			var verifyReq = new VerifySecurityQuestionRequest();
			verifyReq.setAnswer("  hello ");

			mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(verifyReq)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.valid").value(true))
			.andExpect(jsonPath("$.data.message", containsString("correct")));
		}
	}

	@Test
	@WithMockUser
	void verifyAnswer_incorrect_returnsFalse() throws Exception {
		UUID up = UUID.randomUUID();

		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			when(service.getByUserProfileIdAndQuestionId(up, "Q1"))
			.thenReturn(resp(up, "Q1", "answer"));

			var verifyReq = new VerifySecurityQuestionRequest();
			verifyReq.setAnswer("nope");

			mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(verifyReq)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.valid").value(false))
			.andExpect(jsonPath("$.data.message", containsString("incorrect")));

		}
	}

	@Test
	@WithMockUser
	void deleteByUserProfileId_ok() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);
			mvc.perform(delete("/auth/user-security-questions/{userProfileId}", up).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(ResponseCode.DELETE_USER_SECURITY_QUESTION_SUCCESS.getCode()));

			verify(service).deleteByUserProfileId(up);
		}
	}

	// ---------- NEW: verify-answer matrix (trim, case-insensitive, mismatch, whitespace) ----------
	@ParameterizedTest
	@WithMockUser
	@CsvSource({
		// saved,   request,     expectedValid
		"HeLLo,    ' hello ',    true",
		"answer,   ANSWER,       true",
		"answer,   ' Answer ',   true",
		"answer,   different,    false",
		"answer,   '  ',         false",
		"a,        a,            true"
	})
	void verifyAnswer_matrix(String savedAnswer, String requestAnswer, boolean expectedValid) throws Exception {
		UUID up = UUID.randomUUID();
		// Mock the static method
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var saved = new UserSecurityQuestionResponse();
			saved.setUserProfileId(up);
			saved.setSecurityQuestionId("Q1");
			saved.setAnswer(savedAnswer);

			when(service.getByUserProfileIdAndQuestionId(up, "Q1")).thenReturn(saved);

			var req = new VerifySecurityQuestionRequest();
			req.setAnswer(requestAnswer);

			mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.valid").value(expectedValid))
			.andExpect(jsonPath("$.data.message", containsString(expectedValid ? "correct" : "incorrect")));
		}
	}

	// ---------- NEW: saved answer is null -> returns false, 200 ----------
	@Test
	@WithMockUser
	void verifyAnswer_savedAnswerNull_returnsFalse() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);
			var saved = new UserSecurityQuestionResponse();
			saved.setUserProfileId(up);
			saved.setSecurityQuestionId("Q1");
			saved.setAnswer(null);

			when(service.getByUserProfileIdAndQuestionId(up, "Q1")).thenReturn(saved);

			var req = new VerifySecurityQuestionRequest();
			req.setAnswer("anything");

			mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.valid").value(false))
			.andExpect(jsonPath("$.data.message", containsString("incorrect")));
		}
	}

	@Test
	@WithMockUser
	void verifyAnswer_emptyString_failsValidation400() throws Exception {
		UUID up = UUID.randomUUID();

		mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"\"}"))
		.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	@WithMockUser
	void verifyAnswer_questionMissing_returns404() throws Exception {
		UUID up = UUID.randomUUID();
		when(service.getByUserProfileIdAndQuestionId(up, "Q1")).thenReturn(null);

		var req = new VerifySecurityQuestionRequest();
		req.setAnswer("anything");

		mvc.perform(post("/auth/user-security-questions/{userProfileId}/{qid}/verify-answer", up, "Q1")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code").exists());
	}

	@WithMockUser
	@Test
	void create_conflict_returns409() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var req = new UserSecurityQuestionRequest();
			req.setUserProfileId(up.toString());
			req.setSecurityQuestionId("Q1");
			req.setAnswer("secret");

			// simulate duplicate
			when(service.getByUserProfileIdAndQuestionId(
					eq(UUID.fromString(req.getUserProfileId())), eq("Q1")))
			.thenReturn(new UserSecurityQuestionResponse());

			mvc.perform(post("/auth/user-security-questions")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ResponseCode.USER_SECURITY_QUESTION_ID_EXISTS.getCode()));
		}
	}

	@WithMockUser
	@Test
	void batch_conflict_shortCircuits_returns409() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var r1 = new UserSecurityQuestionRequest();
			r1.setUserProfileId(up.toString());
			r1.setSecurityQuestionId("Q1");
			r1.setAnswer("a1");

			var r2 = new UserSecurityQuestionRequest();
			r2.setUserProfileId(up.toString());
			r2.setSecurityQuestionId("Q2");
			r2.setAnswer("a2");

			// first pass detects existing Q1 → 409 (controller checks all first)
			when(service.getByUserProfileIdAndQuestionId(up, "Q1"))
			.thenReturn(new UserSecurityQuestionResponse());
			when(service.getByUserProfileIdAndQuestionId(up, "Q2"))
			.thenReturn(null);

			mvc.perform(post("/auth/user-security-questions/batch")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(List.of(r1, r2))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ResponseCode.USER_SECURITY_QUESTION_ID_EXISTS.getCode()));
		}
	}

	@WithMockUser
	@Test
	void getByUserProfileId_masksAnswers() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			var r1 = new UserSecurityQuestionResponse();
			r1.setUserProfileId(up);
			r1.setSecurityQuestionId("Q1");
			r1.setAnswer("AAA");

			var r2 = new UserSecurityQuestionResponse();
			r2.setUserProfileId(up);
			r2.setSecurityQuestionId("Q2");
			r2.setAnswer("BBB");

			when(service.getByUserProfileId(up)).thenReturn(List.of(r1, r2));

			mvc.perform(get("/auth/user-security-questions/{userProfileId}", up))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].answer").value("***"))
			.andExpect(jsonPath("$.data[1].answer").value("***"));
		}
	}

	@WithMockUser
	@Test
	void deleteByUserProfileId_callsService_returns200() throws Exception {
		UUID up = UUID.randomUUID();
		// Mock the static method
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			mvc.perform(delete("/auth/user-security-questions/{userProfileId}", up).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(ResponseCode.DELETE_USER_SECURITY_QUESTION_SUCCESS.getCode()));

			verify(service).deleteByUserProfileId(up);
		}
	}

	@WithMockUser
	@Test
	void getByUserProfileId_emptyList_returnsOkAndNoMasking() throws Exception {
		UUID up = UUID.randomUUID();

		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			when(service.getByUserProfileId(up)).thenReturn(List.of());

			mvc.perform(get("/auth/user-security-questions/{userProfileId}", up))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(0))); // empty array
		}
	}

	@WithMockUser
	@Test
	void getByUserProfileId_null_returnsOkWithNullData() throws Exception {
		UUID up = UUID.randomUUID();
		try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
			mocked.when(SecurityUtil::getUserKey).thenReturn(up);

			when(service.getByUserProfileId(up)).thenReturn(null);

			mvc.perform(get("/auth/user-security-questions/{userProfileId}", up))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());
		}

	}
}
