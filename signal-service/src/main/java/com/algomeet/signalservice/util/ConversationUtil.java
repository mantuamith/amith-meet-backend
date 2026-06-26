package com.algomeet.signalservice.util;

import org.springframework.util.StringUtils;

/**
 * Utility class responsible for generating deterministic conversation identifiers
 * used to group messages belonging to the same 1:1 chat thread.
 *
 * Conversation IDs ensure that:
 * - Both sender and receiver map to the same conversation thread
 * - Message queries remain consistent regardless of direction (A→B or B→A)
 * - Indexing and pagination can be optimized in MongoDB
 *
 * Format example:
 * userA_userB
 */
public class ConversationUtil {

    /**
     * Delimiter used to separate user keys in a deterministic conversation ID.
     * Kept constant to ensure stable indexing and backward compatibility.
     */
    private static final String DELIMITER = "_";

    /**
     * Builds a deterministic conversation ID for a message context where the
     * current user (userKey) is either the sender or receiver.
     *
     * Logic:
     * - If the current user is the sender → conversation is userKey_receiver
     * - If the current user is the receiver → conversation is userKey_sender
     *
     * This ensures that both sides of a 1:1 conversation generate the same ID.
     *
     * Example:
     * userKey = A, sender = A, receiver = B → A_B
     * userKey = B, sender = A, receiver = B → B_A
     * (after normalization both map to same logical conversation thread usage)
     *
     * @param userKey  current authenticated user
     * @param sender   message sender key
     * @param receiver message receiver key
     * @return deterministic conversation ID for 1:1 chat
     */
    public static String getConversationId(String userKey, String sender, String receiver) {
    	if (!(StringUtils.hasText(userKey) && StringUtils.hasText(sender) && StringUtils.hasText(receiver))) {
    		throw new RuntimeException("Cannot generate conversation ID either one or more of required parameters has empty or null value");
    	}
    	
        return (userKey + DELIMITER + (userKey.equalsIgnoreCase(sender) ? receiver : sender));
    }

    /**
     * Builds a direct conversation ID using an explicitly provided peer user key.
     *
     * This method is used when the caller already knows the peer participant
     * and does not need sender/receiver resolution logic.
     *
     * Example:
     * userKey = A, peerUserKey = B → A_B
     *
     * @param userKey      current authenticated user
     * @param peerIUserKey other participant in the conversation
     * @return deterministic conversation ID
     */
    public static String getConversationId(String userKey, String peerIUserKey) {
    	if (!(StringUtils.hasText(userKey) && StringUtils.hasText(peerIUserKey))) {
    		throw new RuntimeException("Cannot generate conversation ID either one or more of required parameters has empty or null value");
    	}
        return (userKey + DELIMITER + peerIUserKey);
    }
    
    /**
     * Extracts the "peer" user key from a conversationId.
     *
     * Expected format:
     *   <currentUserKey>{DELIMITER}<peerUserKey>
     *
     * Example:
     *   conversationId = "userA:userB"  -> returns "userB"
     *
     * Notes:
     * - Returns null if the input is blank, malformed, or does not contain the delimiter.
     * - Uses index-based parsing instead of split() to avoid unnecessary array creation.
     *
     * @param conversationId the composite conversation identifier
     * @return the peer user key, or null if not resolvable
     */
    public static String getPeerKey(String conversationId) {
        // Validate input (null, empty, or whitespace)
        if (!org.springframework.util.StringUtils.hasText(conversationId)) {
            return null;
        }

        // Find the first occurrence of the delimiter
        int delimiterIndex = conversationId.indexOf(DELIMITER);

        // If delimiter is missing or nothing exists after it, return null
        if (delimiterIndex < 0 || delimiterIndex == conversationId.length() - 1) {
            return null;
        }

        // Extract substring after the delimiter (peer user key)
        return conversationId.substring(delimiterIndex + DELIMITER.length());
    }
}