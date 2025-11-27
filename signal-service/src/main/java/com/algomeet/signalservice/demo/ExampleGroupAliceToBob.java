package com.algomeet.signalservice.demo;

import static org.signal.libsignal.internal.FilterExceptions.filterExceptions;

import java.util.Base64;
import java.util.UUID;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.GroupCipher;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.protocol.groups.state.InMemorySenderKeyStore;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;

public class ExampleGroupAliceToBob {		
	private static final SignalProtocolAddress SENDER_ADDRESS =
			filterExceptions(() -> new SignalProtocolAddress("+14150001111", 1));
	private static final UUID DISTRIBUTION_ID =
			UUID.fromString("d1d1d1d1-7000-11eb-b32a-33b8a8a487a6");


	public static void main(String[] args) throws Exception {		
		test();
	}  

	public static void test() throws InvalidKeyException, InvalidMessageException, InvalidVersionException, LegacyMessageException, NoSessionException, DuplicateMessageException {
		InMemorySenderKeyStore aliceStore = new InMemorySenderKeyStore();
		InMemorySenderKeyStore bobStore = new InMemorySenderKeyStore();

		GroupSessionBuilder aliceSessionBuilder = new GroupSessionBuilder(aliceStore);
		GroupSessionBuilder bobSessionBuilder = new GroupSessionBuilder(bobStore);

		GroupCipher aliceGroupCipher = new GroupCipher(aliceStore, SENDER_ADDRESS);
		GroupCipher bobGroupCipher = new GroupCipher(bobStore, SENDER_ADDRESS);

		SenderKeyDistributionMessage sentAliceDistributionMessage =
				aliceSessionBuilder.create(SENDER_ADDRESS, DISTRIBUTION_ID);

		System.out.println("skdm: " + Base64.getEncoder().encodeToString(sentAliceDistributionMessage.serialize()));
		// Encrypt SKDM using 1:1 session and upload to BE using API endpoint: POST /signal/v2/devices/{senderDeviceId}/groups/{groupId}/sender-keys		


		// Retrieve recipient SKDM and decrypt using 1:1 session from BE using API endpoint: GET /signal/v2/devices/{receiverDeviceId}/groups/{groupId}/sender-keys/poll	
		SenderKeyDistributionMessage receivedAliceDistributionMessage =
				new SenderKeyDistributionMessage(sentAliceDistributionMessage.serialize());

		bobSessionBuilder.process(SENDER_ADDRESS, receivedAliceDistributionMessage);

		CiphertextMessage ciphertextFromAlice =
				aliceGroupCipher.encrypt(DISTRIBUTION_ID, "smert ze smert".getBytes());
		System.out.println("Encrypted: " + Base64.getEncoder().encodeToString(ciphertextFromAlice.serialize()));

		byte[] plaintextFromAlice = bobGroupCipher.decrypt(ciphertextFromAlice.serialize());
		System.out.println("Decrypted: " + new String(plaintextFromAlice));



		/** Backup Bob's inbounds group session */		
		SenderKeyRecord record = bobStore.loadSenderKey(SENDER_ADDRESS, DISTRIBUTION_ID);
		byte[] bytes = record.serialize();

		System.out.println("SenderKeyRecord inbound: " + Base64.getEncoder().encodeToString(bytes));
		// Encrypt and upload group session backup to backend using API endpoint: POST /signal/backup/group-sessions

		// Restore
		// Retrive and restore group session backup from backend using API endpoint: GET /signal/backup/group-sessions
		byte[] bytesRestore = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(bytes));
		SenderKeyRecord recordRestore = new SenderKeyRecord(bytesRestore);

		bobStore.storeSenderKey(SENDER_ADDRESS, DISTRIBUTION_ID, recordRestore);


		/** Backup Alice's outbounds group session */		
		SenderKeyRecord recordOB = aliceStore.loadSenderKey(SENDER_ADDRESS, DISTRIBUTION_ID);
		byte[] bytesOB = recordOB.serialize();

		System.out.println("SenderKeyRecord outbound: " + Base64.getEncoder().encodeToString(bytesOB));
		// Encrypt and upload group session backup to backend using API endpoint: POST /signal/backup/group-sessions

		// Restore
		// Retrive and restore group session backup from backend using API endpoint: GET /signal/backup/group-sessions
		byte[] bytesRestoreOB = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(bytes));
		SenderKeyRecord recordRestoreOB = new SenderKeyRecord(bytesRestoreOB);

		aliceStore.storeSenderKey(SENDER_ADDRESS, DISTRIBUTION_ID, recordRestoreOB);
	}	
}