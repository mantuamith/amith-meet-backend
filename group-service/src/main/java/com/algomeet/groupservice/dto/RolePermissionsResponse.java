package com.algomeet.groupservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Resolved permission set for a group role.")
public class RolePermissionsResponse {

    private boolean editGroupSettings;
    private boolean sendNewMessages;
    private boolean addOtherMembers;
    private boolean sendMessageHistory;
    private boolean inviteViaLinkOrQrCode;
    private boolean approveNewMembers;
    private boolean editGroupAdmins;
    private boolean removeMembers;
    private boolean disableChatForMembers;
    private boolean deleteGroup;
}
