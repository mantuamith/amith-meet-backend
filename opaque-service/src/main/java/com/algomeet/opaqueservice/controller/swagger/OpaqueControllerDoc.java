package com.algomeet.opaqueservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretResponse;
import com.algomeet.opaqueservice.dto.UserCredentialRequest;
import com.algomeet.opaqueservice.dto.UserCredentialResponse;
import com.algomeet.opaqueservice.dto.UserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.UserMasterSecretResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OPAQUE Authentication", 
description = "Endpoints implementing OPAQUE secure registration and authentication protocol for storing and retrieving user master secrets.")
public interface OpaqueControllerDoc {
	// -------------------------------------------------------------------
	// Registration Phase
	// -------------------------------------------------------------------

	@Operation(
		summary = "Register user master secret record",
		description = """
			Client sends OPAQUE registration message (derived from PIN or device secret).
			Server returns:
			- `pub`: Server's registration response to client
			- `serverId`: Server identifier
			
			Server also stores the temporary session secret (server side registration secret) in Redis.
		""",
		responses = {
			@ApiResponse(responseCode = "200", description = "Registration response created successfully",
				content = @Content(schema = @Schema(implementation = RegistrationResponse.class)))
		}
	)
	public ResponseEntity<CommonResponse<RegistrationResponse>> register(
			@RequestBody RegistrationRequest req);

	// -------------------------------------------------------------------
	// Master Secret Store
	// -------------------------------------------------------------------

	@Operation(
		summary = "Store master secret",
		description = """
			Stores the user's encrypted master secret record after successful OPAQUE registration flow.
			
			- Validates that a master secret for the same `type` does not already exist.
			- Uses OPAQUE `storeRec` to finalize the server-side record.
		""",
		responses = {
			@ApiResponse(responseCode = "200", description = "Master secret stored",
				content = @Content(schema = @Schema(implementation = UserMasterSecretResponse.class))),
			@ApiResponse(responseCode = "409", description = "Master secret already exists for this type")
		}
	)
	public ResponseEntity<CommonResponse<UserMasterSecretResponse>> saveMasterSecret(
			@RequestBody UserMasterSecretRequest req);

	@Operation(
		summary = "Update master secret",
		description = """
			Updates an existing master secret for the given type.
			This performs the OPAQUE record storage using the updated record from client.
		"""
	)
	public ResponseEntity<CommonResponse<UserMasterSecretResponse>> updateMasterSecret(
			@RequestBody UserMasterSecretRequest req);

	// -------------------------------------------------------------------
	// Credential Exchange
	// -------------------------------------------------------------------

	@Operation(
		summary = "Exchange OPAQUE credential response",
		description = """
			Part of the OPAQUE authentication flow.

			Client sends:
			- `clientPublicKey` (base64)
			- master secret `type`

		Server returns:
			- `pub`: server credential response (public)
			- `serverId`

		Also stores temporary OPAQUE server authentication secret in Redis.
		"""
	)
	public ResponseEntity<CommonResponse<UserCredentialResponse>> credentialResponse(
			@RequestBody UserCredentialRequest req);

	// -------------------------------------------------------------------
	// Retrieve Master Secret
	// -------------------------------------------------------------------

	@Operation(
		summary = "Retrieve master secret",
		description = """
			Final step in OPAQUE authentication.

			Client sends:
			- `clientAuth` (base64)
			- `type`

		Server:
			- Validates OPAQUE user auth using temporary server secret stored in Redis.
			- Returns decrypted master secret metadata (still encrypted on server).
		""",
		responses = {
			@ApiResponse(responseCode = "200", description = "Master secret retrieved",
				content = @Content(schema = @Schema(implementation = RetrieveUserMasterSecretResponse.class))),
			@ApiResponse(responseCode = "404", description = "Master secret not found"),
			@ApiResponse(responseCode = "403", description = "Forbidden – failed OPAQUE authentication")
		}
	)
	public ResponseEntity<CommonResponse<RetrieveUserMasterSecretResponse>> retrieveSecret(
			@RequestBody RetrieveUserMasterSecretRequest req);
}