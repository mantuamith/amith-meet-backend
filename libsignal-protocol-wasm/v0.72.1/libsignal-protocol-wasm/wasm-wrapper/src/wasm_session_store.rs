use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;
use wasm_bindgen::JsValue;

use once_cell::sync::Lazy;

use std::sync::Mutex;
use std::cmp::Ordering;
use crate::handle_store::{HandleStore};
use libsignal_core::address::ProtocolAddress;
use libsignal_protocol::{SessionRecord}; // adjust path if necessary
use libsignal_protocol::error::SignalProtocolError;
use libsignal_protocol::error::Result as ProtocolResult;

pub type SessionStoreMap = HandleStore<String, SessionRecord>;

static SESSION_STORES: Lazy<Mutex<Vec<Option<Box<SessionStoreMap>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

/// Store → return handle
fn save_session_store(pk: SessionStoreMap) -> u32 {
    let mut table = SESSION_STORES.lock().unwrap();
    let boxed = Box::new(pk);

    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32; // Return (index + 1)
        }
    }

    table.push(Some(boxed));
    table.len() as u32 // Return (index + 1)
}

pub fn with_session_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&SessionStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid Session Store pointer"));
    }

    let table = SESSION_STORES.lock().unwrap();

    let key = table
        .get((ptr - 1) as usize)
        .and_then(|slot| slot.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid Session Store pointer"))?;

    // Borrow happens ONLY here
    f(key)
}

pub fn with_session_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut SessionStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid Session Store pointer"));
    }

    let mut table = SESSION_STORES.lock().unwrap();

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|slot| slot.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid Session Store pointer"))?;

    f(store)
}

fn remove_key(ptr: u32) {    
    if ptr == 0 {
        return;
    } 

    let mut table = SESSION_STORES.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

pub fn store_session(
    store_handle: u32,
    address: &ProtocolAddress,
    record: &SessionRecord,
) -> Result<u32, JsValue> {
    // Build a stable key (string is safest across WASM boundary)
    let session_key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    // Create or retrieve SessionStore
    let store_handle = if store_handle == 0 {
        // Create a new store
        let mut store: HandleStore<String, SessionRecord> = HandleStore::new();
        store.insert(session_key.clone(), record.clone());

        save_session_store(store)
    } else {
        // Mutate existing store
        with_session_store_mut(store_handle, |store| {
            store.insert(session_key.clone(), record.clone());
            Ok(())
        });

        store_handle
    };

    // Return both handles
    Ok(store_handle)
}

pub fn load_session(
    store_handle: u32,
    address: &ProtocolAddress,
) -> ProtocolResult<Option<SessionRecord>> {
    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    with_session_store(store_handle, |store| {
        match store.get(&key) {
            Some(record) => {
                // IMPORTANT: return a deep copy
                Ok(Some(record.clone()))
            }
            None => Ok(None),
        }
    })
    .map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "load_session failed: {:?}",
            e
        ))
    })
}

/// Create a new empty SessionStore and return its handle
#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_create_session_store() -> u32 {
    let store: SessionStoreMap = SessionStoreMap::new();
    save_session_store(store)
}

#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_load_session(
    store_handle: u32,
    addr_handle: u32,
) -> Result<Option<Uint8Array>, JsValue> {
    let address =
        crate::wasm_protocol_address::get_protocol_address_clone(addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    with_session_store(store_handle, |store| {
        match store.get(&key) {
            Some(record) => {
                let bytes = record
                    .serialize()
                    .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

                Ok(Some(Uint8Array::from(bytes.as_slice())))
            }
            None => Ok(None),
        }
    })
}

#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_load_existing_sessions(
    store_handle: u32,
    addr_handles: Vec<u32>,
) -> Result<Vec<Uint8Array>, JsValue> {
    with_session_store(store_handle, |store| {
        let mut result = Vec::with_capacity(addr_handles.len());

        for addr_handle in addr_handles {
            let address =
                crate::wasm_protocol_address::get_protocol_address_clone(addr_handle)
                    .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

            let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

            let record = store
                .get(&key)
                .ok_or_else(|| JsValue::from_str("NoSessionException"))?;

            let bytes = record
                .serialize()
                .map_err(|e| JsValue::from_str(&format!("SessionRecord serialize failed: {:?}", e)))?;

            result.push(Uint8Array::from(bytes.as_slice()));
        }

        Ok(result)
    })
}

#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_store_session_record(
    store_handle: u32,
    addr_handle: u32,
    record_bytes: Uint8Array,
) -> Result<(), JsValue> {
    let address = crate::wasm_protocol_address::get_protocol_address_clone(addr_handle)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    let record = SessionRecord::deserialize(&record_bytes.to_vec())
        .map_err(|e| JsValue::from_str(&format!("Invalid SessionRecord: {:?}", e)))?;

    with_session_store_mut(store_handle, |store| {
        store.insert(key, record);
        Ok(())
    })
}

#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_contains_session(
    store_handle: u32,
    addr_handle: u32,
) -> Result<bool, JsValue> {
    let address = crate::wasm_protocol_address::get_protocol_address_clone(addr_handle)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    with_session_store(store_handle, |store| Ok(store.contains(&key)))
}

#[wasm_bindgen(js_namespace = sessionStore)]
pub fn sessionstore_delete_session(
    store_handle: u32,
    addr_handle: u32,
) -> Result<(), JsValue> {
    let address =
        crate::wasm_protocol_address::get_protocol_address_clone(addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    with_session_store_mut(store_handle, |store| {
        store.remove(&key);
        Ok(())
    })
}

