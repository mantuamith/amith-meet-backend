package com.algomeet.signalservice.demo;

import static org.signal.libsignal.internal.FilterExceptions.filterExceptions;

import java.util.Base64;
import java.util.Random;

import org.signal.libsignal.internal.NativeTesting;
import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.util.Medium;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;

import kotlin.Pair;

public class SignalExample {	
	static final SignalProtocolAddress ALICE_ADDRESS =
			filterExceptions(() -> new SignalProtocolAddress("+14151111111", 1));
	static final SignalProtocolAddress BOB_ADDRESS =
			filterExceptions(() -> new SignalProtocolAddress("+14152222222", 1));
	static final SignalProtocolAddress MALLORY_ADDRESS =
			filterExceptions(() -> new SignalProtocolAddress("+14153333333", 1));

	private BundleFactory bundleFactory = new PQXDHBundleFactory();;

	public static byte[] getAliceBaseKey(SessionRecord record) {
		return filterExceptions(
				() -> record.guardedMapChecked(NativeTesting::SessionRecord_GetAliceBaseKey));
	}

	public static void main(String[] args) throws Exception {		
		// Create Spring Boot application
		SpringApplication app = new SpringApplication(SignalExample.class);

		// Set as non-web application (CLI)
		app.setWebApplicationType(WebApplicationType.NONE);

		// Start context
		ApplicationContext context = app.run(args);

		// Retrieve beans
		SignalExample sample = context.getBean(SignalExample.class);
		sample.testBasicPreKey();
	}  


	Pair<SignalProtocolStore, SignalProtocolStore> initializeSessions() {
		try {
			SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
			SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

			SignalProtocolStore bobStore = new TestInMemorySignalProtocolStore();

			PreKeyBundle bobPreKey = bundleFactory.createBundle(bobStore);

			aliceSessionBuilder.process(bobPreKey);

			System.out.println(aliceStore.containsSession(BOB_ADDRESS));


			String originalMessage = "initial hello!";
			SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
			
			CiphertextMessage outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

			System.out.println(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

			PreKeySignalMessage incomingMessage = new PreKeySignalMessage(outgoingMessage.serialize());

			SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
			byte[] plaintext = bobSessionCipher.decrypt(incomingMessage);

			System.out.println(bobStore.containsSession(ALICE_ADDRESS));
			System.out.println(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion());
			System.out.println(getAliceBaseKey(bobStore.loadSession(ALICE_ADDRESS)));
			System.out.println(originalMessage.equals(new String(plaintext)));

			CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
			System.out.println(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);
			System.out.println(Base64.getEncoder().encodeToString(bobOutgoingMessage.serialize()));

			byte[] alicePlaintext =
					aliceSessionCipher.decrypt(new SignalMessage(bobOutgoingMessage.serialize()));
			System.out.println(new String(alicePlaintext).equals(originalMessage));
			System.out.println(new String(alicePlaintext));

			return new Pair<>(aliceStore, bobStore);

		} catch (DuplicateMessageException
				| InvalidKeyException
				| InvalidKeyIdException
				| InvalidMessageException
				| InvalidVersionException
				| LegacyMessageException
				| NoSessionException
				| UntrustedIdentityException e) {
			throw new AssertionError("basic initialization should not encounter any exceptions", e);
		}
	}

	public void testBasicPreKey() throws NoSessionException, UntrustedIdentityException, InvalidMessageException, InvalidVersionException, DuplicateMessageException, InvalidKeyException, LegacyMessageException, InvalidKeyIdException {
		var stores = initializeSessions();
		SignalProtocolStore aliceStore = stores.getFirst();
		SignalProtocolStore bobStore = stores.getSecond();

		//runInteraction(aliceStore, bobStore);

		aliceStore = new TestInMemorySignalProtocolStore();
		var aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
		var aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);

		PreKeyBundle anotherBundle = bundleFactory.createBundle(bobStore);
		aliceSessionBuilder.process(anotherBundle);

		String originalMessage = "Good, fast, cheap: pick two";
		var outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

		var bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
		try {
			bobSessionCipher.decrypt(new PreKeySignalMessage(outgoingMessage.serialize()));
			System.out.println("shouldn't be trusted!");
		} catch (UntrustedIdentityException uie) {
			bobStore.saveIdentity(
					ALICE_ADDRESS, new PreKeySignalMessage(outgoingMessage.serialize()).getIdentityKey());
		}

		var plaintext =
				bobSessionCipher.decrypt(new PreKeySignalMessage(outgoingMessage.serialize()));
		System.out.println(new String(plaintext).equals(originalMessage));

		Random random = new Random();
		PreKeyBundle badIdentityBundle =
				new PreKeyBundle(
						bobStore.getLocalRegistrationId(),
						1,
						random.nextInt(Medium.MAX_VALUE),
						ECKeyPair.generate().getPublicKey(),
						random.nextInt(Medium.MAX_VALUE),
						anotherBundle.getSignedPreKey(),
						anotherBundle.getSignedPreKeySignature(),
						aliceStore.getIdentityKeyPair().getPublicKey(),
						random.nextInt(Medium.MAX_VALUE),
						anotherBundle.getKyberPreKey(),
						anotherBundle.getKyberPreKeySignature());

		try {
			aliceSessionBuilder.process(badIdentityBundle);
			System.out.println("shoulnd't be trusted!");
		} catch (UntrustedIdentityException uie) {
			// good
		}

		System.out.println("OK");
	}
}