// src/SignalDemo.tsx
import React, { useState } from "react";
import { useSignalWasm } from "./useSignalWasm";
import { parseWasmValue } from "./utils/parseWasmValue";
import { protocol } from "libsignal_wasm_pqxdh";
import { InMemorySignalProtocolStore } from "./signal/state/impl/InMemorySignalProtocolStore";

import { IdentityKey } from "./signal/protocol/IdentityKey";
import { ECPublicKey } from "./signal/protocol/ecc/ECPublicKey";
import { ECKeyPair } from "./signal/protocol/ecc/ECKeyPair";
import { SignalProtocolAddress } from "./signal/protocol/SignalProtocolAddress";
import { IdentityKeyPair } from "./signal/protocol/IdentityKeyPair";
import { KeyHelper } from "./signal/protocol/util/KeyHelper";
import { KEMKeyPair } from "./signal/protocol/kem/KEMKeyPair";
import { KEMKeyType } from "./signal/protocol/kem/KEMKeyType";
import { PreKeyBundle } from "./signal/state/PreKeyBundle";
import { SessionBuilder } from "./signal/protocol/SessionBuilder";


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

function randomBytes(n: number) {
  const arr = new Uint8Array(n);
  crypto.getRandomValues(arr);
  return arr;
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;

  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }

  return btoa(binary);
}

export default function SignalDemo() {
  const {
    ready,
    pqxdh_initiate,
    pqxdh_receive,
    x25519_pub_from_priv,
  } = useSignalWasm();

  const [output, setOutput] = useState("");
  // keep UI fields around (optional) for debugging or advanced usage
  const [bobKyberPubB64, setBobKyberPubB64] = useState("");
  const [bobKyberPrivB64, setBobKyberPrivB64] = useState("");

  async function runDemo() {
  if (!ready) {
    setOutput("WASM not loaded yet...");
    return;
  }

  // ---------- generate keys WITH wasm helpers (avoid mixing raw random) ----------
  // Alice identity + ephemeral
  // const aliceIdentityKeyPairGen = protocol.identity_key_generate();
  const aliceEphKeyPair = protocol.ephemeral_generate();

  // Bob identity + signed-prekey
  //const bobIdentityKeyPair = protocol.identity_key_generate();
  //const bobSignedPreKeyPair = protocol.signed_prekey_generate(
  //  1,
  //  bobIdentityKeyPair.private_key
  //);



  //  Addresses
  const aliceAddress = new SignalProtocolAddress("alice", 1);
  const bobAddress = new SignalProtocolAddress("bob", 1);
    
  const aliceIdentityKeyECPair = ECKeyPair.generate();
  const aliceIdentityKey = new IdentityKey(aliceIdentityKeyECPair.publicKey);
  const aliceIdentityKeyPair = new IdentityKeyPair(aliceIdentityKey, aliceIdentityKeyECPair.privateKey);
  const aliceRegistrationId = KeyHelper.generateRegistrationId();

  const  aliceKyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
  console.log(aliceKyberPreKeyPair);
  console.log("Kyber secretKey: " + toBase64(aliceKyberPreKeyPair.secretKey.serialize()));
  console.log("Kyber secretKey: " + toBase64(aliceKyberPreKeyPair.publicKey.serialize()));

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
  const aliceEphPrivB64 = b64.encode(aliceEphKeyPair.private_key);

  // Bob keys
  const bobIdentityKeyECPair = ECKeyPair.generate();
  const bobIdentityKey = new IdentityKey(bobIdentityKeyECPair.publicKey);
  const bobIdentityKeyPair2 = new IdentityKeyPair(bobIdentityKey, bobIdentityKeyECPair.privateKey);
  const bobRegistrationId = KeyHelper.generateRegistrationId();

  const bobKyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
  const bobKyberPreKeySig = bobIdentityKeyPair2.getPrivateKey().calculateSignature(bobKyberPreKeyPair.publicKey.serialize());
  console.log(bobKyberPreKeyPair);
  console.log("Kyber secretKey: " + toBase64(bobKyberPreKeyPair.secretKey.serialize()));
  console.log("Kyber secretKey: " + toBase64(bobKyberPreKeyPair.publicKey.serialize()));

  console.log("------->");
  const bobStore = new InMemorySignalProtocolStore(bobIdentityKeyPair2, bobRegistrationId);

  console.log(bobIdentityKeyECPair.publicKey);  
  console.log("------->");
  console.log("privateKey: " + toBase64(bobIdentityKeyECPair.privateKey.serialize()));
  console.log("publicKey: " + toBase64(bobIdentityKeyECPair.publicKey.serialize()));
  console.log("------->");

  const bobSignedPreKeyPair2 = ECKeyPair.generate();
  
  const bobSignedPreKeySig = bobIdentityKeyPair2.getPrivateKey().calculateSignature(bobSignedPreKeyPair2.publicKey.serialize());
  console.log("signed prekey signature: " + b64.encode(bobSignedPreKeySig));

  // Create preKeyBundle
  const bobDeviceId = 1;
	const bobPreKeyId = 1;
	const bobSignedPreKeyId = 2;
	const bobKyberPreKeyId = 5;
  const bobPreKeyPair = ECKeyPair.generate();

  const bobPreKeyBundle = new PreKeyBundle(bobRegistrationId, 
	      bobDeviceId,
				bobPreKeyId, 
				bobPreKeyPair.publicKey,
				bobSignedPreKeyId, 
				bobSignedPreKeyPair2.publicKey,
				bobSignedPreKeySig,
				bobIdentityKey,
				bobKyberPreKeyId,
				bobKyberPreKeyPair.publicKey,
				bobKyberPreKeySig);
  
  
  // Create Alice session builder
  const aliceSessionBuilder = SessionBuilder.fromStore(aliceStore, bobAddress);

  console.log("identity pub:", bobIdentityKey.serialize());
  console.log("signed prekey pub:", bobSignedPreKeyPair2.publicKey.serialize());
  console.log("signed prekey sig:", bobSignedPreKeySig);

  console.log("kyber prekey pub:", bobKyberPreKeyPair.publicKey.serialize());
  console.log("kyber prekey sig:", bobKyberPreKeySig);
  // Process preKeyBundle
  aliceSessionBuilder.process(bobPreKeyBundle);

  const bobIdPrivB64 = b64.encode(bobIdentityKeyPair2.privateKey.serialize());
  const bobIdPubB64 = b64.encode(bobIdentityKeyPair2.publicKey.serialize());
  const bobSpkPrivB64 = b64.encode(bobSignedPreKeyPair2.privateKey.serialize());
  const bobSpkPubB64 = b64.encode(bobSignedPreKeyPair2.publicKey.serialize());

  // ---------- Kyber keypair (auto-generate if not pasted) ----------
  let bobKyPrivB64 = bobKyberPrivB64.trim();
  let bobKyPubB64 = bobKyberPubB64.trim();

  if (!bobKyPrivB64 || !bobKyPubB64) {
    if (typeof protocol.kyber_keygen === "function") {
      try {
        const kyberPair = protocol.kyber_keygen(); // parseWasmValue will handle JsValue or object
        //const map = parseWasmValue(kyberPair); // your helper returns a Map or object
        // If parseWasmValue returns a Map:
        bobKyPrivB64 = kyberPair.get ? kyberPair.get("priv_b64") : kyberPair.priv_b64;
        bobKyPubB64 = kyberPair.get ? kyberPair.get("pub_b64") : kyberPair.pub_b64;

        if (!bobKyPrivB64 || !bobKyPubB64) {
          setOutput("kyber_keygen_wasm returned unexpected value; please paste Kyber keys.");
          return;
        }
        setBobKyberPrivB64(bobKyPrivB64);
        setBobKyberPubB64(bobKyPubB64);
      } catch (e: any) {
        setOutput("kyber_keygen_wasm threw: " + String(e) + "\nPlease paste Kyber keys.");
        return;
      }
    } else {
      setOutput("No Kyber keypair provided and wasm does not export kyber_keygen_wasm.");
      return;
    }
  }

  // ---------- Call pqxdh_initiate (Alice) ----------
  let initiateRaw: any;
  try {
    // Passing base64 strings (matches the existing-wasm API shown earlier)
    initiateRaw = pqxdh_initiate(
      aliceIdPrivB64,
      aliceEphPrivB64,
      bobIdPubB64,
      bobSpkPubB64,
      bobKyPubB64,
      "" // no OPK
    );
  } catch (e: any) {
    setOutput("Initiate failed (threw): " + String(e));
    return;
  }

  const initObj = parseWasmValue(initiateRaw);
  if (!initObj || !initObj.ok) {
    setOutput("Initiate failed: " + (initObj?.error ?? "unknown"));
    return;
  }

  const kyberCiphertextB64 = initObj.kyber_ciphertext_b64;
  const aliceEphPubFromInitiateB64 = initObj.eph_x25519_pub_b64;
  const aliceSharedRootB64 = initObj.shared_root_b64;

  // ---------- Call pqxdh_receive (Bob) ----------
  let bobReceiveRaw: any;
  try {
    bobReceiveRaw = pqxdh_receive(
      bobIdPrivB64,
      bobSpkPrivB64,
      bobKyPrivB64,
      kyberCiphertextB64,
      aliceIdPubB64,
      aliceEphPubFromInitiateB64
    );
  } catch (e: any) {
    setOutput("Receive failed (threw): " + String(e));
    return;
  }

  const bobObj = parseWasmValue(bobReceiveRaw);
  if (!bobObj || !bobObj.ok) {
    setOutput("Receive failed: " + (bobObj?.error ?? "unknown"));
    return;
  }

  const bobSharedRootB64 = bobObj.shared_root_b64;
  const ok = aliceSharedRootB64 === bobSharedRootB64;

  setOutput(
    `Alice Root: ${aliceSharedRootB64}\nBob Root:   ${bobSharedRootB64}\n\nMatch: ${ok ? "YES 🎉" : "NO ❌"}`
  );
}

  return (
    <div style={{ padding: 20 }}>
      <h2>Signal PQXDH WASM Demo</h2>

      <div style={{ marginTop: 12 }}>
        <label>Bob Kyber Public (base64):</label>
        <textarea
          rows={3}
          cols={80}
          value={bobKyberPubB64}
          onChange={(e) => setBobKyberPubB64(e.target.value)}
          placeholder="Optional — left empty to auto-generate if available"
        />
      </div>

      <div style={{ marginTop: 8 }}>
        <label>Bob Kyber Private (base64):</label>
        <textarea
          rows={3}
          cols={80}
          value={bobKyberPrivB64}
          onChange={(e) => setBobKyberPrivB64(e.target.value)}
          placeholder="Optional — left empty to auto-generate if available"
        />
      </div>

      <div style={{ marginTop: 12 }}>
        <button disabled={!ready} onClick={runDemo}>
          Run PQXDH Test
        </button>
      </div>

      <pre style={{ whiteSpace: "pre-wrap", marginTop: 20 }}>{output}</pre>
    </div>
  );
}
