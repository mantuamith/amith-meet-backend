package com.algomeet.signalservice.demo;

import java.util.Base64;
import java.util.List;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.IdentityKey;
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
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore;
import org.signal.libsignal.protocol.util.KeyHelper;

public class ExampleAliceToBobWeb {	
	private static final SignalProtocolAddress ALICE_ADDRESS = new SignalProtocolAddress("2fc35cae-e0b7-40a5-b2aa-e86206730e99", 1);
	private static final SignalProtocolAddress BOB_ADDRESS   = new SignalProtocolAddress("ppss00huw-kkd0-0df3-np6a-d84op538mh27", 1);

	public static void main(String[] args) throws Exception {		
		test();
	}  
	
	private static IdentityKeyPair generateIdentityKeyPair() {
		ECKeyPair identityKeyPairKeys = Curve.generateKeyPair();

		return new IdentityKeyPair(
				new IdentityKey(identityKeyPairKeys.getPublicKey()), identityKeyPairKeys.getPrivateKey());
	}
	
	private static IdentityKeyPair getBobIdentityKeyPair() throws InvalidKeyException {

		return new IdentityKeyPair(
				new IdentityKey(new ECPublicKey(Base64.getDecoder().decode("BQ628exkgSRR+FzXZo9U+JE5Op5LALg6IjbipVvFAUB/"))), 
				new ECPrivateKey(Base64.getDecoder().decode("SKm9MV33dzIhmyMsaZYbr2CWd5IEi7HP2oUyCW7n/Hk=")));
	}

	private static int generateRegistrationId() {
		return KeyHelper.generateRegistrationId(false);
	}

	public static void test() throws InvalidKeyException, UntrustedIdentityException, NoSessionException, 
	InvalidMessageException, InvalidVersionException, LegacyMessageException, 
	DuplicateMessageException, InvalidKeyIdException {

		InMemorySignalProtocolStore aliceStore = new InMemorySignalProtocolStore(generateIdentityKeyPair(), generateRegistrationId());
		SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

		// Generate bob store and keys
		final InMemorySignalProtocolStore bobStore = new InMemorySignalProtocolStore(getBobIdentityKeyPair(), 11188);
		ECKeyPair    bobPreKeyPair            = new ECKeyPair (new ECPublicKey(Base64.getDecoder().decode("BcSx9HxZbyIgdkwblWvpjWNc9uh0PpR1pigkBPgCfv5A")), 
				new ECPrivateKey(Base64.getDecoder().decode("AGR4jdz8dvB4QhP/DVvrPaY0zjSltXEGgIMjzC+aJ3k=")));
		
		ECKeyPair    bobSignedPreKeyPair      = new ECKeyPair (new ECPublicKey(Base64.getDecoder().decode("BaYx0l0d7l+nS7Rvkx7gWbdbJmA8+EiGEpls+CglnqUS")), 
				new ECPrivateKey(Base64.getDecoder().decode("qCnn9gzBmpLr1I+rV9njLL5FKSz7uFRwGVA8g6OAFlA=")));

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
		// Upload device keys (not only Sender but all users) to backend using API endpoints: 
		// POST /signal/v2/devices - This endoint will return the device ID save it in your device to be used in succeeding request.
		// POST /signal/v2/devices/{deviceId}/keys - Upload pre-keys bundle

		
		// Get/Retrieve recipient device identity key and prekeys from backend using API endpoint: GET /signal/v2/keys/{userkKey}?deviceIds=
		PreKeyBundle bobPreKeyBundle= new PreKeyBundle(11188, 
				bobDeviceId,
				bobPreKeyId, 
				bobPreKeyPair.getPublicKey(),
				bobSignedPreKeyId, 
				bobSignedPreKeyPair.getPublicKey(),
				Base64.getDecoder().decode("00ixaQDJeGdyvZ8jkuee+qznGxs7JVgvKrm0yVuvJCNxZPuBZHLnCJwfUyGv32JgqT64Ev7CsOen6sugsfy0Bg=="),
				//bobStore.getIdentityKeyPair().getPublicKey(),
				bobStore
				.getIdentityKeyPair().getPublicKey() /*,
				bobKyberPreKeyId,
				//bobKyberPreKeyPair.getPublicKey(),
				new KEMPublicKey(Base64.getDecoder().decode("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")),
				Base64.getDecoder().decode("h80Ibg5k9r0VDe9IFS2GyWwow/ICi3DpJvD1ZoUY3fw2StDeg/YADUFLSAN/SrpQYITXhAQaBWAEy/f9m/Lmgg==")*/);
		

		aliceSessionBuilder.process(bobPreKeyBundle);

		final String            originalMessage    = "L'homme est condamné à être libre test";
		SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
		CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());
		System.out.println("Encrypted: " + Base64.getEncoder().encodeToString(outgoingMessage.serialize()));

		PreKeySignalMessage incomingMessage = new PreKeySignalMessage(outgoingMessage.serialize());

		//Add tp store the prekeys
		bobStore.storePreKey(bobPreKeyId, new PreKeyRecord(bobPreKeyId, bobPreKeyPair));
		// Sample add more pre-keys
		//bobStore.storePreKey(2, new PreKeyRecord(2, bobPreKeyPair));
		
		//Add tp store the signed-prekeys
		bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobPreKeyBundle.getSignedPreKeyId(), 
				System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

		//Add tp store the kyber-prekeys
		/*
		bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobPreKeyBundle.getKyberPreKeyId(), 
				System.currentTimeMillis(), bobKyberPreKeyPair, bobKyberPreKeySignature));
				*/

		SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
		byte[] plaintext = bobSessionCipher.decrypt(incomingMessage);

		System.out.println("Decrypted message: " + new String(plaintext));
		
		
		/** Backup Bob device keys */
		IdentityKeyPair ik = bobStore.getIdentityKeyPair();
		int registrationId = bobStore.getLocalRegistrationId();		
		String serializedIdentityKey =  Base64.getEncoder().encodeToString(ik.serialize());
		
		// Serialize unuzed prekeys
		List<Integer> preKeyIds = List.of(2); // Device prekey IDs tracker
		for (Integer preKeyId: preKeyIds) {
			// check for unsed pre-keys
			if (bobStore.containsPreKey(2)) {
				String serializedPrekey = Base64.getEncoder().encodeToString(bobStore.loadPreKey(2).serialize());
			}
		}
		
		String serializedSignedPreKey = Base64.getEncoder().encodeToString(bobStore.loadSignedPreKeys().get(0).serialize());
		/* String serializedKyberPreKey = Base64.getEncoder().encodeToString(bobStore.loadKyberPreKeys().get(0).serialize()); */		
		// Encrypt using AES & upload device keys backup to backend using API endpoint: POST /signal/backup/device-keys
		
		
		// Retrieve and restore device keys backup from backend using API endpoint: GET /signal/backup/device-keys/{deviceId}
		IdentityKeyPair restoreIdentity = new IdentityKeyPair(Base64.getDecoder().decode(serializedIdentityKey));
		SignedPreKeyRecord restoreSignedPreKeyRecord = new SignedPreKeyRecord(Base64.getDecoder().decode(serializedSignedPreKey));
		/* KyberPreKeyRecord restoreKyberPreKeyRecord = new KyberPreKeyRecord(Base64.getDecoder().decode(serializedKyberPreKey)); */
		// Load restore keys to Bob store
		//bobStore.storePreKey();
		//bobStore.storeSignedPreKey();
		//bobStore.storeKyberPreKey();
		
		/** Backup Bob's sessions (Contain both inbound and outbound) */
		// Get list of all known sessions
		SessionRecord bobSession = bobStore.loadSession(ALICE_ADDRESS);

		// Save into your backup structure
		Integer bobRegId = bobSession.getLocalRegistrationId();
		String bobSerializedSessionBackup = Base64.getEncoder().encodeToString(bobSession.serialize());
		// Encrypt using AES & upload user session backups to backend using API endpoint: POST /signal/backup/devices/{deviceId}/sessions		
		
		// Retrieve and restore session backups from backend using API endpoint: GET /signal/backup/devices/{deviceId}/sessions
		// Decrypt using AES
		byte[] restoreSerializedSessionBytes = Base64.getDecoder().decode(bobSerializedSessionBackup);
		SessionRecord restoredRecord = new SessionRecord(restoreSerializedSessionBytes);
		// Store session to in-memory store
		bobStore.storeSession(ALICE_ADDRESS, restoredRecord);
				
		
		/** Backup Alice's sessions (Contain both inbound and outbound)  */
		// Get list of all known sessions
		SessionRecord aliceSession = aliceStore.loadSession(BOB_ADDRESS);

		// Save into your backup structure
		Integer aliceRegistrationId = aliceSession.getLocalRegistrationId();
		System.out.println("Local registration: " + aliceRegistrationId);
		String aliceSerializedSessionBackup = Base64.getEncoder().encodeToString(aliceSession.serialize());
		// Encrypt using AES & upload user session backups to backend using API endpoint: POST /signal/backup/devices/{deviceId}/sessions		
		// Retrieve and restore session backups from backend using API endpoint: GET /signal/backup/devices/{deviceId}/sessions
		// Decrypt using AES
		byte[] restoreSerializedOutboundSessionBytes = Base64.getDecoder().decode(aliceSerializedSessionBackup);
		SessionRecord restoredOutboundRecord = new SessionRecord(restoreSerializedOutboundSessionBytes);
		// Store session to in-memory store
		aliceStore.storeSession(BOB_ADDRESS, restoredOutboundRecord);
	}	
}