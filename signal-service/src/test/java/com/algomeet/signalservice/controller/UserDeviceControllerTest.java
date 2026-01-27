package com.algomeet.signalservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.signalservice.config.LocalizationConfig;
import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.DevicePreKeyBundleResponse;
import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.DeviceExistsException;
import com.algomeet.signalservice.exceptions.OneTimePreKeyExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.security.filter.JwtAuthenticationFilter;
import com.algomeet.signalservice.service.UserDeviceService;
import com.algomeet.signalservice.util.MessageUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;


@WebMvcTest(
		controllers = UserDeviceController.class,
		excludeFilters = {
				@ComponentScan.Filter(
						type = FilterType.ASSIGNABLE_TYPE,
						classes = JwtAuthenticationFilter.class
						)
		}
		)
@ContextConfiguration(classes = UserDeviceController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
		MongoAutoConfiguration.class,
		MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class UserDeviceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private UserDeviceService service;

	@Autowired
	private ObjectMapper objectMapper;

	private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private MockedStatic<SecurityUtil> securityUtilMock;

	@Autowired
	MessageSource messageSource;

	@BeforeEach
	void setup() {
		securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
		securityUtilMock.when(SecurityUtil::getUserKey)
		.thenReturn(USER_KEY.toString());

		new MessageUtil(messageSource);
	}

	@AfterEach
	void tearDown() {
		if (securityUtilMock != null) {
			securityUtilMock.close();
		}
	}

	/* -------------------------------------------------
	 * CREATE DEVICE
	 * ------------------------------------------------- */

	@Test
	void createDevice_success() throws Exception {
		UserDeviceRequest request = new UserDeviceRequest();
		request.setIdentityKey("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D");
		request.setRegistrationId(1);
		
		UserDeviceResponse response = new UserDeviceResponse();

		when(service.registerDevice(eq(USER_KEY), any()))
		.thenReturn(response);

		mockMvc.perform(post("/signal/v2/devices")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void createDevice_alreadyExists() throws Exception {
		when(service.registerDevice(eq(USER_KEY), any()))
		.thenThrow(new DeviceExistsException("exists"));
		
		UserDeviceRequest request = new UserDeviceRequest();
		request.setIdentityKey("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D");
		request.setRegistrationId(1);

		mockMvc.perform(post("/signal/v2/devices")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isConflict())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_DEVICE_EXISTS.name()));
	}

	/* -------------------------------------------------
	 * GET DEVICES
	 * ------------------------------------------------- */

	@Test
	void getDevices_success() throws Exception {
		when(service.getDevicesByUser(USER_KEY))
		.thenReturn(List.of(new UserDeviceResponse()));

		mockMvc.perform(get("/signal/v2/devices"))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()))
		.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void getDevices_withUserKeyParam() throws Exception {
		UUID otherUser = UUID.randomUUID();

		when(service.getDevicesByUser(otherUser))
		.thenReturn(List.of());

		mockMvc.perform(get("/signal/v2/devices")
				.param("userKey", otherUser.toString()))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()));
	}

	/* -------------------------------------------------
	 * UPDATE DEVICE
	 * ------------------------------------------------- */

	@Test
	void updateDevice_success() throws Exception {
		UserDeviceRequest request = new UserDeviceRequest();
		request.setIdentityKey("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D");
		request.setRegistrationId(1);
		
		UserDeviceResponse response = new UserDeviceResponse();

		when(service.updateDevice(eq(USER_KEY), eq(1), any()))
		.thenReturn(response);

		mockMvc.perform(put("/signal/v2/devices/1")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void updateDevice_notFound() throws Exception {
		when(service.updateDevice(eq(USER_KEY), eq(1), any()))
		.thenThrow(new RecordNotFoundException("not found"));
		
		UserDeviceRequest request = new UserDeviceRequest();
		request.setIdentityKey("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D");
		request.setRegistrationId(1);

		mockMvc.perform(put("/signal/v2/devices/1")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
	}

	/* -------------------------------------------------
	 * DELETE DEVICE
	 * ------------------------------------------------- */

	@Test
	void deleteDevice_success() throws Exception {
		doNothing().when(service).deleteDevice(USER_KEY, 1);

		mockMvc.perform(delete("/signal/v2/devices/1")
				.with(csrf()))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void deleteDevice_notFound() throws Exception {
		doThrow(new RecordNotFoundException("not found"))
		.when(service).deleteDevice(USER_KEY, 1);

		mockMvc.perform(delete("/signal/v2/devices/1")
				.with(csrf()))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
	}

	/* -------------------------------------------------
	 * CREATE DEVICE PRE-KEY BUNDLE
	 * ------------------------------------------------- */

	@Test
	void createDevicePreKeyBundle_success() throws Exception {
		DevicePreKeyBundleRequest req = new DevicePreKeyBundleRequest();
		SignedPreKeyRequest signedPreKeyRequest = new SignedPreKeyRequest();
		signedPreKeyRequest.setSignedPreKeyId(1);
		signedPreKeyRequest.setPublicKey("BBSm1xQ4kLzZrPp8Vw9xY2R1c0ZfQw==");
		signedPreKeyRequest.setSignature("MEUCIQD3yZy9yK0Z7KkqXx9b8cYxZpQzX7f1e9xQwIDA==");
		
		KyberPreKeyRequest kyberPreKeyRequest = new KyberPreKeyRequest();
		kyberPreKeyRequest.setKyberPreKeyId(1);
		kyberPreKeyRequest.setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==");
		kyberPreKeyRequest.setSignature("MEUCIFakeSignatureBase64Example==");
		
		OneTimePreKeyRequest oneTimePreKeyRequest = new OneTimePreKeyRequest();
		oneTimePreKeyRequest.setPreKeyId(1L);
		oneTimePreKeyRequest.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=");
		
		req.setSignedPreKey(signedPreKeyRequest);
		req.setKyberPreKey(kyberPreKeyRequest);
		req.setOneTimePreKeys(List.of(oneTimePreKeyRequest));
		
		DevicePreKeyBundleResponse response = new DevicePreKeyBundleResponse();

		when(service.createDevicePreKeyBundle(eq(USER_KEY), eq(1), any()))
		.thenReturn(response);

		mockMvc.perform(post("/signal/v2/devices/1/keys")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.SUCCESS.name()));
	}

	@Test
	void createDevicePreKeyBundle_deviceNotFound() throws Exception {
		when(service.createDevicePreKeyBundle(eq(USER_KEY), eq(1), any()))
		.thenThrow(new RecordNotFoundException("not found"));
		
		DevicePreKeyBundleRequest req = new DevicePreKeyBundleRequest();
		SignedPreKeyRequest signedPreKeyRequest = new SignedPreKeyRequest();
		signedPreKeyRequest.setSignedPreKeyId(1);
		signedPreKeyRequest.setPublicKey("BBSm1xQ4kLzZrPp8Vw9xY2R1c0ZfQw==");
		signedPreKeyRequest.setSignature("MEUCIQD3yZy9yK0Z7KkqXx9b8cYxZpQzX7f1e9xQwIDA==");
		
		KyberPreKeyRequest kyberPreKeyRequest = new KyberPreKeyRequest();
		kyberPreKeyRequest.setKyberPreKeyId(1);
		kyberPreKeyRequest.setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==");
		kyberPreKeyRequest.setSignature("MEUCIFakeSignatureBase64Example==");
		
		OneTimePreKeyRequest oneTimePreKeyRequest = new OneTimePreKeyRequest();
		oneTimePreKeyRequest.setPreKeyId(1L);
		oneTimePreKeyRequest.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=");
		
		req.setSignedPreKey(signedPreKeyRequest);
		req.setKyberPreKey(kyberPreKeyRequest);
		req.setOneTimePreKeys(List.of(oneTimePreKeyRequest));

		mockMvc.perform(post("/signal/v2/devices/1/keys")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
	}

	@Test
	void createDevicePreKeyBundle_oneTimeKeyExists() throws Exception {
		when(service.createDevicePreKeyBundle(eq(USER_KEY), eq(1), any()))
		.thenThrow(new OneTimePreKeyExistsException("exists"));
		
		DevicePreKeyBundleRequest req = new DevicePreKeyBundleRequest();
		SignedPreKeyRequest signedPreKeyRequest = new SignedPreKeyRequest();
		signedPreKeyRequest.setSignedPreKeyId(1);
		signedPreKeyRequest.setPublicKey("BBSm1xQ4kLzZrPp8Vw9xY2R1c0ZfQw==");
		signedPreKeyRequest.setSignature("MEUCIQD3yZy9yK0Z7KkqXx9b8cYxZpQzX7f1e9xQwIDA==");
		
		KyberPreKeyRequest kyberPreKeyRequest = new KyberPreKeyRequest();
		kyberPreKeyRequest.setKyberPreKeyId(1);
		kyberPreKeyRequest.setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==");
		kyberPreKeyRequest.setSignature("MEUCIFakeSignatureBase64Example==");
		
		OneTimePreKeyRequest oneTimePreKeyRequest = new OneTimePreKeyRequest();
		oneTimePreKeyRequest.setPreKeyId(1L);
		oneTimePreKeyRequest.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=");
		
		req.setSignedPreKey(signedPreKeyRequest);
		req.setKyberPreKey(kyberPreKeyRequest);
		req.setOneTimePreKeys(List.of(oneTimePreKeyRequest));
		
		mockMvc.perform(post("/signal/v2/devices/1/keys")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
		.andExpect(status().isConflict())
		.andExpect(jsonPath("$.code")
				.value(ResponseCode.ONE_TIME_PRE_KEY_EXISTS.name()));
	}
}
