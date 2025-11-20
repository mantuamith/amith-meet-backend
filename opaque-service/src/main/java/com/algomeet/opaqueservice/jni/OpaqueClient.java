package com.algomeet.opaqueservice.jni;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretResponse;
import com.algomeet.opaqueservice.dto.UserCredentialRequest;
import com.algomeet.opaqueservice.dto.UserCredentialResponse;
import com.algomeet.opaqueservice.dto.UserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.UserMasterSecretResponse;
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
    		CredentialType type,
            String clientRegMsgBase64,
            String bearerToken) {

        RegistrationRequest req = new RegistrationRequest();
        req.setType(type);
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
    public UserMasterSecretResponse saveSecret(
            CredentialType type,
            String rec,
            String masterSecretKey,
            String algorithm,
            String version,
            String salt,
            String bearerToken) {

        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(type);
        req.setRecord(rec);
        req.setMasterSecretKey(masterSecretKey);
        req.setAlgorithm(algorithm);
        req.setVersion(version);
        req.setSalt(salt);

        HttpEntity<UserMasterSecretRequest> entity = new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<UserMasterSecretResponse>> resp =
                rest.exchange(
                        baseUrl + "/user/master-secret/store",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<UserMasterSecretResponse>>() {}
                );

        return resp.getBody().getData();
    }
    
    /**
     * Call /user/master-secret/credential/exchange endpoint
     */
    public UserCredentialResponse exchangeMasterSecretCredential(
            CredentialType type,
            String clientPublicKeyBase64,
            String bearerToken) {

        UserCredentialRequest req = new UserCredentialRequest();
        req.setType(type);
        req.setClientPublicKey(clientPublicKeyBase64);

        HttpEntity<UserCredentialRequest> entity = new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<UserCredentialResponse>> resp =
                rest.exchange(
                        baseUrl + "/user/master-secret/credential/exchange",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<UserCredentialResponse>>() {}
                );

        return resp.getBody().getData();
    }
    
    /**
     * Retrieve user E2EE secret (OPAQUE-based retrieval).
     */
    public RetrieveUserMasterSecretResponse retrieveMasterSecret(
            CredentialType type,
            String clientAuthBase64,
            String bearerToken) {

        RetrieveUserMasterSecretRequest req = new RetrieveUserMasterSecretRequest();
        req.setType(type);
        req.setClientAuth(clientAuthBase64);

        HttpEntity<RetrieveUserMasterSecretRequest> entity =
                new HttpEntity<>(req, getHeaders(bearerToken));

        ResponseEntity<CommonResponse<RetrieveUserMasterSecretResponse>> resp =
                rest.exchange(
                        baseUrl + "/user/master-secret/retrieve",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<CommonResponse<RetrieveUserMasterSecretResponse>>() {}
                );

        return resp.getBody().getData();
    }
}