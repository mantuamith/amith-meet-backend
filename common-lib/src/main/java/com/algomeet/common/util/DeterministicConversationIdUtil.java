package com.algomeet.common.util;

import java.util.UUID;

public class DeterministicConversationIdUtil {
	
	 /**
     * Generates the deterministic conversation ID using lexicographical ordering.
     * Format: lowerUserKey_higherUserKey
     */
    public static String getConversationId(UUID userKeyA, UUID userKeyB) {
        String strA = userKeyA.toString();
        String strB = userKeyB.toString();
        return strA.compareTo(strB) < 0 ? strA + "_" + strB : strB + "_" + strA;
    }
}
