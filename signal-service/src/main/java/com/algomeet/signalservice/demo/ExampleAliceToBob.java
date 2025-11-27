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
import org.signal.libsignal.protocol.util.KeyHelper;

public class ExampleAliceToBob {	
	private static final SignalProtocolAddress ALICE_ADDRESS = new SignalProtocolAddress("+14151111111", 1);
	private static final SignalProtocolAddress BOB_ADDRESS   = new SignalProtocolAddress("+14152222222", 1);

	public static void main(String[] args) throws Exception {		
		test();
	}  
	
	private static IdentityKeyPair generateIdentityKeyPair() {
		ECKeyPair identityKeyPairKeys = ECKeyPair.generate();

		return new IdentityKeyPair(
				new IdentityKey(identityKeyPairKeys.getPublicKey()), identityKeyPairKeys.getPrivateKey());
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
		final InMemorySignalProtocolStore bobStore = new InMemorySignalProtocolStore(generateIdentityKeyPair(), generateRegistrationId());
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

		// Upload device keys to backend using API endpoint: POST /signal/v2/devices
		
		
		// Get/Retrieve recipient device identity key from backend using API endpoint: GET /signal/v2/devices?userKey=2fc35cae-e0b7-40a5-b2aa-e86206730e99
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


		PreKeySignalMessage incomingMessage = new PreKeySignalMessage(outgoingMessage.serialize());

		bobStore.storePreKey(bobPreKeyId, new PreKeyRecord(bobPreKeyId, bobPreKeyPair));
		bobStore.storePreKey(2, new PreKeyRecord(2, bobPreKeyPair));
				
		bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobPreKeyBundle.getSignedPreKeyId(), 
				System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

		bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobPreKeyBundle.getKyberPreKeyId(), 
				System.currentTimeMillis(), bobKyberPreKeyPair, bobKyberPreKeySignature));

		SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
		byte[] plaintext = bobSessionCipher.decrypt(incomingMessage);

		System.out.println("Decrypted message: " + new String(plaintext));
		
		
		/** Backup device keys */
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
		String serializedKyberPreKey = Base64.getEncoder().encodeToString(bobStore.loadKyberPreKeys().get(0).serialize());		
		// Encrypt & upload device keys backup to backend using API endpoint: POST /signal/backup/device-keys
		
		
		// Retrieve and restore device keys backup from backend using API endpoint: GET /signal/backup/device-keys/{deviceId}
		IdentityKeyPair restoreIdentity = new IdentityKeyPair(Base64.getDecoder().decode(serializedIdentityKey));
		SignedPreKeyRecord restoreSignedPreKeyRecord = new SignedPreKeyRecord(Base64.getDecoder().decode(serializedSignedPreKey));
		KyberPreKeyRecord restoreKyberPreKeyRecord = new KyberPreKeyRecord(Base64.getDecoder().decode(serializedKyberPreKey));
				
		
		/** Backup sessions */
		// Get list of all known sessions
		SessionRecord aliceSession = bobStore.loadSession(ALICE_ADDRESS);

		// Save into your backup structure
		Integer aliceRegistrationId = aliceSession.getLocalRegistrationId();
		String SerializedSessionBackup = Base64.getEncoder().encodeToString(aliceSession.serialize());
		// Encrypt & upload user session backups to backend using API endpoint: POST /signal/backup/devices/{deviceId}/sessions
		
		// Retrieve and restore session backups from backend using API endpoint: GET /signal/backup/devices/{deviceId}/sessions
	}	
}