package com.algomeet.signalservice.demo;

import java.util.Base64;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.IdentityKeyPair;
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
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;


public class SignalExampleAliceToBob {		

	public static void main(String[] args) throws Exception {		
		// Create Spring Boot application
		SpringApplication app = new SpringApplication(SignalExampleAliceToBob.class);

		// Set as non-web application (CLI)
		app.setWebApplicationType(WebApplicationType.NONE);

		// Start context
		ApplicationContext context = app.run(args);

		// Retrieve beans
		SignalExampleAliceToBob sample = context.getBean(SignalExampleAliceToBob.class);
		sample.test();
	}  

	private static final SignalProtocolAddress ALICE_ADDRESS = new SignalProtocolAddress("+14151111111", 1);
	private static final SignalProtocolAddress BOB_ADDRESS   = new SignalProtocolAddress("+14152222222", 1);

	public static void test() throws InvalidKeyException, UntrustedIdentityException, NoSessionException, 
	InvalidMessageException, InvalidVersionException, LegacyMessageException, 
	DuplicateMessageException, InvalidKeyIdException {

		InMemorySignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
		SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

		// Generate bob store and keys
		final InMemorySignalProtocolStore bobStore = new TestInMemorySignalProtocolStore();
		ECKeyPair    bobPreKeyPair            = ECKeyPair.generate();
		ECKeyPair    bobSignedPreKeyPair      = ECKeyPair.generate();

		byte[] bobSignedPreKeySignature =
				bobStore
				.getIdentityKeyPair()
				.getPrivateKey()
				.calculateSignature(bobSignedPreKeyPair.getPublicKey().serialize());

		int bobRegistrationId = bobStore.getLocalRegistrationId();
		int bobDeviceId = 1;
		int bobPreKeyId = 1;
		int bobSignedPreKeyId = 2;
		int bobKyberPreKeyId = 5;
		
		KEMKeyPair bobKyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
		byte[] bobKyberPreKeySignature =
				bobStore
				.getIdentityKeyPair()
				.getPrivateKey()
				.calculateSignature(bobKyberPreKeyPair.getPublicKey().serialize());

		PreKeyBundle bobPreKeyBundle= new PreKeyBundle(bobRegistrationId, 
				bobDeviceId,
				bobPreKeyId, 
				bobPreKeyPair.getPublicKey(),
				bobSignedPreKeyId, 
				bobSignedPreKeyPair.getPublicKey(),
				bobSignedPreKeySignature,
				bobStore.getIdentityKeyPair().getPublicKey(),
				bobKyberPreKeyId,
				bobKyberPreKeyPair.getPublicKey(),
				bobKyberPreKeySignature);
		

		aliceSessionBuilder.process(bobPreKeyBundle);

		final String            originalMessage    = "L'homme est condamné à être libre";
		SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
		CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

		System.out.println("length: " + Base64.getEncoder().encodeToString(outgoingMessage.serialize()).length());
		System.out.println(Base64.getEncoder().encodeToString(outgoingMessage.serialize()));
		System.out.println(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);


		PreKeySignalMessage incomingMessage = new PreKeySignalMessage(outgoingMessage.serialize());

		bobStore.storePreKey(bobPreKeyId, new PreKeyRecord(bobPreKeyId, bobPreKeyPair));
		bobStore.storePreKey(2, new PreKeyRecord(2, bobPreKeyPair));
				
		bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobPreKeyBundle.getSignedPreKeyId(), 
				System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

		bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobPreKeyBundle.getKyberPreKeyId(), 
				System.currentTimeMillis(), bobKyberPreKeyPair, bobKyberPreKeySignature));

		SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
		
		System.out.println("loadPreKey        --->" +  bobStore.containsPreKey(bobPreKeyId));
		byte[] plaintext = bobSessionCipher.decrypt(incomingMessage);
		System.out.println("loadPreKey        --->" +  bobStore.containsPreKey(bobPreKeyId));

		System.out.println("Decrypted message: " + new String(plaintext));
		
		
		/** Backup identity keys */
		IdentityKeyPair ik = bobStore.getIdentityKeyPair();
		int registrationId = bobStore.getLocalRegistrationId();		
		System.out.println("IdentityKeyPair   --->" +  Base64.getEncoder().encodeToString(ik.serialize()).length());
		System.out.println("loadPreKey        --->" +  bobStore.containsPreKey(2));
		System.out.println("loadPreKey        --->" +  Base64.getEncoder().encodeToString(bobStore.loadPreKey(2).serialize()).length());
		System.out.println("loadSignedPreKeys --->" +  Base64.getEncoder().encodeToString(bobStore.loadSignedPreKeys().get(0).serialize()).length());
		System.out.println("loadKyberPreKeys  --->" +  Base64.getEncoder().encodeToString(bobStore.loadKyberPreKeys().get(0).serialize()).length());
		

		// encrypt & save to disk
		// restore
		IdentityKeyPair identity = new IdentityKeyPair(ik.serialize());
		
		
		/** Backup sessions */
		// get list of all known sessions
		SessionRecord aliceSession = bobStore.loadSession(ALICE_ADDRESS);

		// Save into your backup structure
		System.out.println("getLocalRegistrationId  --->" + aliceSession.getLocalRegistrationId());
		System.out.println("bobRegistrationId  --->" + bobRegistrationId);
		String sessionBackup = Base64.getEncoder().encodeToString(aliceSession.serialize());
		System.out.println("sessionBackup  --->" +  sessionBackup.length());

	}	
}