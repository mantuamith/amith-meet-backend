package com.algomeet.opaqueservice.jni;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.FinalizeRegistrationRequest;
import com.algomeet.opaqueservice.dto.FinalizeRegistrationResponse;
import com.algomeet.opaqueservice.dto.LoginRequest;
import com.algomeet.opaqueservice.dto.LoginResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretRequest;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretResponse;
import com.algomeet.opaqueservice.dto.UserSecretRequest;
import com.algomeet.opaqueservice.dto.UserSecretResponse;
import com.algomeet.opaqueservice.enums.CredentialType;

public class OpaqueClient {

    private final RestTemplate rest;
    private final String baseUrl;

    public OpaqueClient(String baseUrl) {
        this.rest = new RestTemplate();
        this.baseUrl = baseUrl;
    }

    /**
     * Step 1: Send OPAQUE client registration message
     */
    public RegistrationResponse register(
            String clientRegMsgBase64,
            String bearerToken) {

        RegistrationRequest req = new RegistrationRequest();
        req.setClientRegistrationMessage(clientRegMsgBase64);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + bearerToken);

        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<CommonResponse<RegistrationResponse>> resp =
                rest.exchange(
                        baseUrl + "/register",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<RegistrationResponse>>() {}
                );

        return resp.getBody().getData();
    }

    
    private HttpHeaders getHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + bearerToken);
        return headers;
    }

    /**
     * Save user E2EE secret.
     */
    public UserSecretResponse saveSecret(
            CredentialType type,
            String rec,
            String secretKey,
            String bearerToken) {

        UserSecretRequest req = new UserSecretRequest();
        req.setType(type);
        req.setClientRecord(rec);
        req.setSecretKey(secretKey);

        HttpEntity<UserSecretRequest> entity = new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<UserSecretResponse>> resp =
                rest.exchange(
                        baseUrl + "/user/secret/store",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<UserSecretResponse>>() {}
                );

        return resp.getBody().getData();
    }
    
    /**
     * Call /login endpoint
     */
    public LoginResponse login(
            CredentialType type,
            String clientPublicKeyBase64,
            String bearerToken) {

        LoginRequest req = new LoginRequest();
        req.setType(type);
        req.setClientPublicKey(clientPublicKeyBase64);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<LoginResponse>> resp =
                rest.exchange(
                        baseUrl + "/login",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<LoginResponse>>() {}
                );

        return resp.getBody().getData();
    }
    
    /**
     * Retrieve user E2EE secret (OPAQUE-based retrieval).
     */
    public RetrieveUserSecretResponse retrieveSecret(
            CredentialType type,
            String clientAuthBase64,
            String serverSecretKeyBase64,
            String bearerToken) {

        RetrieveUserSecretRequest req = new RetrieveUserSecretRequest();
        req.setType(type);
        req.setClientAuth(clientAuthBase64);
        req.setServerSecKey(serverSecretKeyBase64);

        HttpEntity<RetrieveUserSecretRequest> entity =
                new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<RetrieveUserSecretResponse>> resp =
                rest.exchange(
                        baseUrl + "/user/secret/retrieve",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<RetrieveUserSecretResponse>>() {}
                );

        return resp.getBody().getData();
    }
}