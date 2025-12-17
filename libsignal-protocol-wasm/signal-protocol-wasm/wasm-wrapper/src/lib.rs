// ------------------------
// IMPORTS
// ------------------------
use wasm_bindgen::prelude::*;
use serde::{Deserialize, Serialize};
use base64::{engine::general_purpose, Engine as _};

use hkdf::Hkdf;
use sha2::Sha256;

use js_sys::{Object, Reflect, Uint8Array};

use web_sys::console;


use base64::engine::general_purpose::STANDARD as B64;
use serde_wasm_bindgen;
use serde_json::json;

// Import libsignal-protocol bindings
use libsignal_protocol::identity_key::wasm_identity::{
    x25519_dh_wasm,
    x25519_pub_from_priv_wasm,
};
use libsignal_protocol::kem::wasm_helpers::{
    kyber_encapsulate_wasm_from_bytes,
    kyber_decapsulate_wasm_from_bytes,
};

mod protocol;
mod wasm_ec_public_key;
mod wasm_ec_private_key;
mod wasm_protocol_address;
mod wasm_identity_key_pair;
mod wasm_kem_secret_key;
mod wasm_kem_public_key;
mod wasm_kem_key_pair;
mod wasm_pre_key_bundle;
mod wasm_session_builder;
mod wasm_session_record;
mod wasm_pre_key_signal_message;
mod handle_message_store;
mod wasm_signal_message;
mod utils;
mod wasm_session_cipher;
mod wasm_ciphertext_message;
mod handle_store;
mod handle_identity_store;
mod wasm_session_store;
mod wasm_identity_key_store;

// Re-export each module's wasm_bindgen API
pub use protocol::*;
pub use wasm_ec_public_key::*;
pub use wasm_ec_private_key::*;
pub use wasm_protocol_address::*;
pub use wasm_identity_key_pair::*;
pub use wasm_kem_secret_key::*;
pub use wasm_kem_public_key::*;
pub use wasm_kem_key_pair::*;
pub use wasm_pre_key_bundle::*;
pub use wasm_session_record::*;
pub use wasm_session_builder::*;
pub use wasm_pre_key_signal_message::*;
pub use handle_message_store::*;
pub use wasm_signal_message::*;
pub use utils::*;
pub use wasm_session_cipher::*;
pub use wasm_ciphertext_message::*;
pub use handle_store::*;
pub use wasm_session_store::*;
pub use wasm_identity_key_store::*;

// ------------------------
// ERROR HANDLING
// ------------------------
#[derive(Serialize)]
struct ErrorJson {
    ok: bool,
    error: String,
}

fn json_error(msg: impl Into<String>) -> JsValue {
    JsValue::from_str(&serde_json::to_string(&ErrorJson {
        ok: false,
        error: msg.into(),
    }).unwrap())
}


// ------------------------
// PANIC HOOK
// ------------------------
#[wasm_bindgen]
pub fn init_panic_hook() {
    console_error_panic_hook::set_once();
}


// ------------------------
// BASE64 HELPERS
// ------------------------
fn decode_b64(s: &str) -> Result<Vec<u8>, String> {
    general_purpose::STANDARD.decode(s)
        .map_err(|_| format!("Invalid base64 input: {}", s))
}

fn encode_b64(b: &[u8]) -> String {
    general_purpose::STANDARD.encode(b)
}


// ------------------------
// PQXDH RESULT STRUCTURES
// ------------------------
#[derive(Serialize, Deserialize)]
struct InitiateResult {
    ok: bool,
    kyber_ciphertext_b64: String,
    eph_x25519_pub_b64: String,
    shared_root_b64: String,
}

#[derive(Serialize, Deserialize)]
struct ReceiveResult {
    ok: bool,
    shared_root_b64: String,
}


// ------------------------
// PQXDH INITIATE (Alice)
// ------------------------

#[wasm_bindgen]
pub fn pqxdh_initiate(
    sender_identity_priv_b64: &str,
    sender_eph_priv_b64: &str,
    receiver_identity_pub_b64: &str,
    receiver_signedprekey_pub_b64: &str,
    receiver_kyber_pub_b64: &str,
    optional_receiver_onetime_pub_b64: &str,
) -> JsValue {
    // ---- decode all inputs ----
    let s_id_priv = match decode_b64(sender_identity_priv_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let s_eph_priv = match decode_b64(sender_eph_priv_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let r_id_pub = match decode_b64(receiver_identity_pub_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let r_spk_pub = match decode_b64(receiver_signedprekey_pub_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let r_ky_pub = match decode_b64(receiver_kyber_pub_b64) { Ok(v) => v, Err(e) => return json_error(e) };

    let r_opk_pub = if optional_receiver_onetime_pub_b64.is_empty() {
        None
    } else {
        Some(match decode_b64(optional_receiver_onetime_pub_b64) {
            Ok(v) => v,
            Err(e) => return json_error(e),
        })
    };

    // ---- DH1 ----
    let dh1 = match x25519_dh_wasm(&s_id_priv, &r_id_pub) {
        Ok(v) => v,
        Err(_) => return json_error("DH1 failed: Invalid identity pubkey"),
    };

    // ---- DH2 ----
    let dh2 = match x25519_dh_wasm(&s_eph_priv, &r_spk_pub) {
        Ok(v) => v,
        Err(_) => return json_error("DH2 failed: Invalid signed prekey pub"),
    };

    // ---- DH3 ----
    let dh3 = match x25519_dh_wasm(&s_id_priv, &r_spk_pub) {
        Ok(v) => v,
        Err(_) => return json_error("DH3 failed: Invalid signed prekey pub"),
    };

    // ---- DH4 ----
    let dh4 = match x25519_dh_wasm(&s_eph_priv, &r_id_pub) {
        Ok(v) => v,
        Err(_) => return json_error("DH4 failed: Invalid identity pubkey"),
    };

    // ---- Kyber encapsulate ----
    let (ct, shared2) = match kyber_encapsulate_wasm_from_bytes(&r_ky_pub) {
        Ok(v) => v,
        Err(e) => return json_error(format!("Kyber encapsulation failed: {}", e)),
    };

    // ---- Collect IKM ----
    let mut ikm = Vec::new();
    ikm.extend_from_slice(&dh1);
    ikm.extend_from_slice(&dh2);
    ikm.extend_from_slice(&dh3);
    ikm.extend_from_slice(&dh4);

    // optional OPK
    if let Some(opk_pub) = r_opk_pub {
        match x25519_dh_wasm(&s_eph_priv, &opk_pub) {
            Ok(v) => ikm.extend_from_slice(&v),
            Err(_) => return json_error("DH(OPK) failed"),
        }
    }

    // PQ shared secret
    ikm.extend_from_slice(&shared2);

    // ---- HKDF derive root ----
    let hk = Hkdf::<Sha256>::new(None, &ikm);
    let mut okm = [0u8; 32];

    if hk.expand(b"signal-pqxdh-v1", &mut okm).is_err() {
        return json_error("HKDF expansion failed");
    }

    // ---- compute ephemeral pub ----
    let eph_pub = match x25519_pub_from_priv_wasm(&s_eph_priv) {
        Ok(v) => v,
        Err(_) => return json_error("Unable to compute ephemeral public key"),
    };

    // ---- success result ----
    let res = InitiateResult {
        ok: true,
        kyber_ciphertext_b64: encode_b64(&ct),
        eph_x25519_pub_b64: encode_b64(&eph_pub),
        shared_root_b64: encode_b64(&okm),
    };

    JsValue::from_str(&serde_json::to_string(&res).unwrap())
}


// ------------------------
// PQXDH RECEIVE (Bob)
// ------------------------
#[wasm_bindgen]
pub fn pqxdh_receive(
    receiver_identity_priv_b64: &str,
    receiver_signedprekey_priv_b64: &str,
    receiver_kyber_priv_b64: &str,
    kyber_ciphertext_b64: &str,
    sender_identity_pub_b64: &str,
    sender_eph_pub_b64: &str,
) -> JsValue {

    // decode
    let r_id_priv = match decode_b64(receiver_identity_priv_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let r_spk_priv = match decode_b64(receiver_signedprekey_priv_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let r_ky_priv = match decode_b64(receiver_kyber_priv_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let ct = match decode_b64(kyber_ciphertext_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let s_id_pub = match decode_b64(sender_identity_pub_b64) { Ok(v) => v, Err(e) => return json_error(e) };
    let s_eph_pub = match decode_b64(sender_eph_pub_b64) { Ok(v) => v, Err(e) => return json_error(e) };

    // DH steps
    let dh1 = match x25519_dh_wasm(&r_id_priv, &s_id_pub) { Ok(v) => v, Err(_) => return json_error("DH1 failed") };
    let dh2 = match x25519_dh_wasm(&r_spk_priv, &s_eph_pub) { Ok(v) => v, Err(_) => return json_error("DH2 failed") };
    let dh3 = match x25519_dh_wasm(&r_spk_priv, &s_id_pub) { Ok(v) => v, Err(_) => return json_error("DH3 failed") };
    let dh4 = match x25519_dh_wasm(&r_id_priv, &s_eph_pub) { Ok(v) => v, Err(_) => return json_error("DH4 failed") };

    // Kyber decapsulate
    let shared2 = match kyber_decapsulate_wasm_from_bytes(&r_ky_priv, &ct) {
        Ok(v) => v,
        Err(e) => return json_error(format!("Kyber decapsulation failed: {}", e)),
    };

    // IKM
    let mut ikm = Vec::new();
    ikm.extend_from_slice(&dh1);
    ikm.extend_from_slice(&dh2);
    ikm.extend_from_slice(&dh3);
    ikm.extend_from_slice(&dh4);
    ikm.extend_from_slice(&shared2);

    // HKDF
    let hk = Hkdf::<Sha256>::new(None, &ikm);
    let mut okm = [0u8; 32];
    if hk.expand(b"signal-pqxdh-v1", &mut okm).is_err() {
        return json_error("HKDF expansion failed");
    }

    // Success
    let res = ReceiveResult {
        ok: true,
        shared_root_b64: encode_b64(&okm),
    };

    JsValue::from_str(&serde_json::to_string(&res).unwrap())
}


// ------------------------
// Kyber Simple Helpers
// ------------------------
#[wasm_bindgen]
pub fn kyber_encapsulate(pubkey: &[u8]) -> Result<JsValue, JsValue> {
    let (ct, ss) = kyber_encapsulate_wasm_from_bytes(pubkey)
        .map_err(|e| JsValue::from_str(&e.to_string()))?;

    let obj = Object::new();
    Reflect::set(&obj, &"ciphertext".into(), &Uint8Array::from(&ct[..]))?;
    Reflect::set(&obj, &"shared_secret".into(), &Uint8Array::from(&ss[..]))?;
    Ok(obj.into())
}

#[wasm_bindgen]
pub fn kyber_decapsulate(secret_key: &[u8], ct: &[u8]) -> Result<JsValue, JsValue> {
    let ss = kyber_decapsulate_wasm_from_bytes(secret_key, ct)
        .map_err(|e| JsValue::from_str(&e.to_string()))?;

    Ok(Uint8Array::from(&ss[..]).into())
}

// ------------------------
// RE-EXPORT X25519 HELPERS
// ------------------------
#[wasm_bindgen]
pub fn x25519_pub_from_priv(priv_bytes: &[u8]) -> Result<js_sys::Uint8Array, JsValue> {
    console::log_1(&format!("priv_bytes (base64) = {}", base64::encode(priv_bytes)).into());

    match x25519_pub_from_priv_wasm(priv_bytes) {
        Ok(pk) => Ok(js_sys::Uint8Array::from(pk.as_slice())),
        Err(e) => Err(JsValue::from_str(&format!("pub_from_priv failed: {e}"))),
    }
}

#[wasm_bindgen]
pub fn x25519_dh(priv_bytes: &[u8], pub_bytes: &[u8]) -> Result<js_sys::Uint8Array, JsValue> {
    match x25519_dh_wasm(priv_bytes, pub_bytes) {
        Ok(ss) => Ok(js_sys::Uint8Array::from(ss.as_slice())),
        Err(e) => Err(JsValue::from_str(&format!("x25519_dh failed: {e}"))),
    }
}

#[wasm_bindgen]
pub fn kyber_keygen() -> Result<JsValue, JsValue> {
    // generate pk, sk inside protocol crate
    let (pk, sk) = libsignal_protocol::kem::wasm_helpers::kyber_keygen_in_protocol()
        .map_err(|e| JsValue::from_str(&format!("keygen failed: {}", e)))?;

    // Create a JS object using wasm_bindgen-friendly serialization.
    let result = serde_wasm_bindgen::to_value(&serde_json::json!({
        "pub_b64": B64.encode(pk),
        "priv_b64": B64.encode(sk),
    }))
    .map_err(|e| JsValue::from_str(&format!("serialize failed: {}", e)))?;

    Ok(result)
}

