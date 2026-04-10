package com.algomeet.xmpp.chatservice.util;

import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.MucRole;

/**
 * Utility class for managing and mapping Multi-User Chat (MUC) roles and affiliations.
 * <p>
 * This utility follows the XEP-0045 specification for mapping persistent affiliations 
 * (Owner, Admin, Member, Outcast) to temporary room roles (Moderator, Participant, Visitor).
 * </p>
 * * @author Algomeet Core Team
 * @version 1.0
 */
public class MucRoleUtil {

    /**
     * Determines the appropriate {@link MucRole} based on a user's {@link MucAffiliation}.
     * <p>
     * In the AlgoMeet XMPP architecture:
     * <ul>
     * <li>Owners and Admins are automatically granted the <b>Moderator</b> role.</li>
     * <li>All other affiliations (Members or None) default to the <b>Participant</b> role.</li>
     * </ul>
     * </p>
     *
     * @param affiliate The string representation of the user's affiliation (e.g., "owner", "admin", "member").
     * @return The resulting {@link MucRole} for the room session; defaults to {@code MucRole.PARTICIPANT}.
     */
    public static MucRole getMucRole(String affiliate) {
        if (MucAffiliation.OWNER == MucAffiliation.fromString(affiliate)
                || MucAffiliation.ADMIN == MucAffiliation.fromString(affiliate)) {
            return MucRole.MODERATOR;
        }
        
        return MucRole.PARTICIPANT; 
    }
}