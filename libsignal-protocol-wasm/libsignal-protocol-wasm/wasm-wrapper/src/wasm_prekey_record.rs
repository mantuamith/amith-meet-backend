use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::collections::HashMap;

use wasm_bindgen::JsValue;

use libsignal_protocol::{PreKeyRecord, PreKeyId, PublicKey, PrivateKey};
use libsignal_core::curve::KeyPair;

// =======================================================
// Global store: PreKeyId (u32) → PreKeyRecord
// =======================================================

static PREKEY_RECORDS: Lazy<Mutex<HashMap<u32, PreKeyRecord>>> =
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

fn insert_prekey_record(id: u32, record: PreKeyRecord) -> Result<(), JsValue> {
    let mut map = PREKEY_RECORDS.lock().unwrap();

    if map.contains_key(&id) {
        return Err(JsValue::from_str("PreKeyRecord with this id already exists"));
    }

    map.insert(id, record);
    Ok(())
}

fn with_prekey_record<F, R>(id: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PreKeyRecord) -> Result<R, JsValue>,
{
    let map = PREKEY_RECORDS.lock().unwrap();

    let record = map
        .get(&id)
        .ok_or_else(|| JsValue::from_str("Unknown PreKeyRecord id"))?;

    f(record)
}

// =======================================================
// WASM exports
// =======================================================

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_new(
    id: u32,
    pub_ptr: u32,
    priv_ptr: u32,
) -> Result<u32, JsValue> {
    // Borrow public key safely
    crate::wasm_ec_public_key::with_public_key(pub_ptr, |public_key| {
        // Borrow private key safely
        crate::wasm_ec_private_key::with_private_key(priv_ptr, |private_key| {
            // Rebuild KeyPair (Signal-required)
            // Serialize INSIDE closures so keys never escape
            let keypair = KeyPair::from_public_and_private(
                &public_key.serialize(),
                &private_key.serialize(),
            )
            .map_err(signal_err)?;

            let record = PreKeyRecord::new(
                PreKeyId::from(id),
                &keypair,
            );

            insert_prekey_record(id, record);

            Ok(id) // return ID as record handle
        })
    })
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_deserialize(bytes: &[u8]) -> Result<u32, JsValue> {
    let record =
        PreKeyRecord::deserialize(bytes)
            .map_err(|_| JsValue::from_str("Invalid PreKeyRecord encoding"))?;

    let id: u32 = record
        .id()
        .map_err(signal_err)?
        .into();

    insert_prekey_record(id, record)?;
    Ok(id)
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_get_id(id: u32) -> Result<u32, JsValue> {
    with_prekey_record(id, |r| {
        r.id()
            .map(|pid| pid.into())
            .map_err(signal_err)
    })
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_get_serialized(id: u32) -> Result<Uint8Array, JsValue> {
    with_prekey_record(id, |r| {
        Ok(Uint8Array::from(
            r.serialize()
                .map_err(signal_err)?
                .as_slice(),
        ))
    })
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_get_public_key(id: u32) -> Result<u32, JsValue> {
    with_prekey_record(id, |r| {
        let public_key: PublicKey = r
            .public_key()
            .map_err(signal_err)?;

        Ok(crate::wasm_ec_public_key::store_public_key(public_key))
    })
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_get_private_key(id: u32) -> Result<u32, JsValue> {
    with_prekey_record(id, |r| {
        let private_key: PrivateKey = r
            .private_key()
            .map_err(signal_err)?;

        Ok(crate::wasm_ec_private_key::store_key(private_key))
    })
}

#[wasm_bindgen(js_namespace = preKeyRecord)]
pub fn prekeyrecord_destroy(id: u32) {
    PREKEY_RECORDS.lock().unwrap().remove(&id);
}
