use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use crate::handle_store::HandleStore;
use libsignal_protocol::GenericSignedPreKey;
use libsignal_protocol::SignedPreKeyRecord;
use web_sys::console;

pub type SignedPreKeyStoreMap = HandleStore<u32, SignedPreKeyRecord>;

static SIGNED_PREKEY_STORES: Lazy<Mutex<Vec<Option<Box<SignedPreKeyStoreMap>>>>> =
    Lazy::new(|| {
        let mut v = Vec::new();
        v.push(None); // handle 0 = null
        Mutex::new(v)
    });


    fn save_signed_prekey_store(store: SignedPreKeyStoreMap) -> u32 {
    let mut table = SIGNED_PREKEY_STORES.lock().unwrap();
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

pub fn with_signed_prekey_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&SignedPreKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid SignedPreKeyStore pointer"));
    }

    let table = SIGNED_PREKEY_STORES.lock().unwrap();

    let store = table
        .get((ptr - 1) as usize)
        .and_then(|s| s.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid SignedPreKeyStore pointer"))?;

    f(store)
}

pub fn with_signed_prekey_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut SignedPreKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid SignedPreKeyStore pointer"));
    }

    let mut table = SIGNED_PREKEY_STORES.lock().unwrap();

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|s| s.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid SignedPreKeyStore pointer"))?;

    f(store)
}


#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_create() -> u32 {
    let store: SignedPreKeyStoreMap = SignedPreKeyStoreMap::new();
    save_signed_prekey_store(store)
}

#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_load_signed_prekey(
    store_handle: u32,
    id: u32,
) -> Result<Uint8Array, JsValue> {
    with_signed_prekey_store(store_handle, |store| {
        let record = store
            .get(&id)
            .ok_or_else(|| {
                JsValue::from_str(&format!(
                    "No such SignedPreKeyRecord! {}",
                    id
                ))
            })?;

        let bytes = record
            .serialize()
            .map_err(|e| JsValue::from_str(&format!(
                "serialize failed: {:?}", e
            )))?;

        Ok(Uint8Array::from(bytes.as_slice()))
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_load_signed_prekeys(
    store_handle: u32,
) -> Result<Vec<Uint8Array>, JsValue> {
    with_signed_prekey_store(store_handle, |store| {
        let mut result = Vec::new();

        for record in store.values() {
            let bytes = record
                .serialize()
                .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

            result.push(Uint8Array::from(bytes.as_slice()));
        }

        Ok(result)
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_store_signed_prekey(
    store_handle: u32,
    id: u32,
    record_bytes: Uint8Array,
) -> Result<(), JsValue> {
    let record = SignedPreKeyRecord::deserialize(&record_bytes.to_vec())
        .map_err(|e| JsValue::from_str(&format!("Invalid SignedPreKeyRecord: {:?}", e)))?;

    with_signed_prekey_store_mut(store_handle, |store| {
        store.insert(id, record);
        Ok(())
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_contains_signed_prekey(
    store_handle: u32,
    id: u32,
) -> Result<bool, JsValue> {
    with_signed_prekey_store(store_handle, |store| {
        let exists = store.contains(&id);        
        Ok(exists)
    })
}

#[wasm_bindgen(js_namespace = signedPreKeyStore)]
pub fn signedprekeystore_remove_signed_prekey(
    store_handle: u32,
    id: u32,
) -> Result<(), JsValue> {
    with_signed_prekey_store_mut(store_handle, |store| {
        store.remove(&id);
        Ok(())
    })
}

