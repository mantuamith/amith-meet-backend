// ---------------------------------------------------------
// protocol.rs — FINAL FIXED VERSION (ChaCha RNG + wasm helpers)
// ---------------------------------------------------------

use wasm_bindgen::prelude::*;
use js_sys::{Reflect, Uint8Array, Object};

use serde_json::json;

use libsignal_protocol::*;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use base64::engine::general_purpose::STANDARD as B64;
use getrandom;

use base64::Engine;

use libsignal_protocol::identity_key::wasm_identity::{
    x25519_dh_wasm,
    x25519_pub_from_priv_wasm,
};

use libsignal_protocol::kem::wasm_helpers::{
    kyber_encapsulate_wasm_from_bytes,
    kyber_decapsulate_wasm_from_bytes,
};

use wasm_bindgen::JsValue;


use libsignal_protocol::{
    PreKeyBundle, PublicKey, IdentityKey,
    PreKeyId, KyberPreKeyId,
    kem::PublicKey as KyberPublicKey,
}; 

use libsignal_core::address::InvalidDeviceId;

// ---------------------------------------------
// Local js_err helper (required)
// ---------------------------------------------
fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ---------------------------------------------
// Secure RNG for WASM
// ---------------------------------------------
fn new_rng() -> Result<ChaCha20Rng, JsValue> {
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| js_err(format!("Random seed error: {e}")))?;
    Ok(ChaCha20Rng::from_seed(seed))
}

// ---------------------------------------------------------
// Identity key generation
// ---------------------------------------------------------
#[wasm_bindgen(js_namespace = protocol)]
pub fn identity_key_generate() -> Result<JsValue, JsValue> {
    let mut rng = new_rng()?;
    let keypair = KeyPair::generate(&mut rng);

    let obj = Object::new();
    Reflect::set(&obj, &"public_key".into(),
        &Uint8Array::from(&keypair.public_key.serialize()[..]))?;
    Reflect::set(&obj, &"private_key".into(),
        &Uint8Array::from(&keypair.private_key.serialize()[..]))?;

    Ok(obj.into())
}

// ---------------------------------------------------------
// Ephemeral key helpers (sender ephemeral key generation)
// ---------------------------------------------------------

/// Generate an ephemeral X25519 identity keypair (KeyPair) and return
/// `{ public_key: Uint8Array, private_key: Uint8Array }`.
///
/// The private key is intended to be used **only** for the single handshake.

#[wasm_bindgen(js_namespace = protocol)]
pub fn ephemeral_generate() -> Result<JsValue, JsValue> {
    let mut rng = new_rng()?;
    let pair = KeyPair::generate(&mut rng);

    let obj = Object::new();
    Reflect::set(&obj, &"public_key".into(),
        &Uint8Array::from(&pair.public_key.serialize()[..]))?;
    Reflect::set(&obj, &"private_key".into(),
        &Uint8Array::from(&pair.private_key.serialize()[..]))?;

    Ok(obj.into())
}

/// Derive an X25519 public key from a private key (useful if you only
/// serialized/transferred the private key and need the public for the wire).
#[wasm_bindgen(js_namespace = protocol)]
pub fn ephemeral_pub_from_priv(priv_bytes: &[u8]) -> Result<Uint8Array, JsValue> {
    // x25519_pub_from_priv_wasm returns a Vec<u8> / Box<[u8]>; use as_ref()
    let pk = x25519_pub_from_priv_wasm(priv_bytes)
        .map_err(|e| js_err(format!("ephemeral pub_from_priv failed: {e}")))?;

    Ok(Uint8Array::from(pk.as_ref()))
}

// ---------------------------------------------------------
// PreKey
// ---------------------------------------------------------
#[wasm_bindgen(js_namespace = protocol)]
pub fn prekey_generate(id: u32) -> Result<JsValue, JsValue> {
    let mut rng = new_rng()?;
    let pair = KeyPair::generate(&mut rng);

    let rec_bytes = PreKeyRecord::new(id.into(), &pair)
        .serialize()
        .map_err(|e| js_err(format!("prekey serialize: {e}")))?;

    let obj = Object::new();
    Reflect::set(&obj, &"id".into(), &JsValue::from_f64(id as f64))?;
    Reflect::set(&obj, &"public_key".into(),
        &Uint8Array::from(&pair.public_key.serialize()[..]))?;
    Reflect::set(&obj, &"private_key".into(),
        &Uint8Array::from(&pair.private_key.serialize()[..]))?;
    Reflect::set(&obj, &"record".into(),
        &Uint8Array::from(rec_bytes.as_ref()))?;

    Ok(obj.into())
}

// ---------------------------------------------------------
// Signed PreKey
// ---------------------------------------------------------
#[wasm_bindgen(js_namespace = protocol)]
pub fn signed_prekey_generate(id: u32, identity_priv_bytes: &[u8])
    -> Result<JsValue, JsValue>
{
    let mut rng = new_rng()?;

    let identity_priv = PrivateKey::deserialize(identity_priv_bytes)
        .map_err(|e| js_err(format!("bad identity priv: {:?}", e)))?;

    let pair = KeyPair::generate(&mut rng);

    let sig = identity_priv
        .calculate_signature(&pair.public_key.serialize(), &mut rng)
        .map_err(|e| js_err(format!("signature error: {e}")))?;

    let timestamp = Timestamp::from_epoch_millis(0);

    let rec_bytes = SignedPreKeyRecord::new(id.into(), timestamp, &pair, &sig)
        .serialize()
        .map_err(|e| js_err(format!("signed prekey serialize: {e}")))?;

    let obj = Object::new();
    Reflect::set(&obj, &"id".into(), &JsValue::from_f64(id as f64))?;
    Reflect::set(&obj, &"public_key".into(),
        &Uint8Array::from(&pair.public_key.serialize()[..]))?;
    Reflect::set(&obj, &"private_key".into(),
        &Uint8Array::from(&pair.private_key.serialize()[..]))?;
    Reflect::set(&obj, &"signature".into(),
        &Uint8Array::from(&sig[..]))?;
    Reflect::set(&obj, &"record".into(),
        &Uint8Array::from(rec_bytes.as_ref()))?;

    Ok(obj.into())
}

#[wasm_bindgen(js_namespace = protocol)]
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

#[wasm_bindgen(js_namespace = protocol)]
pub fn prekey_bundle_new_wasm(
    registration_id: u32,
    device_id: u32,
    prekey_id: Option<u32>,
    prekey_pub: Option<Box<[u8]>>,
    signed_prekey_id: u32,
    signed_prekey_pub: &[u8],
    signed_prekey_sig: &[u8],
    identity_key_pub: &[u8],
    kyber_prekey_id: u32,
    kyber_prekey_pub: &[u8],
    kyber_prekey_sig: &[u8],
) -> Result<JsValue, JsValue> {

    let identity_key = IdentityKey::new(
        PublicKey::deserialize(identity_key_pub)
            .map_err(js_err)?
    );

    let signed_prekey = PublicKey::deserialize(signed_prekey_pub)
        .map_err(js_err)?;

    let kyber_prekey =
        KyberPublicKey::deserialize(kyber_prekey_pub).map_err(js_err)?;

    let prekey: Option<(PreKeyId, PublicKey)> = match (prekey_id, prekey_pub) {
        (None, None) => None,
        (Some(id), Some(bytes)) => {
            Some((id.into(), PublicKey::deserialize(&bytes).map_err(js_err)?))
        }
        _ => return Err(js_err("Must supply both or neither of prekey + prekey_id")),
    };

    let device_id = device_id.try_into()
        .map_err(|e: InvalidDeviceId| js_err(e.to_string()))?;

    let kyber_prekey_clone = kyber_prekey.clone();

    let bundle = PreKeyBundle::new(
        registration_id,
        device_id,
        prekey,
        signed_prekey_id.into(),
        signed_prekey,
        signed_prekey_sig.to_vec(),
        kyber_prekey_id.into(),
        kyber_prekey,
        kyber_prekey_sig.to_vec(),
        identity_key,
    ).map_err(js_err)?;

    // Convert to JS object — WASM cannot return PreKeyBundle itself
    // JS object to return *all* bundle components
let obj = js_sys::Object::new();

// status OK
Reflect::set(&obj, &"ok".into(), &JsValue::TRUE)?;

// full serialized PreKeyBundle (used by SessionBuilder)
//Reflect::set(&obj, &"bundle_bytes".into(), &js_buf.into())?;

// identity info
Reflect::set(&obj, &"registration_id".into(), &JsValue::from(registration_id))?;

let device_id_raw: u32 = device_id.into();
Reflect::set(&obj, &"device_id".into(), &JsValue::from(device_id_raw))?;

// Optional EC one-time prekey
if let Some((id, pk)) = &prekey {
    Reflect::set(&obj, &"prekey_id".into(), &JsValue::from(u32::from(*id)))?;
    Reflect::set(
        &obj,
        &"prekey_public".into(),
        &Uint8Array::from(pk.serialize().as_ref()).into(),
    )?;
} else {
    Reflect::set(&obj, &"prekey_id".into(), &JsValue::NULL)?;
    Reflect::set(&obj, &"prekey_public".into(), &JsValue::NULL)?;
}

// signed prekey
Reflect::set(&obj, &"signed_prekey_id".into(), &JsValue::from(signed_prekey_id))?;
Reflect::set(
    &obj,
    &"signed_prekey_public".into(),
    &Uint8Array::from(signed_prekey.serialize().as_ref()).into(),
)?;
Reflect::set(
    &obj,
    &"signed_prekey_signature".into(),
    &Uint8Array::from(signed_prekey_sig).into(),
)?;

// identity key
Reflect::set(
    &obj,
    &"identity_key_public".into(),
    &Uint8Array::from(identity_key.public_key().serialize().as_ref()).into(),
)?;

// Kyber prekey
Reflect::set(&obj, &"kyber_prekey_id".into(), &JsValue::from(kyber_prekey_id))?;
Reflect::set(
    &obj,
    &"kyber_prekey_public".into(),
    &Uint8Array::from(kyber_prekey_clone.serialize().as_ref()).into(),
)?;
Reflect::set(
    &obj,
    &"kyber_prekey_signature".into(),
    &Uint8Array::from(kyber_prekey_sig).into(),
)?;

Ok(obj.into())
}
