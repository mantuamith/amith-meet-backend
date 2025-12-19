use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::collections::HashMap;

use wasm_bindgen::JsValue;

use libsignal_protocol::{
    GenericSignedPreKey,
    KyberPreKeyRecord,
    KyberPreKeyId,
    Timestamp,
};

use libsignal_protocol::kem;

// =======================================================
// Global store: KyberPreKeyId (u32) → KyberPreKeyRecord
// =======================================================

static KYBER_PREKEY_RECORDS: Lazy<Mutex<HashMap<u32, KyberPreKeyRecord>>> =
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

fn insert_kyber_prekey(id: u32, record: KyberPreKeyRecord) -> Result<(), JsValue> {
    let mut map = KYBER_PREKEY_RECORDS.lock().unwrap();

    if map.contains_key(&id) {
        return Err(JsValue::from_str(
            "KyberPreKeyRecord with this id already exists",
        ));
    }

    map.insert(id, record);
    Ok(())
}

fn with_kyber_prekey<F, R>(id: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&KyberPreKeyRecord) -> Result<R, JsValue>,
{
    let map = KYBER_PREKEY_RECORDS.lock().unwrap();

    let record = map
        .get(&id)
        .ok_or_else(|| JsValue::from_str("Unknown KyberPreKeyRecord id"))?;

    f(record)
}

// =======================================================
// WASM exports
// =======================================================

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_new(
    id: u32,
    timestamp: u64,
    keypair_ptr: u32,
    signature: Uint8Array,
) -> Result<u32, JsValue> {
    // --- Retrieve Kyber KeyPair safely ---
    let keypair: kem::KeyPair =
        crate::wasm_kem_key_pair::get_kyber_keypair_clone(keypair_ptr)?;

    let record = KyberPreKeyRecord::new(
        KyberPreKeyId::from(id),
        Timestamp::from_epoch_millis(timestamp),
        &keypair,
        &signature.to_vec(),
    );

    insert_kyber_prekey(id, record)?;
    Ok(id)
}


#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_deserialize(data: Uint8Array) -> Result<u32, JsValue> {
    let record =
        KyberPreKeyRecord::deserialize(&data.to_vec())
            .map_err(|_| JsValue::from_str("Invalid KyberPreKeyRecord encoding"))?;

    let id: u32 = record
        .id()
        .map_err(signal_err)?
        .into();

    insert_kyber_prekey(id, record)?;
    Ok(id)
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_get_id(id: u32) -> Result<u32, JsValue> {
    with_kyber_prekey(id, |r| {
        r.id()
            .map(|pid| pid.into())
            .map_err(signal_err)
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_get_timestamp(id: u32) -> Result<u64, JsValue> {
    with_kyber_prekey(id, |r| {
        r.timestamp()
            .map(|ts| ts.epoch_millis())
            .map_err(signal_err)
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_get_keypair(id: u32) -> Result<u32, JsValue> {
    with_kyber_prekey(id, |r| {
        let keypair: kem::KeyPair =
            r.key_pair().map_err(signal_err)?;

        Ok(crate::wasm_kem_key_pair::store_kem_keypair(keypair))
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_get_signature(id: u32) -> Result<Uint8Array, JsValue> {
    with_kyber_prekey(id, |r| {
        Ok(Uint8Array::from(
            r.signature()
                .map_err(signal_err)?
                .as_slice(),
        ))
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_serialize(id: u32) -> Result<Uint8Array, JsValue> {
    with_kyber_prekey(id, |r| {
        Ok(Uint8Array::from(
            r.serialize()
                .map_err(signal_err)?
                .as_slice(),
        ))
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyRecord)]
pub fn kyber_prekey_destroy(id: u32) {
    KYBER_PREKEY_RECORDS.lock().unwrap().remove(&id);
}
