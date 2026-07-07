package com.algomeet.xmpp.chatservice.stanza.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

@Component
public class ViewManageStaxParser {

    public static class ViewItem {
        public String action;
        public String room;
        public String id;
        public String peer;

        public ViewItem(String action, String room, String id, String peer) {
            this.action = action;
            this.room = room;
            this.id = id;
            this.peer = peer;
        }

        @Override
        public String toString() {
            return "ViewItem{" +
                    "action='" + action + '\'' +
                    ", room='" + room + '\'' +
                    ", id='" + id + '\'' +
                    ", peer='" + peer + '\'' +
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
                        if ("https://algomeet.app/protocol/view-management".equals(ns)) {
                            inQuery = true;
                        }
                    }

                    // <item>
                    if (inQuery && "item".equals(localName)) {
                        String action = reader.getAttributeValue(null, "action");
                        String room = reader.getAttributeValue(null, "room");
                        String id = reader.getAttributeValue(null, "id");
                        String peer = reader.getAttributeValue(null, "peer");

                        result.items.add(new ViewItem(action, room, id, peer));
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