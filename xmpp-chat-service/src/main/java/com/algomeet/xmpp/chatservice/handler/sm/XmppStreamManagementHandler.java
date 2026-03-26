//package com.algomeet.xmpp.chatservice.handler.sm;
//
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.Deque;
//import java.util.concurrent.ConcurrentLinkedDeque;
//
//public class XmppStreamManagementHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
//
//    // Counters for XEP-0198
//    private final AtomicLong handledCount = new AtomicLong(0); // h (incoming)
//    private final AtomicLong serverSentCount = new AtomicLong(0); // h (outgoing)
//    
//    // Buffer for messages sent but not yet acknowledged by client
//    private final Deque<String> unackedBuffer = new ConcurrentLinkedDeque<>();
//    private final int MAX_BUFFER_SIZE = 500;
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
//        String xml = frame.text().trim();
//
//        // 1. If it's a standard Stanza, increment our handled counter
//        if (isStanza(xml)) {
//            handledCount.incrementAndGet();
//        }
//
//        // 2. Handle Acknowledgment Request from Client (<r />)
//        if (xml.contains("<r ") || xml.equals("<r/>") || xml.equals("<r />")) {
//            sendAck(ctx);
//        }
//
//        // 3. Handle Acknowledgment from Client (<a h='...' />)
//        if (xml.startsWith("<a ") && xml.contains("h=")) {
//            long clientHandled = parseHAttribute(xml);
//            processClientAck(clientHandled);
//        }
//        
//        // Pass to next handler for business logic (processing the actual message)
//        ctx.fireChannelRead(frame.retain());
//    }
//
//    /**
//     * Sends the current handled count back to the client.
//     */
//    private void sendAck(ChannelHandlerContext ctx) {
//        String ackXml = String.format("<a xmlns='urn:xmpp:sm:3' h='%d'/>", handledCount.get());
//        ctx.writeAndFlush(new TextWebSocketFrame(ackXml));
//    }
//
//    /**
//     * Clears the buffer based on what the client has confirmed receiving.
//     */
//    private void processClientAck(long clientHandled) {
//        // If client says they've handled 50, we remove up to 50 from our buffer
//        // Note: This logic assumes your buffer tracking is synced with serverSentCount
//        while (serverSentCount.get() > clientHandled && !unackedBuffer.isEmpty()) {
//            unackedBuffer.pollFirst(); 
//            // In a real app, you'd track the specific ID of the message 
//            // to ensure you aren't over-polling.
//        }
//    }
//
//    /**
//     * Helper to identify XMPP Stanzas (<message>, <iq>, <presence>)
//     */
//    private boolean isStanza(String xml) {
//        return xml.startsWith("<message") || 
//               xml.startsWith("<iq") || 
//               xml.startsWith("<presence");
//    }
//
//    /**
//     * Simple regex or string manipulation to extract 'h' value
//     */
//    private long parseHAttribute(String xml) {
//        try {
//            String hValue = xml.split("h='")[1].split("'")[0];
//            return Long.parseLong(hValue);
//        } catch (Exception e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        cause.printStackTrace();
//        ctx.close();
//    }
//}