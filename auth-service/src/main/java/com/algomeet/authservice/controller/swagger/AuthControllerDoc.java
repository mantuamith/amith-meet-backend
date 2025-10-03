package com.algomeet.authservice.controller.swagger;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.ForgotPasswordInitRequest;
import com.algomeet.authservice.dto.ForgotPasswordResetRequest;
import com.algomeet.authservice.dto.ForgotPasswordVerifyRequest;
import com.algomeet.authservice.dto.LoginInitRequest;
import com.algomeet.authservice.dto.LoginVerifyRequest;
import com.algomeet.authservice.dto.RefreshTokenRequest;
import com.algomeet.authservice.dto.RegisterInitRequest;
import com.algomeet.authservice.dto.RegisterInitResponse;
import com.algomeet.authservice.dto.RegisterVerifyRequest;
import com.algomeet.authservice.dto.RegisterVerifyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "Authentication", description = "Endpoints for authentication, registration, and password management")
public interface AuthControllerDoc {

    // ================== TOKEN REFRESH ==================
    @Operation(
        summary = "Refresh access token",
        description = "Refreshes an access token using a valid refresh token.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token"),
            @ApiResponse(responseCode = "500", description = "Server error")
        }
    )
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request);

    // ================== DELETE ACCOUNT =================
    @Operation(
        summary = "Delete account",
        description = "Deletes the currently authenticated user account.",
        responses = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Failed to delete user")
        }
    )
    public ResponseEntity<?> deleteAccount(
        @Parameter(description = "Authorization header with Bearer token") 
        @RequestHeader("Authorization") String authHeader);

    // ================== LOGOUT =========================
    @Operation(
        summary = "Logout",
        description = "Logs out the user, revokes refresh token, and rotates session if needed.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing Authorization header"),
            @ApiResponse(responseCode = "500", description = "Server error")
        }
    )
    public ResponseEntity<?> logout(
        @RequestBody(required = false) RefreshTokenRequest request,
        @Parameter(description = "Authorization header with Bearer token") 
        @RequestHeader("Authorization") String authHeader,
        @Parameter(description = "Optional device ID") 
        @RequestHeader(value = "X-Device-Id", required = false) String deviceId);

    // ================== LOGIN INIT =====================
    @Operation(
        summary = "Initialize login",
        description = "Starts the login process. Depending on policy, may return tokens directly or require OTP verification.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Login initialized",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Device lock active")
        }
    )
    public ResponseEntity<?> initLogin(@Valid @RequestBody LoginInitRequest request);

    // ================== LOGIN VERIFY ===================
    @Operation(
        summary = "Verify login",
        description = "Verifies OTP or TOTP (depending on login policy) and issues tokens.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Login verified, tokens issued",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Verification type mismatch"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired OTP")
        }
    )
    public ResponseEntity<AuthResponse> verifyLogin(@Valid @RequestBody LoginVerifyRequest request);

    // ================== REGISTRATION INIT ==============
    @Operation(
        summary = "Initialize registration",
        description = "Starts the user registration process, dispatching OTP or verification steps.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Registration init response",
                content = @Content(schema = @Schema(implementation = RegisterInitResponse.class)))
        }
    )
    public RegisterInitResponse init(@Valid @RequestBody RegisterInitRequest req, HttpServletRequest http);

    // ================== REGISTRATION VERIFY ============
    @Operation(
        summary = "Verify registration",
        description = "Verifies OTP for registration and finalizes account creation.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Registration verified",
                content = @Content(schema = @Schema(implementation = RegisterVerifyResponse.class)))
        }
    )
    public RegisterVerifyResponse verify(@Valid @RequestBody RegisterVerifyRequest req, HttpServletRequest http);

    // ================== PASSWORD FORGOT INIT ===========
    @Operation(
        summary = "Forgot password init",
        description = "Starts password reset flow by sending OTP to user's email or phone.",
        responses = {
            @ApiResponse(responseCode = "200", description = "OTP sent for password reset")
        }
    )
    public ResponseEntity<Map<String,Object>> forgotPasswordInit(@Valid @RequestBody ForgotPasswordInitRequest request);

    // ================== PASSWORD FORGOT VERIFY =========
    @Operation(
        summary = "Verify password reset OTP",
        description = "Verifies OTP sent during password reset and issues a reset ticket.",
        responses = {
            @ApiResponse(responseCode = "200", description = "OTP verified, ticket issued")
        }
    )
    public ResponseEntity<Map<String,Object>> forgotPasswordVerify(@Valid @RequestBody ForgotPasswordVerifyRequest request);

    // ================== PASSWORD RESET =================
    @Operation(
        summary = "Reset password",
        description = "Resets the user's password using a valid reset ticket.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Password updated successfully")
        }
    )
    public ResponseEntity<Map<String,Object>> forgotPasswordReset(@Valid @RequestBody ForgotPasswordResetRequest request);
}