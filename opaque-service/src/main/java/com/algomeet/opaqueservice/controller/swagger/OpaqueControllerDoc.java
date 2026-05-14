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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Swagger/OpenAPI documentation for OPAQUE authentication and
 * encrypted master secret management APIs.
 *
 * <p>
 * This API implements the OPAQUE (Oblivious Password Authentication and Key Exchange)
 * protocol flow for securely protecting user master secrets without exposing
 * raw passwords to the server.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 *     <li>OPAQUE registration</li>
 *     <li>Encrypted master secret storage</li>
 *     <li>Credential exchange</li>
 *     <li>Secure master secret retrieval</li>
 *     <li>Authentication attempt limiting</li>
 * </ul>
 */
@Tag(
        name = "OPAQUE Authentication",
        description = """
                OPAQUE authentication and encrypted master secret management APIs.
                
                This controller provides:
                - OPAQUE registration
                - OPAQUE credential exchange
                - encrypted master secret storage
                - encrypted master secret retrieval
                
                All endpoints require authenticated user access.
                """
)
@SecurityRequirement(name = "bearerAuth")
public interface OpaqueControllerDoc {

    @Operation(
            summary = "OPAQUE Registration",
            description = """
                    Performs OPAQUE registration step 1.
                    
                    The client sends a locally-generated OPAQUE registration message
                    derived from a password, PIN, or device secret.
                    
                    The server responds with:
                    - server registration public response
                    - server identifier
                    
                    The server temporarily stores the generated OPAQUE SEC
                    in Redis for later record generation.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Registration response successfully generated",
                    content = @Content(
                            schema = @Schema(implementation = RegistrationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "Success",
                                      "data": {
                                        "serverPublicRegistrationResponse": "BASE64_SERVER_PUBLIC_RESPONSE",
                                        "serverId": "opaque.algomeet.app"
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<CommonResponse<RegistrationResponse>> register(
            @Parameter(description = "OPAQUE registration request", required = true)
            @RequestBody RegistrationRequest req
    );

    @Operation(
            summary = "Store Master Secret",
            description = """
                    Stores a user's encrypted master secret and OPAQUE record.
                    
                    The client sends:
                    - encrypted OPAQUE record (REC)
                    - encrypted master secret
                    - metadata
                    
                    The server reconstructs the final OPAQUE record using
                    the temporarily stored registration SEC.
                    
                    Used for:
                    - E2EE backup encryption
                    - Signal session encryption
                    - sender key protection
                    - encrypted metadata storage
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Master secret successfully stored",
                    content = @Content(
                            schema = @Schema(implementation = UserMasterSecretResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "409", description = "Master secret already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<CommonResponse<UserMasterSecretResponse>> saveMasterSecret(
            @Parameter(description = "Encrypted master secret request", required = true)
            @RequestBody UserMasterSecretRequest req
    );

    @Operation(
            summary = "Update Master Secret",
            description = """
                    Updates an existing encrypted master secret and OPAQUE record.
                    
                    This endpoint replaces:
                    - encrypted master secret
                    - OPAQUE REC
                    - encryption metadata
                    
                    Typically used during:
                    - password/PIN changes
                    - encryption rotation
                    - master key upgrades
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Master secret successfully updated",
                    content = @Content(
                            schema = @Schema(implementation = UserMasterSecretResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<CommonResponse<UserMasterSecretResponse>> updateMasterSecret(
            @Parameter(description = "Updated master secret request", required = true)
            @RequestBody UserMasterSecretRequest req
    );

    @Operation(
            summary = "OPAQUE Credential Exchange",
            description = """
                    Performs the OPAQUE credential exchange step.
                    
                    The client sends:
                    - ephemeral public credential key
                    
                    The server responds with:
                    - server credential public response
                    - server identifier
                    
                    The generated server SEC is temporarily stored in Redis
                    for later authentication verification.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Credential response successfully generated",
                    content = @Content(
                            schema = @Schema(implementation = UserCredentialResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Master secret not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<CommonResponse<UserCredentialResponse>> exchangeMasterSecCredential(
            @Parameter(description = "OPAQUE credential request", required = true)
            @RequestBody UserCredentialRequest req
    );

    @Operation(
            summary = "Retrieve Master Secret",
            description = """
                    Authenticates the user using OPAQUE and retrieves
                    the encrypted master secret.
                    
                    The client sends:
                    - final OPAQUE ClientAuth message
                    
                    If authentication succeeds:
                    - encrypted master secret is returned
                    - metadata is returned
                    
                    Brute-force protection:
                    - failed attempts are tracked
                    - temporary lockout is enforced after exceeding limits
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Master secret successfully retrieved",
                    content = @Content(
                            schema = @Schema(implementation = RetrieveUserMasterSecretResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "403", description = "Forbidden or temporarily locked"),
            @ApiResponse(responseCode = "404", description = "Master secret not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<CommonResponse<RetrieveUserMasterSecretResponse>> retrieveSecret(
            @Parameter(description = "OPAQUE client authentication request", required = true)
            @RequestBody RetrieveUserMasterSecretRequest req
    );
}