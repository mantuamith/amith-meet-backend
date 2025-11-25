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
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;


public class SignalExampleGroupAliceToBob {		

	public static void main(String[] args) throws Exception {		
		// Create Spring Boot application
		SpringApplication app = new SpringApplication(SignalExampleGroupAliceToBob.class);

		// Set as non-web application (CLI)
		app.setWebApplicationType(WebApplicationType.NONE);

		// Start context
		ApplicationContext context = app.run(args);

		// Retrieve beans
		SignalExampleGroupAliceToBob sample = context.getBean(SignalExampleGroupAliceToBob.class);
		sample.test();
	}  

	private static final SignalProtocolAddress ALICE_ADDRESS = new SignalProtocolAddress("+14151111111", 1);
	private static final SignalProtocolAddress BOB_ADDRESS   = new SignalProtocolAddress("+14152222222", 1);

	private static final SignalProtocolAddress SENDER_ADDRESS =
			filterExceptions(() -> new SignalProtocolAddress("+14150001111", 1));
	private static final UUID DISTRIBUTION_ID =
			UUID.fromString("d1d1d1d1-7000-11eb-b32a-33b8a8a487a6");

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
		SenderKeyDistributionMessage receivedAliceDistributionMessage =
				new SenderKeyDistributionMessage(sentAliceDistributionMessage.serialize());
		System.out.println(Base64.getEncoder().encode(sentAliceDistributionMessage.serialize()).length);

		System.out.println(DISTRIBUTION_ID == receivedAliceDistributionMessage.getDistributionId());
		System.out.println(0 == receivedAliceDistributionMessage.getIteration());
		System.out.println(
				sentAliceDistributionMessage.getChainKey() == receivedAliceDistributionMessage.getChainKey());
		System.out.println(
				sentAliceDistributionMessage.getSignatureKey() ==
				receivedAliceDistributionMessage.getSignatureKey());
		System.out.println(
				sentAliceDistributionMessage.getChainId() == receivedAliceDistributionMessage.getChainId());

		bobSessionBuilder.process(SENDER_ADDRESS, receivedAliceDistributionMessage);

		CiphertextMessage ciphertextFromAlice =
				aliceGroupCipher.encrypt(DISTRIBUTION_ID, "smert ze smert".getBytes());
		System.out.println("Encrypted: " + Base64.getEncoder().encodeToString(ciphertextFromAlice.serialize()));
		
		byte[] plaintextFromAlice = bobGroupCipher.decrypt(ciphertextFromAlice.serialize());

		System.out.println(new String(plaintextFromAlice));
	}	
}