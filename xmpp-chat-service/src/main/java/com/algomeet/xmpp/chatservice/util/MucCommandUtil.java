package com.algomeet.xmpp.chatservice.util;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MucCommandUtil {
	private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

	public static boolean isAuthorized(MucMember sender, MucMember victim) {
		// Permission Check (Pseudo-logic: check your Room Manager/DB)        
		if (!(MucAffiliation.ADMIN == MucAffiliation.fromString(sender.getRole())
				|| MucAffiliation.OWNER == MucAffiliation.fromString(sender.getRole()))) {

			return false;
		}

		// Victim is owner but sender is not owner
		if ((MucAffiliation.OWNER == MucAffiliation.fromString(victim.getRole())
				&& MucAffiliation.OWNER != MucAffiliation.fromString(sender.getRole()))) {

			return false;
		}

		return true;
	}

	public static boolean isKickPayload(String xml) {
		if (xml == null) return false;

		// 1. Verify it is a 'set' (command) rather than a 'get' (query) or 'result'
		if (!xml.contains("type='set'") && !xml.contains("type=\"set\"")) {
			return false;
		}

		// 2. The "Kick" indicator: Setting a role to 'none' removes the user from the room
		// Note: We check for role='none' specifically within the context of MUC Admin
		return xml.contains("role='none'") || xml.contains("role=\"none\"");
	}

	public static boolean isMutePayload(String xml) {
		if (xml == null) return false;

		// 1. Verify it is a 'set' (command) rather than a 'get' (query) or 'result'
		if (!xml.contains("type='set'") && !xml.contains("type=\"set\"")) {
			return false;
		}

		// 2. The "Mute" indicator: Setting a role to 'none' removes the user from the room
		// Note: We check for role='none' specifically within the context of MUC Admin
		return xml.contains("role='visitor'") || xml.contains("role=\"visitor\"");
	}
	
	public static boolean isUnMutePayload(String xml) {
		if (xml == null) return false;

		// 1. Verify it is a 'set' (command) rather than a 'get' (query) or 'result'
		if (!xml.contains("type='set'") && !xml.contains("type=\"set\"")) {
			return false;
		}

		// 2. The "Mute" indicator: Setting a role to 'none' removes the user from the room
		// Note: We check for role='none' specifically within the context of MUC Admin
		return xml.contains("role='participant'") || xml.contains("role=\"participant\"");
	}


	/*
	 **
	 * Checks if the IQ stanza is an Admin command to add/promote a user to 'member'.
	 */
	/**
	 * Checks if the provided XML represents a MUC Admin "Add Member" request.
	 * <p>
	 * This method validates that the stanza is an {@code <iq type='set'>} containing 
	 * a {@code <query>} in the MUC Admin namespace, where the {@code <item>} sets 
	 * a persistent affiliation (Owner, Admin, or Member) for a specific JID.
	 * </p>
	 *
	 * @param xml The raw XML string from the Netty buffer.
	 * @return {@code true} if the payload matches the Add Member criteria; {@code false} otherwise.
	 */
	public static boolean isAddMemberStanza(String xml) {
	    boolean isAddAction = false;
	    
	    try (StringReader sr = new StringReader(xml)) {
	        XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);

	        try {
	            /* * IMPORTANT: XMLStreamReader starts at START_DOCUMENT state. 
	             * We must advance to the first tag (<iq>) to access attributes.
	             */
	            if (reader.hasNext()) {
	                reader.nextTag(); 
	            }

	            // 1. Verify we are at the <iq> element and it is a 'set' type
	            String iqType = reader.getAttributeValue(null, "type");
	            if (!"set".equals(iqType)) {
	                return false;
	            }

	            // 2. Iterate through children to find the <item> tag
	            while (reader.hasNext()) {
	                int event = reader.next();

	                if (event == XMLStreamConstants.START_ELEMENT) {
	                    String localName = reader.getLocalName();

	                    if ("item".equals(localName)) {
	                        String affiliation = reader.getAttributeValue(null, "affiliation");
	                        String targetJid = reader.getAttributeValue(null, "jid");

	                        /*
	                         * 3. Logic Check: 
	                         * In XMPP, adding a member means changing their affiliation 
	                         * from 'none' to 'member', 'admin', or 'owner'.
	                         */
	                        if (("owner".equals(affiliation) || "admin".equals(affiliation) || "member".equals(affiliation)) 
	                                && targetJid != null) {
	                            isAddAction = true;
	                        }
	                        // Found the relevant metadata; exit the loop
	                        break; 
	                    }
	                }

	                // Stop if we exit the <iq> block without finding what we need
	                if (event == XMLStreamConstants.END_ELEMENT && "iq".equals(reader.getLocalName())) {
	                    break;
	                }
	            }

	        } finally {
	            reader.close();
	        }

	        return isAddAction;
	    } catch (XMLStreamException e) {
	        log.error("StAX parsing failed for AddMember check. Payload: {}", xml, e);
	        return false;
	    } catch (IllegalStateException e) {
	        log.error("Invalid XML state transition for payload: {}", xml, e);
	        return false;
	    }
	}

	public static String getNewMemberJid(String xml) throws XMLStreamException {
		try (StringReader sr = new StringReader(xml)) {
			XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);

			try {
				// 1. Verify IQ Type is 'set'
				String iqType = reader.getAttributeValue(null, "type");
				if (!"set".equals(iqType)) return null;

				while (reader.hasNext()) {
					int event = reader.next();

					if (event == XMLStreamConstants.START_ELEMENT) {
						String localName = reader.getLocalName();

						if ("item".equals(localName)) {
							String affiliation = reader.getAttributeValue(null, "affiliation");
							String targetJid = reader.getAttributeValue(null, "jid");

							// 3. Logic Check: Must have a JID and be setting affiliation to 'member'
							// Note: You can expand this to include 'admin' or 'owner' if needed
							if (("owner".equals(affiliation) || "admin".equals(affiliation) || "member".equals(affiliation)) 
									&& targetJid != null) {
								return targetJid;
							}
							break; // Found the item, we can stop
						}
					}

					if (event == XMLStreamConstants.END_ELEMENT && "iq".equals(reader.getLocalName())) {
						break;
					}
				}

			} finally {
				reader.close();
			}

			return null;
		}
	}

}
