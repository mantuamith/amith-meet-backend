use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use crate::handle_store::HandleStore;
use libsignal_protocol::PreKeyRecord;
use libsignal_protocol::error::SignalProtocolError;
use libsignal_protocol::error::Result as ProtocolResult;

pub type PreKeyStoreMap = HandleStore<u32, PreKeyRecord>;

static PREKEY_STORES: Lazy<Mutex<Vec<Option<Box<PreKeyStoreMap>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

/// Save store → return handle
fn save_prekey_store(store: PreKeyStoreMap) -> u32 {
    let mut table = PREKEY_STORES.lock().unwrap();
    let boxed = Box::new(store);

    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32;
        }
    }

    table.push(Some(boxed));
    table.len() as u32
}

pub fn with_prekey_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PreKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid PreKeyStore pointer"));
    }

    let table = PREKEY_STORES.lock().unwrap();

    let store = table
        .get((ptr - 1) as usize)
        .and_then(|s| s.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid PreKeyStore pointer"))?;

    f(store)
}

pub fn with_prekey_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut PreKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid PreKeyStore pointer"));
    }

    let mut table = PREKEY_STORES.lock().unwrap();

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|s| s.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid PreKeyStore pointer"))?;

    f(store)
}


#[wasm_bindgen(js_namespace = preKeyStore)]
pub fn prekeystore_create() -> u32 {
    let store: PreKeyStoreMap = PreKeyStoreMap::new();
    save_prekey_store(store)
}

#[wasm_bindgen(js_namespace = preKeyStore)]
pub fn prekeystore_load_prekey(
    store_handle: u32,
    prekey_id: u32,
) -> Result<Uint8Array, JsValue> {
    with_prekey_store(store_handle, |store| {
        let record = store
            .get(&prekey_id)
            .ok_or_else(|| JsValue::from_str("No such prekeyrecord!"))?;

        let bytes = record
            .serialize()
            .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

        Ok(Uint8Array::from(bytes.as_slice()))
    })
}

#[wasm_bindgen(js_namespace = preKeyStore)]
pub fn prekeystore_store_prekey(
    store_handle: u32,
    prekey_id: u32,
    record_bytes: Uint8Array,
) -> Result<(), JsValue> {
    let record = PreKeyRecord::deserialize(&record_bytes.to_vec())
        .map_err(|e| JsValue::from_str(&format!("Invalid PreKeyRecord: {:?}", e)))?;

    with_prekey_store_mut(store_handle, |store| {
        store.insert(prekey_id, record);
        Ok(())
    })
}

#[wasm_bindgen(js_namespace = preKeyStore)]
pub fn prekeystore_contains_prekey(
    store_handle: u32,
    prekey_id: u32,
) -> Result<bool, JsValue> {
    with_prekey_store(store_handle, |store| {
        Ok(store.contains(&prekey_id))
    })
}

#[wasm_bindgen(js_namespace = preKeyStore)]
pub fn prekeystore_remove_prekey(
    store_handle: u32,
    prekey_id: u32,
) -> Result<(), JsValue> {
    with_prekey_store_mut(store_handle, |store| {
        store.remove(&prekey_id);
        Ok(())
    })
}

