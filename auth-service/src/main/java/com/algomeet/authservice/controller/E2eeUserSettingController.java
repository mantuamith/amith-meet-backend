package com.algomeet.authservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.controller.swagger.E2eeUserSettingControllerDoc;
import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.E2eeUserSettingRequest;
import com.algomeet.authservice.dto.E2eeUserSettingResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.E2eeUserSettingService;
import com.algomeet.authservice.util.SecurityUtil;

/**
 * REST controller for managing End-to-End Encryption (E2EE) user settings.
 * <p>
 * This controller provides APIs for authenticated users to:
 * <ul>
 *   <li>Retrieve their current E2EE configuration</li>
 *   <li>Create or update their encryption settings (PIN, auto-sync key, etc.)</li>
 *   <li>Delete their E2EE configuration</li>
 * </ul>
 *
 * <p>All operations use the authenticated user's UUID, which is resolved from the
 * security context using {@link SecurityUtil#getUserKey()}.</p>
 *
 * <p>These endpoints act as a proxy layer between the Auth Service and the
 * User Service through a Feign client.</p>
 *
 * @author 
 * @since 1.0
 */
@RestController
@RequestMapping("/auth/e2ee-user-settings")
public class E2eeUserSettingController implements E2eeUserSettingControllerDoc{

    private final E2eeUserSettingService service;

    /**
     * Constructs a new {@code E2eeUserSettingController} with the provided service.
     *
     * @param service the {@link E2eeUserSettingService} used to manage E2EE user settings
     */
    public E2eeUserSettingController(E2eeUserSettingService service) {
        this.service = service;
    }

    /**
     * Retrieves the current authenticated user's End-to-End Encryption (E2EE) settings.
     * <p>
     * This endpoint allows users to view their E2EE configuration, including
     * their auto-sync status and associated keys.
     *
     * @return a {@link ResponseEntity} containing a {@link CommonResponse}
     *         wrapping the {@link E2eeUserSettingResponse} with {@link ResponseCode#SUCCESS}
     */
    @GetMapping
    public ResponseEntity<CommonResponse<E2eeUserSettingResponse>> getById() {
        E2eeUserSettingResponse response = service.getUserSettingById(SecurityUtil.getUserKey());
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    }

    /**
     * Creates or updates the authenticated user's End-to-End Encryption (E2EE) settings.
     * <p>
     * This endpoint allows the user to modify their encryption-related configuration,
     * such as the auto-sync key or enabling/disabling sync. If the record does not exist,
     * it will be created automatically.
     *
     * @param request the {@link E2eeUserSettingRequest} containing new or updated values
     * @return a {@link ResponseEntity} containing a {@link CommonResponse}
     *         wrapping the updated {@link E2eeUserSettingResponse}
     */
    @PostMapping
    public ResponseEntity<CommonResponse<E2eeUserSettingResponse>> createOrUpdate(
            @RequestBody E2eeUserSettingRequest request) {
        E2eeUserSettingResponse updated = service.createOrUpdateUserSetting(SecurityUtil.getUserKey(), request);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, updated));
    }

    /**
     * Deletes the current authenticated user's End-to-End Encryption (E2EE) settings.
     * <p>
     * This endpoint removes the user's encryption configuration from the system.
     * Once deleted, any previous E2EE configuration must be recreated manually.
     *
     * @return a {@link ResponseEntity} indicating successful deletion,
     *         wrapped in a {@link CommonResponse} with {@link ResponseCode#SUCCESS}
     */
    @DeleteMapping
    public ResponseEntity<?> delete() {
        service.deleteUserSetting(SecurityUtil.getUserKey());
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
}