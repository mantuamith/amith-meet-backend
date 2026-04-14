package com.algomeet.groupservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Partial permission update for a single role. Only provided fields are merged.")
public class RolePermissionsPatchRequest {

    @Schema(description = "Allows changing group info like name, description, profile photo, or rules.", example = "true")
    private Boolean editGroupSettings;

    @Schema(description = "Allows sending new messages in the group.", example = "false")
    private Boolean sendNewMessages;

    @Schema(description = "Allows adding other users directly to the group.", example = "true")
    private Boolean addOtherMembers;

    @Schema(description = "Allows sharing previous chat history when adding someone.", example = "true")
    private Boolean sendMessageHistory;

    @Schema(description = "Allows managing invite links or QR-code based joins.", example = "true")
    private Boolean inviteViaLinkOrQrCode;

    @Schema(description = "Controls whether new users must be approved before joining.", example = "true")
    private Boolean approveNewMembers;

    @Schema(description = "Allows promoting or demoting other admins.", example = "true")
    private Boolean editGroupAdmins;

    @Schema(description = "Allows removing members from the group.", example = "true")
    private Boolean removeMembers;

    @Schema(description = "Allows making the group read-only for members.", example = "false")
    private Boolean disableChatForMembers;

    @Schema(description = "Allows permanently deleting the group. Intended for OWNER only.", example = "false")
    private Boolean deleteGroup;
}
