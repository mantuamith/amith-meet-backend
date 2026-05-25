package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.SmBufferMessage;
import com.algomeet.xmpp.chatservice.repository.SmBufferMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing the lifecycle of XEP-0198 buffered stanzas.
 * * <p>Handles the temporary persistence of outbound stanzas to support 
 * stream resumption. Stanzas are stored until acknowledged by the client 
 * or until the session TTL expires.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmBufferMessageService {

	private final SmBufferMessageRepository repository;

	/**
	 * Buffers an outbound stanza for a specific Stream Management session.
	 * * @param smId      The Stream Management ID (previd).
	 * @param stanzaId  The unique ID of the stanza.
	 * @param stanzaXml The raw XML content to be buffered.
	 * @return A Mono containing the saved SmBufferMessage.
	 */
	public Mono<SmBufferMessage> bufferStanza(UUID smSessionId, UUID stanzaId, UUID seq, String stanzaXml) {    	
		SmBufferMessage message = SmBufferMessage.builder()
				.id(stanzaId)
				.smSid(smSessionId)
				.stanzaXml(stanzaXml)
				.seq(seq)
				.build();

		return repository.save(message)
				.doOnSuccess(m -> log.trace("Buffered stanza [{}] for SM session [{}]", stanzaId, smSessionId))
				.doOnError(e -> log.error("Failed to buffer stanza [{}] for SM session [{}]", stanzaId, smSessionId, e));
	}

	/**
	 * Retrieves all unacknowledged stanzas to be replayed during stream resumption.
	 * * @param smId The Stream Management ID.
	 * @return A Flux of stanzas ordered by creation time.
	 */
	public Flux<SmBufferMessage> getStanzasForResumption(UUID smSid) {
		log.debug("Retrieving buffered stanzas for resumption of session [{}]", smSid);
		return repository.findBySmSidOrderBySeqAsc(smSid);
	}

	/**
	 * Clears the buffer for a specific session. 
	 * Typically called when a session is cleanly closed or successfully resumed 
	 * (depending on your replay-and-purge strategy).
	 * * @param smId The Stream Management ID.
	 * @return A Mono indicating completion.
	 */
	public Mono<Void> clearBuffer(UUID smSid) {
		return repository.deleteBySmSid(smSid)
				.doOnSuccess(v -> log.debug("Cleared SM buffer for session [{}]", smSid));
	}
}