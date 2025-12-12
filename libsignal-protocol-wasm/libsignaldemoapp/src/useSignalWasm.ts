// src/useSignalWasm.ts
import { useEffect, useState } from "react";
import initWasm, {
  init_panic_hook,
  pqxdh_initiate as wasm_pqxdh_initiate,
  pqxdh_receive as wasm_pqxdh_receive,
  kyber_encapsulate as wasm_kyber_encapsulate,
  kyber_decapsulate as wasm_kyber_decapsulate,
  // raw helpers from WASM (ensure these are exported by your Rust)
  x25519_pub_from_priv as wasm_x25519_pub_from_priv,
  x25519_dh as wasm_x25519_dh,
} from "libsignal_wasm_pqxdh";

import { parseWasmValue } from "./utils/parseWasmValue";

export function useSignalWasm() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    async function load() {
      await initWasm();
      init_panic_hook();
      setReady(true);
    }
    load();
  }, []);

  const pqxdh_initiate = (...args: any[]): any => {
    const raw = (wasm_pqxdh_initiate as any)(...args);
    return parseWasmValue(raw);
  };

  const pqxdh_receive = (...args: any[]): any => {
    const raw = (wasm_pqxdh_receive as any)(...args);
    return parseWasmValue(raw);
  };

  const kyber_encapsulate = (...args: any[]): any => {
    const raw = (wasm_kyber_encapsulate as any)(...args);
    return parseWasmValue(raw);
  };

  const kyber_decapsulate = (...args: any[]): any => {
    const raw = (wasm_kyber_decapsulate as any)(...args);
    return parseWasmValue(raw);
  };

  // low-level helpers: return raw Uint8Array or throw
  const x25519_pub_from_priv = (priv_b64: string): Uint8Array => {
    console.log("JS sending private key:", priv_b64);

    // MUST convert base64 → raw bytes BEFORE calling WASM
    const privBytes = base64ToBytes(priv_b64);

    const raw = wasm_x25519_pub_from_priv(privBytes);

    // wasm_bindgen returns Uint8Array directly — no need to parse JSON
    if (raw instanceof Uint8Array) return raw;

    // fallback (if your wrapper still JSON-encodes)
    const parsed = parseWasmValue(raw);
    if (typeof parsed === "string") return base64ToBytes(parsed);
    if (parsed instanceof Uint8Array) return parsed;

    throw new Error("Unexpected response from wasm");
  };
 
  function base64ToBytes(b64: string): Uint8Array {
    const bin = atob(b64);
    const u8 = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
    return u8;
  }

  const x25519_dh = (priv_b64: string, pub_b64: string): Uint8Array => {
    const raw = (wasm_x25519_dh as any)(priv_b64, pub_b64);
    const parsed = parseWasmValue(raw);
    if (parsed instanceof Uint8Array) return parsed;
    if (typeof parsed === "string") {
      const str = atob(parsed);
      const arr = new Uint8Array(str.length);
      for (let i = 0; i < str.length; i++) arr[i] = str.charCodeAt(i);
      return arr;
    }
    if (parsed && parsed.buffer) return new Uint8Array(parsed.buffer);
    throw new Error("Unexpected response from x25519_dh_wasm");
  };

// return the function along with existing exports
return {
  ready,
  pqxdh_initiate,
  pqxdh_receive,
  x25519_pub_from_priv
};
}
