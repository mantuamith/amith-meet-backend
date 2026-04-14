package com.algomeet.groupservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Embeddable
public class RolePermissions {

    @Column(name = "edit_group_settings", nullable = false)
    private boolean editGroupSettings;

    @Column(name = "send_new_messages", nullable = false)
    private boolean sendNewMessages;

    @Column(name = "add_other_members", nullable = false)
    private boolean addOtherMembers;

    @Column(name = "send_message_history", nullable = false)
    private boolean sendMessageHistory;

    @Column(name = "invite_via_link_or_qr_code", nullable = false)
    private boolean inviteViaLinkOrQrCode;

    @Column(name = "approve_new_members", nullable = false)
    private boolean approveNewMembers;

    @Column(name = "edit_group_admins", nullable = false)
    private boolean editGroupAdmins;

    @Column(name = "remove_members", nullable = false)
    private boolean removeMembers;

    @Column(name = "disable_chat_for_members", nullable = false)
    private boolean disableChatForMembers;

    @Column(name = "delete_group", nullable = false)
    private boolean deleteGroup;

    public RolePermissions(
            boolean editGroupSettings,
            boolean sendNewMessages,
            boolean addOtherMembers,
            boolean sendMessageHistory,
            boolean inviteViaLinkOrQrCode,
            boolean approveNewMembers,
            boolean editGroupAdmins,
            boolean removeMembers,
            boolean disableChatForMembers,
            boolean deleteGroup) {
        this.editGroupSettings = editGroupSettings;
        this.sendNewMessages = sendNewMessages;
        this.addOtherMembers = addOtherMembers;
        this.sendMessageHistory = sendMessageHistory;
        this.inviteViaLinkOrQrCode = inviteViaLinkOrQrCode;
        this.approveNewMembers = approveNewMembers;
        this.editGroupAdmins = editGroupAdmins;
        this.removeMembers = removeMembers;
        this.disableChatForMembers = disableChatForMembers;
        this.deleteGroup = deleteGroup;
    }

    public static RolePermissions ownerDefaults() {
        return new RolePermissions(true, true, true, true, true, true, true, true, true, true);
    }

    public static RolePermissions adminDefaults() {
        return new RolePermissions(true, true, true, true, true, true, true, true, true, false);
    }

    public static RolePermissions memberDefaults() {
        return new RolePermissions(false, false, false, false, false, false, false, false, false, false);
    }
}
