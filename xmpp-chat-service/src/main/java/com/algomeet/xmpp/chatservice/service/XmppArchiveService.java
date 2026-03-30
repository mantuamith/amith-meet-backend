package com.algomeet.xmpp.chatservice.service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class XmppArchiveService {
    private final MucMessageRepository repository;

    public Mono<MucMessage> archiveEvent(String xml, StanzaInfo info, String roomId, String from, String internalId) {
        MucMessage event = MucMessage.builder()
        		.id(internalId)
                .stanzaId(info.getStanzaId()) // Original client ID
                .roomId(roomId)
                .from(from)
                .stanzaXml(xml)
                .category(info.getCategory())
                .refersTo(info.getTargetId()) // This is the 'refersTo' ID
                .isE2EE(info.isE2EE())
                .build();

        return repository.save(event);
    }
    
    public void fetchMUCArchive(ChannelHandlerContext ctx, String roomId, String xml, XmppPrincipal principal) {
        String afterId = XmppStanzaUtil.getFieldValue(xml, "after-id");
        int maxResults = XmppStanzaUtil.getRsmMax(xml, 50);
        String queryId = XmppStanzaUtil.getAttribute(xml, "id");

        log.debug("MAM Request for Room {}: afterId={}, max={}", roomId, afterId, maxResults);

        repository.findByRoomIdAndIdGreaterThanOrderByIdAsc(
                roomId, 
                afterId != null ? afterId : "", // Ensure not null for Mongo query
                PageRequest.of(0, maxResults)
        )
        .concatMap((MucMessage msg) -> { // 1. Explicitly type the input parameter
            String mamResult = String.format(
                    "<message to='%s'>" +
                      "<result xmlns='urn:xmpp:mam:2' %s id='%s'>" +
                        "<forwarded xmlns='urn:xmpp:forward:0'>" +
                          "%s" +
                        "</forwarded>" +
                      "</result>" +
                    "</message>",
                    principal.getBareJid(),
                    (queryId != null ? "queryid='" + queryId + "'" : ""),
                    msg.getId(),
                    msg.getStanzaXml()
                );

            return Mono.<Void>create(sink -> {
                ctx.writeAndFlush(new TextWebSocketFrame(mamResult)).addListener(future -> {
                    if (future.isSuccess()) {
                        sink.success();
                    } else {
                        sink.error(future.cause());
                    }
                });
            });
            })
        .doOnComplete(() -> {
            String fin = String.format(
                "<iq type='result' to='%s' %s>" +
                  "<fin xmlns='urn:xmpp:mam:2' complete='true'>" +
                    "<set xmlns='http://jabber.org/protocol/rsm'/>" +
                  "</fin>" +
                "</iq>",
                principal.getBareJid(),
                (queryId != null ? "id='" + queryId + "'" : "")
            );
            ctx.writeAndFlush(new TextWebSocketFrame(fin));
        })
        .subscribe();
    }
}