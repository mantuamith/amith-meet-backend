// src/SignalDirectMessageDemo.tsx
import React, { useEffect, useState } from "react";

import initWasm from "libsignal_wasm_pqxdh";
import { SignalProtocolAddress } from "./libsignal/protocol/SignalProtocolAddress";
import { InMemorySenderKeyStore } from "./libsignal/protocol/groups/state/InMemorySenderKeyStore";
import { GroupSessionBuilder } from "./libsignal/protocol/groups/GroupSessionBuilder";
import { SenderKeyDistributionMessage } from "./libsignal/protocol/message/SenderKeyDistributionMessage";
import { GroupCipher } from "./libsignal/protocol/groups/GroupCipher";

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

//var bobStore: SignalProtocolStore | undefined = undefined;
//var aliceStore: SignalProtocolStore | undefined = undefined;

export default function SignalDirectMessageDemo() {
  const [ready, setReady] = useState(true);
  const [output, setOutput] = useState("");
  // keep UI fields around (optional) for debugging or advanced usage
  const [alicePlainGroupMessage, setAlicePlainGroupMessage] = useState("L'homme est condamné à être libre");
  const [aliceEncryptedGroupMessage, setAliceEncryptedGroupMessage] = useState("");
  const [aliceDecryptedGroupMessage, setAliceDecryptedGroupMessage] = useState("");

  async function runDemo() {
  //  Init addresses
  const aliceAddress = new SignalProtocolAddress("2fc35cae-e0b7-40a5-b2aa-e86206730e99", 1);
  const bobAddress = new SignalProtocolAddress("ppss00huw-kkd0-0df3-np6a-d84op538mh27", 1);
  const distributionId = "d1d1d1d1-7000-11eb-b32a-33b8a8a487a6";
  console.log("Loading1: ");
  const aliceStore = new InMemorySenderKeyStore();
	const aliceSentDecryptStore = new InMemorySenderKeyStore();
		
  const bobStore = new InMemorySenderKeyStore();  

  const aliceSessionBuilder = new GroupSessionBuilder(aliceStore);
	const aliceSentMessageDecryptorSessionBuilder = new GroupSessionBuilder(aliceSentDecryptStore);

	const bobSessionBuilder = new GroupSessionBuilder(bobStore);

  const aliceGroupCipher = new GroupCipher(aliceStore, aliceAddress);		
	const bobGroupCipher = new GroupCipher(bobStore, aliceAddress);

  const sentAliceDistributionMessage = await
				aliceSessionBuilder.create(aliceAddress, distributionId);

  console.log("skdm: " + b64.encode(sentAliceDistributionMessage.serialize()));
  
  const receivedAliceDistributionMessage =
				new SenderKeyDistributionMessage(sentAliceDistributionMessage.serialize());
  const receivedAliceDistributionMessage2 =
				new SenderKeyDistributionMessage(sentAliceDistributionMessage.serialize());
  
  // process distribution messsage from alice used for decryting message from Alice
	bobSessionBuilder.process(aliceAddress, receivedAliceDistributionMessage);

  const bytes = new TextEncoder().encode(alicePlainGroupMessage);
  const ciphertextFromAlice = await 
				aliceGroupCipher.encrypt(distributionId, bytes);

	console.log("Encrypted: " + b64.encode(ciphertextFromAlice.serialize()));
  setAliceEncryptedGroupMessage(b64.encode(ciphertextFromAlice.serialize()));
  
  const plaintextFromAlice = await bobGroupCipher.decrypt(ciphertextFromAlice.serialize());
  const text = new TextDecoder().decode(plaintextFromAlice);
	console.log("Decrypted: " + text);
  setAliceDecryptedGroupMessage(text);

  // Create decryptor for Alice's sent messages
	aliceSentMessageDecryptorSessionBuilder.process(aliceAddress, receivedAliceDistributionMessage2);
	// Alice decrypt it's own/sent messages
	const aliceSentMessagesGroupCipher = new GroupCipher(aliceSentDecryptStore, aliceAddress);
	const plaintextFromAliceSentMessage =
		        await aliceSentMessagesGroupCipher.decrypt(ciphertextFromAlice.serialize());
  
  const text2 = new TextDecoder().decode(plaintextFromAliceSentMessage);
	console.log("Decrypted Alice own sent message: " + text2);

  setOutput("Success");
}

useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        await initWasm(); // THIS IS REQUIRED
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
      <h2>LibSignal WASM Group Message Demo</h2>

      <div style={{ marginTop: 12 }}>
        <label>Alice message to Group (Plain Text):</label>
        <br />
        <textarea
          rows={3}
          cols={80}
          value={alicePlainGroupMessage}
          onChange={(e) => setAlicePlainGroupMessage(e.target.value)}
          placeholder="Raw text"
        />
      </div>

      <div style={{ marginTop: 8 }}>
        <label>Alice encrypted message to Group (base64):</label>
        <br />
        <textarea
          rows={3}
          cols={80}
          value={aliceEncryptedGroupMessage}
          onChange={(e) => setAliceEncryptedGroupMessage(e.target.value)}
          placeholder="Raw encrypted base64 format"
        />
      </div>
    
      <div style={{ marginTop: 8 }}>
        <label>Decrypted Group message:</label>
        <br />
        <textarea
          rows={3}
          cols={80}
          value={aliceDecryptedGroupMessage}
          onChange={(e) => setAliceDecryptedGroupMessage(e.target.value)}
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
