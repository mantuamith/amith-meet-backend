package com.algomeet.signalingservice.controller.swagger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeysResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.dto.UserOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.UserOneTimeKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "User Keys", description = "APIs for managing identity and one-time keys in the signaling service.")
public interface UserKeyControllerDoc {

	@Operation(
		summary = "Register user identity key",
		description = "Registers a new identity key for the authenticated user.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Identity key registered successfully",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))),
			@ApiResponse(responseCode = "400", description = "Identity key already exists")
		}
	)
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> registerUserIdentity(
			@Valid @RequestBody UserIdentityKeyRequest request);

	@Operation(
		summary = "Get all user identity keys",
		description = "Retrieves all identity keys for the given user or the authenticated user if no key is provided.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved user identity keys",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))),
			@ApiResponse(responseCode = "404", description = "User not found")
		}
	)
	public ResponseEntity<CommonResponse<List<UserIdentityKeyResponse>>> getUserIdentityKeys(
			@Parameter(description = "Optional user key. If omitted, retrieves for the current authenticated user.")
			@RequestParam("userKey") Optional<UUID> userKeyOpt);

	@Operation(
		summary = "Delete user identity key",
		description = "Deletes the specified identity key for the current user.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Identity key deleted successfully"),
			@ApiResponse(responseCode = "404", description = "Identity key not found")
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteIndentity(
			@Parameter(description = "Identity key to delete") @PathVariable String identityKey);

	@Operation(
		summary = "Get user identity key with one-time keys",
		description = "Fetches a user's identity key along with its associated one-time keys.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved identity and one-time keys"),
			@ApiResponse(responseCode = "404", description = "User not found"),
			@ApiResponse(responseCode = "503", description = "No one-time key available")
		}
	)
	public ResponseEntity<CommonResponse<UserIdentityAndOneTimeKeysResponse>> getUserIdentityAndOneTimeKeys(
			@Parameter(description = "User key UUID") @RequestParam UUID userKey);

	@Operation(
		summary = "Add one-time keys for an identity key",
		description = "Adds one or more one-time keys for the given identity key.",
		responses = {
			@ApiResponse(responseCode = "200", description = "One-time keys added successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid or duplicate one-time keys"),
			@ApiResponse(responseCode = "404", description = "User or identity key not found")
		}
	)
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> addOneTimeKeys(
			@PathVariable String identityKey,
			@Valid @RequestBody UserOneTimeKeyRequest request);
	
	@Operation(
		summary = "Get all one-time keys for identity key if userKey parameter is not present, else return only one onetime key.",
		description = "Retrieves all one-time keys associated with a specific identity key.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved one-time keys"),
			@ApiResponse(responseCode = "404", description = "Identity key not found")
		}
	)
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> getOneTimeKeys(
			@PathVariable String identityKey, @RequestParam Optional<UUID> userKeyOpt);

	@Operation(
		summary = "Delete one-time key by ID",
		description = "Deletes a specific one-time key by its ID.",
		responses = {
			@ApiResponse(responseCode = "200", description = "One-time key deleted successfully"),
			@ApiResponse(responseCode = "404", description = "One-time key not found")
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKey(
			@Parameter(description = "One-time key database ID") @PathVariable Long id);

	@Operation(
		summary = "Delete multiple one-time keys",
		description = "Deletes multiple one-time keys by their IDs.",
		responses = {
			@ApiResponse(responseCode = "200", description = "One-time keys deleted successfully"),
			@ApiResponse(responseCode = "404", description = "One or more keys not found")
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKeys(
			@Parameter(description = "List of one-time key IDs") @RequestParam List<Long> ids);

	@Operation(
		summary = "Delete all keys for the user",
		description = "Deletes all identity and one-time keys associated with the current user.",
		responses = {
			@ApiResponse(responseCode = "200", description = "All keys deleted successfully")
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteAllUserKeys();
}