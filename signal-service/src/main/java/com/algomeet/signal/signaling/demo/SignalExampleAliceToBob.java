package com.algomeet.signal.signaling.demo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.IdentityKey;
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
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;


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

		TestInMemorySignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
		SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

		final TestInMemorySignalProtocolStore bobStore = new TestInMemorySignalProtocolStore();
		ECKeyPair    bobPreKeyPair            = ECKeyPair.generate();
		ECKeyPair    bobSignedPreKeyPair      = ECKeyPair.generate();

		byte[] bobSignedPreKeySignature =
				bobStore
				.getIdentityKeyPair()
				.getPrivateKey()
				.calculateSignature(bobSignedPreKeyPair.getPublicKey().serialize());


		int bobRegistrationId = 10039;
		int bobDeviceId = 1;
		int bobPreKeyId = 3;

		//		ECPublicKey preKeyPublic = ECPublicKey.fromPublicKeyBytes(Base64.getDecoder().decode("BaZ7tjdq4wqIOHk0ktbNSNDKmZKxnlRsonHoazOTFWVT"));
		int bobSignedPreKeyId = 2;
		//		ECPublicKey signedPreKeyPublic = ECPublicKey.fromPublicKeyBytes(Base64.getDecoder().decode("BQmBNExLLNZqQfcV+gn+5+p7Q1gcpQc3/5ZsdKVxa08k"));
		//		byte[] signedPreKeySignature = Base64.getDecoder().decode("s1HMJtNYfiVQvkfDj/eWyuj0JLj5a74x+oWrrSsVnvpiWuIle4JK5Mx///ljg/CDUxmsALRiNSQ9zMmZbG/FBw==");
		//		IdentityKey identityKey = new IdentityKey(Base64.getDecoder().decode("Bb6FndhNDd0I9F4ycGgVAtOaaJukpXki6udlebUkOL1b"), 0);


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

		System.out.println("bobRegistrationId: " + bobRegistrationId);
		System.out.println("bobDeviceId: " + bobDeviceId);
		System.out.println("bobPreKeyId: " + bobPreKeyId);
		System.out.println("bobPreKeyPair.getPublicKey(): " + Base64.getEncoder().encodeToString(bobPreKeyPair.getPublicKey().serialize()));
		System.out.println("bobSignedPreKeyId: " + bobSignedPreKeyId);
		System.out.println("bobSignedPreKeyPair.getPublicKey(): " + Base64.getEncoder().encodeToString(bobSignedPreKeyPair.getPublicKey().serialize()));
		System.out.println("bobSignedPreKeySignature: " + Base64.getEncoder().encodeToString(bobSignedPreKeySignature));
		System.out.println("bobStore.getIdentityKeyPair().getPublicKey(): " + Base64.getEncoder().encodeToString(bobStore.getIdentityKeyPair().getPublicKey().serialize()));
		System.out.println("bobKyberPreKeyId: " + bobKyberPreKeyId);
		System.out.println("bobKyberPreKeyPair.getPublicKey(): " + Base64.getEncoder().encodeToString(bobKyberPreKeyPair.getPublicKey().serialize()));
		System.out.println("bobKyberPreKeyPair.getPublicKey(): " + Base64.getEncoder().encodeToString(bobKyberPreKeyPair.getPublicKey().serialize()).length());
		System.out.println("bobKyberPreKeySignature------------>: " + Base64.getEncoder().encodeToString(bobKyberPreKeySignature));

		aliceSessionBuilder.process(bobPreKeyBundle);

		System.out.println(aliceStore.containsSession(BOB_ADDRESS));

		final String            originalMessage    = "L'homme est condamné à être libre";
		SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
		CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

		System.out.println(Base64.getEncoder().encodeToString(outgoingMessage.serialize()));
		System.out.println(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);


		PreKeySignalMessage incomingMessage = new PreKeySignalMessage(outgoingMessage.serialize());

		bobStore.storePreKey(bobPreKeyId, new PreKeyRecord(bobPreKeyBundle.getPreKeyId(), bobPreKeyPair));
		bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobPreKeyBundle.getSignedPreKeyId(), 
				System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

		bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobPreKeyBundle.getKyberPreKeyId(), 
				System.currentTimeMillis(), bobKyberPreKeyPair, bobKyberPreKeySignature));

		SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
		byte[] plaintext = bobSessionCipher.decrypt(incomingMessage);

		System.out.println("Decrypted message: " + new String(plaintext));
	}	
}