package com.algomeet.xmpp.chatservice.stanza.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.constant.Constants;

@Component
public class MessageViewStaxParser {

    public static class ViewItem {
        public String action;
        public String roomId;
        public String id;
        public String peerKey;

        public ViewItem(String action, String roomId, String id, String peerKey) {
            this.action = action;
            this.roomId = roomId;
            this.id = id;
            this.peerKey = peerKey;
        }

        @Override
        public String toString() {
            return "ViewItem{" +
                    "action='" + action + '\'' +
                    ", roomId='" + roomId + '\'' +
                    ", id='" + id + '\'' +
                    ", peerKey='" + peerKey + '\'' +
                    '}';
        }
    }

    public static class ParsedIq {
        public String iqId;
        public String type;
        public List<ViewItem> items = new ArrayList<>();
    }

    public ParsedIq parse(String xml) throws Exception {

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

        ParsedIq result = new ParsedIq();

        boolean inQuery = false;

        while (reader.hasNext()) {
            int event = reader.next();

            switch (event) {

                case XMLStreamConstants.START_ELEMENT:

                    String localName = reader.getLocalName();

                    // <iq>
                    if ("iq".equals(localName)) {
                        result.iqId = reader.getAttributeValue(null, "id");
                        result.type = reader.getAttributeValue(null, "type");
                    }

                    // <query>
                    if ("query".equals(localName)) {
                        String ns = reader.getNamespaceURI();
                        if (Constants.NS_MESSAGE_VIEW.equals(ns)) {
                            inQuery = true;
                        }
                    }

                    // <item>
                    if (inQuery && "item".equals(localName)) {
                        String action = reader.getAttributeValue(null, "action");
                        String roomId = reader.getAttributeValue(null, "room-id");
                        String id = reader.getAttributeValue(null, "id");
                        String peerKey = reader.getAttributeValue(null, "peer-key");

                        result.items.add(new ViewItem(action, roomId, id, peerKey));
                    }

                    break;

                case XMLStreamConstants.END_ELEMENT:
                    if ("query".equals(reader.getLocalName())) {
                        inQuery = false;
                    }
                    break;
            }
        }

        reader.close();
        return result;
    }
}