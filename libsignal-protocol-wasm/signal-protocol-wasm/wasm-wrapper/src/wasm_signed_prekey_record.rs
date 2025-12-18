use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::collections::HashMap;

use wasm_bindgen::JsValue;

use libsignal_protocol::{
    SignedPreKeyRecord,
    SignedPreKeyId,
    GenericSignedPreKey, // ✅ REQUIRED
};

use libsignal_core::curve::{KeyPair, PublicKey, PrivateKey};

// =======================================================
// Global store: SignedPreKeyId (u32) → SignedPreKeyRecord
// =======================================================

static SIGNED_PREKEY_RECORDS: Lazy<Mutex<HashMap<u32, SignedPreKeyRecord>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

// =======================================================
// Error helper
// =======================================================

fn signal_err(e: impl core::fmt::Display) -> JsValue {
    JsValue::from_str(&e.to_string())
}

// =======================================================
// Internal helpers
// =======================================================

fn insert_signed_prekey(id: u32, record: SignedPreKeyRecord) -> Result<(), JsValue> {
    let mut map = SIGNED_PREKEY_RECORDS.lock().unwrap();

    if map.contains_key(&id) {
        return Err(JsValue::from_str(
            "SignedPreKeyRecord with this id already exists",
        ));
    }

    map.insert(id, record);
    Ok(())
}

fn with_signed_prekey<F, R>(id: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&SignedPreKeyRecord) -> Result<R, JsValue>,
{
    let map = SIGNED_PREKEY_RECORDS.lock().unwrap();

    let record = map
        .get(&id)
        .ok_or_else(|| JsValue::from_str("Unknown SignedPreKeyRecord id"))?;

    f(record)
}

// =======================================================
// WASM exports
// =======================================================

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_new(
    id: u32,
    timestamp: u64,
    pub_key_ptr: u32,
    priv_key_ptr: u32,
    signature: Uint8Array,
) -> Result<u32, JsValue> {
    let private_key =
        crate::wasm_ec_private_key::get_private_key_clone(priv_key_ptr)
            .ok_or_else(|| JsValue::from_str("Invalid private key handle"))?;

    let public_key =
        crate::wasm_ec_public_key::get_public_key_clone(pub_key_ptr)?;

    let keypair = KeyPair::from_public_and_private(
        &public_key.serialize(),
        &private_key.serialize(),
    )
    .map_err(signal_err)?;

    let record = SignedPreKeyRecord::new(
        SignedPreKeyId::from(id),
        libsignal_protocol::Timestamp::from_epoch_millis(timestamp),
        &keypair,
        &signature.to_vec(),
    );

    insert_signed_prekey(id, record)?;
    Ok(id)
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_deserialize(data: Uint8Array) -> Result<u32, JsValue> {
    let record = SignedPreKeyRecord::deserialize(&data.to_vec())
        .map_err(|_| JsValue::from_str("Invalid SignedPreKeyRecord encoding"))?;

    let id: u32 = record
        .id()
        .map_err(signal_err)?
        .into();

    insert_signed_prekey(id, record)?;
    Ok(id)
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_get_id(id: u32) -> Result<u32, JsValue> {
    with_signed_prekey(id, |r| {
        r.id()
            .map(|pid| pid.into())
            .map_err(signal_err)
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_get_timestamp(id: u32) -> Result<u64, JsValue> {
    with_signed_prekey(id, |r| {
        r.timestamp()
            .map(|ts| ts.epoch_millis())
            .map_err(signal_err)
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_get_public_key(id: u32) -> Result<u32, JsValue> {
    with_signed_prekey(id, |r| {
        let public_key: PublicKey =
            r.public_key().map_err(signal_err)?;

        Ok(crate::wasm_ec_public_key::store_public_key(public_key))
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_get_private_key(id: u32) -> Result<u32, JsValue> {
    with_signed_prekey(id, |r| {
        let private_key: PrivateKey =
            r.private_key().map_err(signal_err)?;

        Ok(crate::wasm_ec_private_key::store_key(private_key))
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_get_signature(id: u32) -> Result<Uint8Array, JsValue> {
    with_signed_prekey(id, |r| {
        Ok(Uint8Array::from(
            r.signature()
                .map_err(signal_err)?
                .as_slice(),
        ))
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_serialize(id: u32) -> Result<Uint8Array, JsValue> {
    with_signed_prekey(id, |r| {
        Ok(Uint8Array::from(
            r.serialize()
                .map_err(signal_err)?
                .as_slice(),
        ))
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyRecord)]
pub fn signed_prekey_destroy(id: u32) {
    SIGNED_PREKEY_RECORDS.lock().unwrap().remove(&id);
}
