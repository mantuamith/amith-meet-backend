import SignalDemo from "./SignalDemo";

function App() {
  return (
    <div>
      <SignalDemo />
    </div>
  );
}

export default App;


// src/App.tsx
/*
import React, { useEffect, useState } from "react";

import { createUser } from "./signal/users";
import {
  createPreKeyBundle,
  initSession,
  encryptMessage,
  decryptMessage
} from "./signal/crypto";
import { PrivateKey } from "@signalapp/libsignal-client";


export default function App() {
  const [alice, setAlice] = useState<any>(null);
  const [bob, setBob] = useState<any>(null);
  const [output, setOutput] = useState("");

  useEffect(() => {
    //PrivateKey.generate()(() => console.log("WASM OK"));

    (async () => {
      const A = await createUser("alice");
      const B = await createUser("bob");

      // Bob publishes a bundle
      const bobBundle = await createPreKeyBundle(B);

      // Alice initializes session to Bob
      await initSession(A, B, bobBundle);

      setAlice(A);
      setBob(B);
    })();
  }, []);

  const runDemo = async () => {
    const encrypted = await encryptMessage(alice, bob, "Hello Bob!");
    const decrypted = await decryptMessage(bob, alice, encrypted);

    setOutput(`Encrypted: ${encrypted.serialize()}\nDecrypted: ${decrypted}`);
  };

  return (
    <div style={{ padding: 20 }}>
      <h1>Signal Protocol – React Demo</h1>

      <button disabled={!alice || !bob} onClick={runDemo}>
        Run Encryption Test
      </button>

      <pre>{output}</pre>
    </div>
  );
}
*/