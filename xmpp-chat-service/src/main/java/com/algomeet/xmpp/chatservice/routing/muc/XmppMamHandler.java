package com.algomeet.xmpp.chatservice.routing.muc;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmppMamHandler {
	private final XmppArchiveService xmppArchiveService;    
	private final DomainProperties domainProperties;
     
    public void handleMamRequest(ChannelHandlerContext ctx, String to, String xml) {
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
        
        // Determine if this is a MUC MAM or Personal MAM
        if (StringUtils.hasText(to) && to.contains(domainProperties.getGroupChatDomain())) {
            String roomId = XmppUtil.getRoomId(to);
            // Call service to fetch history from the room's archive
            xmppArchiveService.fetchMUCArchive(ctx, roomId, xml, principal);
        } else {
            // Call service to fetch history from the user's personal archive
            // If this server support direct chat MAM
        	// TODO: Retrieve direct chat MAM
        }
    }
}