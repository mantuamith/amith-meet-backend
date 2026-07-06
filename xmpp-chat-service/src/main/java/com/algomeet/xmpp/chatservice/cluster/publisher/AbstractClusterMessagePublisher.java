package com.algomeet.xmpp.chatservice.cluster.publisher;

import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.util.ClusterSyncProtocolUtil;

public class AbstractClusterMessagePublisher {
	/**
	 * Thread-local reusable buffer to avoid allocating StringBuilder per message.
	 *
	 * <p>This is safe because each thread (Netty worker / request thread)
	 * has its own isolated buffer instance.</p>
	 */
	private static final ThreadLocal<StringBuilder> BUFFER =
			ThreadLocal.withInitial(() -> new StringBuilder(512));

	protected String buildClusterMessage(
			String version,
			String id,
			String to,
			String from,
			ChatType chatType,
			Boolean isAllowEcho,
			String sessionId,
			Boolean shouldCarbon,
			Boolean isAckStanza,
			String payload) {

		StringBuilder sb = BUFFER.get();
		sb.setLength(0);

		char sep = ClusterSyncProtocolUtil.SEP;

		sb.append(version).append(sep)
		.append(id).append(sep)
		.append(to).append(sep)
		.append(from).append(sep)
		.append(chatType.name()).append(sep)
		.append(isAllowEcho ? "1" : "0").append(sep)
		.append(sessionId == null ? "" : sessionId).append(sep)
		.append(shouldCarbon ? "1" : "0").append(sep)
		.append(isAckStanza ? "1" : "0").append(sep)
		.append(payload);

		return sb.toString();
	}
}
