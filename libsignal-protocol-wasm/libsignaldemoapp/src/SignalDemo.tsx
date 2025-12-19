// src/SignalDemo.tsx
import React, { useEffect, useState } from "react";
import { InMemorySignalProtocolStore } from "./libsignal/state/impl/InMemorySignalProtocolStore";

import { IdentityKey } from "./libsignal/protocol/IdentityKey";
import { ECKeyPair } from "./libsignal/protocol/ecc/ECKeyPair";
import { SignalProtocolAddress } from "./libsignal/protocol/SignalProtocolAddress";
import { IdentityKeyPair } from "./libsignal/protocol/IdentityKeyPair";
import { KeyHelper } from "./libsignal/protocol/util/KeyHelper";
import { KEMKeyPair } from "./libsignal/protocol/kem/KEMKeyPair";
import { KEMKeyType } from "./libsignal/protocol/kem/KEMKeyType";
import { PreKeyBundle } from "./libsignal/state/PreKeyBundle";
import { SessionBuilder } from "./libsignal/protocol/SessionBuilder";
import { SessionCipher } from "./libsignal/protocol/SessionCipher";
import { PreKeyRecord } from "./libsignal/state/PreKeyRecord";
import { SignedPreKeyRecord } from "./libsignal/state/SignedPreKeyRecord";
import { KyberPreKeyRecord } from "./libsignal/state/KyberPreKeyRecord";
import { PreKeySignalMessage } from "./libsignal/protocol/message/PreKeySignalMessage";
import initWasm from "../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";
import { ECPublicKey } from "./libsignal/protocol/ecc/ECPublicKey";
import { ECPrivateKey } from "./libsignal/protocol/ecc/ECPrivateKey";
import { KEMPublicKey } from "./libsignal/protocol/kem/KEMPublicKey";
import { KEMSecretKey } from "./libsignal/protocol/kem/KEMSecretKey";

// base64 helpers
const b64 = {
  encode: (buf: Uint8Array) => btoa(String.fromCharCode(...Array.from(buf))),
  decodeToUint8: (s: string) => {
    const bin = atob(s);
    const arr = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    return arr;
  },
};

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;

  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }

  return btoa(binary);
}

export default function SignalDemo() {
  const [ready, setReady] = useState(true);
  const [output, setOutput] = useState("");
  // keep UI fields around (optional) for debugging or advanced usage
  const [alicePlainMessageToBob, setAlicePlainMessageToBob] = useState("L'homme est condamné à être libre");
  const [aliceEncryptedMessageToBob, setAliceEncryptedMessageToBob] = useState("");
  const [aliceDecryptedMessageToBob, setAliceDecryptedMessageToBob] = useState("");

  async function runDemo() {
  //  Init addresses
  const aliceAddress = new SignalProtocolAddress("2fc35cae-e0b7-40a5-b2aa-e86206730e99", 1);
  const bobAddress = new SignalProtocolAddress("ppss00huw-kkd0-0df3-np6a-d84op538mh27", 1);
    
  // Init Alice keys
  const aliceIdentityKeyECPair = ECKeyPair.generate();
  const aliceIdentityKey = new IdentityKey(aliceIdentityKeyECPair.publicKey);
  const aliceIdentityKeyPair = new IdentityKeyPair(aliceIdentityKey, aliceIdentityKeyECPair.privateKey);
  const aliceRegistrationId = KeyHelper.generateRegistrationId();

  const  aliceKyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
  console.log(aliceKyberPreKeyPair);
  /*
  console.log("Kyber secretKey: " + toBase64(aliceKyberPreKeyPair.secretKey.serialize()));
  console.log("Kyber secretKey: " + toBase64(aliceKyberPreKeyPair.publicKey.serialize()));
  */

  console.log("------->");
  const aliceStore = new InMemorySignalProtocolStore(aliceIdentityKeyPair, aliceRegistrationId);
  console.log(aliceIdentityKeyECPair.publicKey);  
  console.log("------->");
  console.log("privateKey: " + toBase64(aliceIdentityKeyECPair.privateKey.serialize()));
  console.log("publicKey: " + toBase64(aliceIdentityKeyECPair.publicKey.serialize()));
  console.log("------->");

    // Convert Uint8Array -> base64 for wasm calls that expect base64 strings
  const aliceIdPrivB64 = b64.encode(aliceIdentityKeyECPair.privateKey.serialize());
  const aliceIdPubB64 = b64.encode(aliceIdentityKeyECPair.publicKey.serialize());

  // Inti Bob keys
  var bobIdentityKeyECPair = null;
  var bobIdentityKey = null;
  var bobIdentityKeyPair = null;
  var bobRegistrationId = null;
  var bobKyberPreKeyPair = null;
  var bobKyberPreKeySig = null;
  var bobPreKeyPair = null;
  var bobSignedPreKeyPair = null;
  var bobSignedPreKeySig = null;

  // Check and retrieve the flag

  const bobIdentityKeySerialized = localStorage.getItem("bobIdentityKey");
  bobIdentityKeyECPair = ECKeyPair.generate();
 
  if (bobIdentityKeySerialized !== null) {
     bobIdentityKey = new IdentityKey(b64.decodeToUint8(bobIdentityKeySerialized));     
  } else {
     bobIdentityKey = new IdentityKey(bobIdentityKeyECPair.publicKey);   
     localStorage.setItem("bobIdentityKey", b64.encode(bobIdentityKey.serialize()));  
  }

  const bobIdentityKeyPairSerialized = localStorage.getItem("bobIdentityKeyPair");
  if (bobIdentityKeyPairSerialized != null) {
    bobIdentityKeyPair = new IdentityKeyPair(b64.decodeToUint8(bobIdentityKeyPairSerialized));
  } else {
    bobIdentityKeyPair = new IdentityKeyPair(bobIdentityKey, bobIdentityKeyECPair.privateKey);    
    localStorage.setItem("bobIdentityKeyPair", b64.encode(bobIdentityKeyPair.serialize()));  
  }
  
  const bobRegistrationIdStored = localStorage.getItem("bobRegistrationId");
  if (bobRegistrationIdStored != null) {
    bobRegistrationId = parseInt(bobRegistrationIdStored);
  } else {
    bobRegistrationId = KeyHelper.generateRegistrationId();  
    localStorage.setItem("bobRegistrationId", bobRegistrationId + "");  
  }

  // Prekey
  const bobPreKeyPairSerialized = localStorage.getItem("bobPreKeyPair");
  if (bobPreKeyPairSerialized != null) {
    const keys = bobPreKeyPairSerialized.split("|");
    bobPreKeyPair = new ECKeyPair(new ECPublicKey(b64.decodeToUint8(keys[0])), 
        new ECPrivateKey(b64.decodeToUint8(keys[1])));
  } else {
    bobPreKeyPair = ECKeyPair.generate(); 
    localStorage.setItem("bobPreKeyPair", b64.encode(bobPreKeyPair.publicKey.serialize()) + "|" + 
                          b64.encode(bobPreKeyPair.privateKey.serialize()));  
  }

  // Signed Prekey
  const bobSignedPreKeyPairSerialized = localStorage.getItem("bobSignedPreKeyPair");
  if (bobSignedPreKeyPairSerialized != null) {
    const keys = bobSignedPreKeyPairSerialized.split("|");
    bobSignedPreKeyPair = new ECKeyPair(new ECPublicKey(b64.decodeToUint8(keys[0])), 
        new ECPrivateKey(b64.decodeToUint8(keys[1])));
  } else {
    bobSignedPreKeyPair = ECKeyPair.generate();
    localStorage.setItem("bobSignedPreKeyPair", b64.encode(bobSignedPreKeyPair.publicKey.serialize()) + "|" + 
                          b64.encode(bobSignedPreKeyPair.privateKey.serialize()));  
  }

  bobSignedPreKeySig = bobIdentityKeyPair.getPrivateKey().calculateSignature(bobSignedPreKeyPair.publicKey.serialize());

  // Kyber prekey
  const bobKyberPreKeyPairSerialized  = localStorage.getItem("bobKyberPreKeyPair");
  if (bobKyberPreKeyPairSerialized != null) {
    const keys = bobKyberPreKeyPairSerialized.split("|");
    const kemPub = new KEMPublicKey(b64.decodeToUint8(keys[0]));
    const kemSec = new KEMSecretKey(b64.decodeToUint8(keys[1]));
    
    bobKyberPreKeyPair = KEMKeyPair.fromKeys(kemPub, kemSec);
  } else {
    bobKyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
    localStorage.setItem("bobKyberPreKeyPair", b64.encode(bobKyberPreKeyPair.publicKey.serialize()) + "|" + 
                          b64.encode(bobKyberPreKeyPair.secretKey.serialize()));  
  }
  
  bobKyberPreKeySig = bobIdentityKeyPair.getPrivateKey().calculateSignature(bobKyberPreKeyPair.publicKey.serialize());
  /*
  console.log("Kyber secretKey: " + toBase64(bobKyberPreKeyPair.secretKey.serialize()));
  console.log("Kyber secretKey: " + toBase64(bobKyberPreKeyPair.publicKey.serialize()));
  */
 
  console.log("------->");
  console.log("privateKey: " + toBase64(bobIdentityKeyPair.privateKey.serialize()));
  console.log("publicKey: " + toBase64(bobIdentityKeyPair.publicKey.serialize()));
  console.log("------->");
 
  console.log("signed prekey signature: " + b64.encode(bobSignedPreKeySig));
  console.log("Kyber prekey signature: " + b64.encode(bobKyberPreKeySig));

  const bobStore = new InMemorySignalProtocolStore(bobIdentityKeyPair, bobRegistrationId);

  // Create preKeyBundle
  const bobDeviceId = 1;
	const bobPreKeyId = 1;
	const bobSignedPreKeyId = 2;
	const bobKyberPreKeyId = 5;

  const bobPreKeyBundle = new PreKeyBundle(bobRegistrationId, 
	      bobDeviceId,
				bobPreKeyId, 
				bobPreKeyPair.publicKey,
				bobSignedPreKeyId, 
				bobSignedPreKeyPair.publicKey,
				bobSignedPreKeySig,
				bobIdentityKey,
				bobKyberPreKeyId,
				bobKyberPreKeyPair.publicKey,
				bobKyberPreKeySig);
    
  // Create Alice session builder
  const aliceSessionBuilder = SessionBuilder.fromStore(aliceStore, bobAddress);

  // Bob’s PreKeyBundle fetched from server
  await aliceSessionBuilder.process(bobPreKeyBundle);
   
	const aliceSessionCipher = SessionCipher.fromStore(aliceStore, bobAddress);
   
  const message = alicePlainMessageToBob;
  const bytes = new TextEncoder().encode(message);
	const outgoingMessage = await aliceSessionCipher.encrypt(bytes);
  
  // Display
  setAliceEncryptedMessageToBob(b64.encode(outgoingMessage.serialize()));
  console.log("Encrypted message type: ", outgoingMessage.getType());
  console.log("Encrypted message:", b64.encode(outgoingMessage.serialize()));

  /* Add to Bob's store its prekeys */
  //Add tp store the prekeys
	bobStore.storePreKey(bobPreKeyId, new PreKeyRecord(bobPreKeyId, bobPreKeyPair));		
	//Add tp store the signed-prekeys
	bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobSignedPreKeyId, 
				Date.now(), bobSignedPreKeyPair, bobSignedPreKeySig));
	//Add tp store the kyber-prekeys
	bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobKyberPreKeyId, 
				Date.now(), bobKyberPreKeyPair, bobKyberPreKeySig));

  const bobSessionCipher = SessionCipher.fromStore(bobStore, aliceAddress);
  const plaintext = await bobSessionCipher.decrypt(new PreKeySignalMessage(outgoingMessage.serialize()));
  const text = new TextDecoder().decode(plaintext);
  
  //Display
  setAliceDecryptedMessageToBob(text);
  console.log("Decrypted message:", text);
  /*
  console.log("identity pub:", bobIdentityKey.serialize());
  console.log("signed prekey pub:", bobSignedPreKeyPair2.publicKey.serialize());
  
  console.log("signed prekey sig:", bobSignedPreKeySig);

  console.log("kyber prekey pub:", bobKyberPreKeyPair.publicKey.serialize());
  console.log("kyber prekey sig:", bobKyberPreKeySig);
  */
  // Process preKeyBundle
  aliceSessionBuilder.process(bobPreKeyBundle);

  setOutput(
    `Success`
  );
}

useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        await initWasm(); // 🔑 THIS IS REQUIRED
        if (!cancelled) {
          setReady(true);
          console.log("libsignal_wasm_pqxdh initialized");
        }
      } catch (e) {
        console.error("WASM init failed", e);
        setOutput("WASM init failed: " + String(e));
      }
    })();
    return () => {
      cancelled = true;
    };
}, []);

  return (
    <div style={{ padding: 20 }}>
      <h2>Signal WASM Demo</h2>

      <div style={{ marginTop: 12 }}>
        <label>Alice message to Bob (Plain Text):</label>
        <textarea
          rows={3}
          cols={80}
          value={alicePlainMessageToBob}
          onChange={(e) => setAlicePlainMessageToBob(e.target.value)}
          placeholder="Raw text"
        />
      </div>

      <div style={{ marginTop: 8 }}>
        <label>Alice encrypted message to Bob (base64):</label>
        <textarea
          rows={3}
          cols={80}
          value={aliceEncryptedMessageToBob}
          onChange={(e) => setAliceEncryptedMessageToBob(e.target.value)}
          placeholder="Raw encrypted base64 format"
        />
      </div>
    
      <div style={{ marginTop: 8 }}>
        <label>Decrypted message:</label>
        <br />
        <textarea
          rows={3}
          cols={80}
          value={aliceDecryptedMessageToBob}
          onChange={(e) => setAliceDecryptedMessageToBob(e.target.value)}
          placeholder="Decrypted message"
        />
      </div>

      <div style={{ marginTop: 12 }}>
        <button disabled={!ready} onClick={runDemo}>
          Run Encrypt and Decrypt Test
        </button>
      </div>

      <pre style={{ whiteSpace: "pre-wrap", marginTop: 20 }}>{output}</pre>
    </div>
  );
}
