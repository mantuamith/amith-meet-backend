use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::SenderKeyRecord;
use libsignal_protocol::error::SignalProtocolError;
use libsignal_protocol::error::Result as ProtocolResult;

use libsignal_core::address::ProtocolAddress;

use crate::handle_store::HandleStore;
use crate::wasm_protocol_address::get_protocol_address_clone;
use crate::wasm_sender_key_record::{
    senderkeyrecord_deserialize,
    senderkeyrecord_serialize,
};

use futures::executor::block_on;

// ------------------------------------------------------------
// Types
// ------------------------------------------------------------

/// (sender + distributionId) → SenderKeyRecord
pub type SenderKeyStoreMap = HandleStore<String, SenderKeyRecord>;

// ------------------------------------------------------------
// Global handle table
// ------------------------------------------------------------

pub(crate) static SENDER_KEY_STORES: Lazy<Mutex<Vec<Option<Box<SenderKeyStoreMap>>>>> =
    Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

// ------------------------------------------------------------
// Internal helpers (same pattern as wasm_session_store.rs)
// ------------------------------------------------------------

fn save_sender_key_store(store: SenderKeyStoreMap) -> u32 {
    let mut table = SENDER_KEY_STORES.lock().expect("sender key store table poisoned");
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

fn with_sender_key_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&SenderKeyStoreMap) -> Result<R, JsValue>,
{
    console::log_1(&"[with_sender_key_store] enter".into());

    if ptr == 0 {
        return Err(JsValue::from_str(
            "Invalid SenderKeyStore handle: 0",
        ));
    }

    let table = match SENDER_KEY_STORES.lock() {
        Ok(t) => t,
        Err(_) => {
            console::error_1(&"[with_sender_key_store] mutex poisoned".into());
            return Err(JsValue::from_str(
                "SenderKeyStore mutex poisoned (previous panic)",
            ));
        }
    };

    let index = (ptr - 1) as usize;

    let slot = table.get(index).ok_or_else(|| {
        JsValue::from_str(&format!(
            "SenderKeyStore handle out of range: {}",
            ptr
        ))
    })?;

    let store = slot.as_ref().ok_or_else(|| {
        JsValue::from_str(&format!(
            "SenderKeyStore handle {} was already destroyed",
            ptr
        ))
    })?;

    f(store)
}

pub fn with_sender_key_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut SenderKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid SenderKeyStore handle"));
    }

    let mut table = SENDER_KEY_STORES.lock().expect("sender key store table poisoned");

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|slot| slot.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid SenderKeyStore handle"))?;

    f(store)
}

pub fn with_sender_key_store_mut_blocking<F, R>(
    ptr: u32,
    f: F,
) -> Result<R, JsValue>
where
    F: FnOnce(&mut SenderKeyStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid SenderKeyStore handle"));
    }

    let mut table = SENDER_KEY_STORES
        .lock()
        .expect("sender key store table poisoned");

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|slot| slot.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid SenderKeyStore handle"))?;

    f(store)
}

fn remove_sender_key_store(ptr: u32) {
    if ptr == 0 {
        return;
    }

    let mut table = SENDER_KEY_STORES.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

pub fn load_sender_key(
    store_handle: u32,
    sender: &ProtocolAddress,
    distribution_id: &str,
) -> ProtocolResult<Option<SenderKeyRecord>> {
    let key = make_key(sender, distribution_id);

    console::log_1(
        &format!(
            "[load_sender_key] store_handle={} sender={} device_id={} distribution_id={} key={}",
            store_handle,
            sender.name(),
            u32::from(sender.device_id()),
            distribution_id,
            key
        )
        .into(),
    );

    console::log_1(&format!("[load_sender_key] newxt calling: with_sender_key_store").into());
    let result = with_sender_key_store(store_handle, |store| {
        let found = store.get(&key).is_some();

        console::log_1(
            &format!(
                "[load_sender_key] lookup result: {}",
                if found { "FOUND" } else { "NOT FOUND" }
            )
            .into(),
        );

        Ok(store.get(&key).cloned())
    });

    console::log_1(&format!("[load_sender_key] newxt calling: with_sender_key_store- END").into());
    match &result {
        Ok(Some(_)) => {
            console::log_1(&"[load_sender_key] returning record".into());
        }
        Ok(None) => {
            console::log_1(&"[load_sender_key] returning None".into());
        }
        Err(e) => {
            console::error_1(
                &format!("[load_sender_key] ERROR: {:?}", e).into(),
            );
        }
    }

    result.map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "load_sender_key failed: {:?}",
            e
        ))
    })
}


pub fn store_sender_key(
    store_handle: u32,
    sender: &ProtocolAddress,
    distribution_id: &str,
    record: &SenderKeyRecord,
) -> ProtocolResult<()> {
    let key = make_key(sender, distribution_id);

    console::log_1(
        &format!(
            "[store_sender_key] store_handle={} sender={} device_id={} distribution_id={} key={}",
            store_handle,
            sender.name(),
            u32::from(sender.device_id()),
            distribution_id,
            key
        )
        .into(),
    );

    let result = with_sender_key_store_mut(store_handle, |store| {
        store.insert(key.clone(), record.clone());

        console::log_1(
            &format!(
                "[store_sender_key] inserted record under key={}",
                key
            )
            .into(),
        );

        Ok(())
    });

    if let Err(e) = &result {
        console::error_1(
            &format!("[store_sender_key] ERROR: {:?}", e).into(),
        );
    } else {
        console::log_1(&"[store_sender_key] success".into());
    }

    result.map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "store_sender_key failed: {:?}",
            e
        ))
    })
}


// ------------------------------------------------------------
// Utility
// ------------------------------------------------------------

fn make_key(sender: &ProtocolAddress, distribution_id: &str) -> String {
    format!(
        "{}::{}::{}",
        sender.name(),
        u32::from(sender.device_id()),
        distribution_id
    )
}

// ------------------------------------------------------------
// WASM exports
// ------------------------------------------------------------

/// Create a new empty SenderKeyStore
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_create() -> u32 {
    let store: SenderKeyStoreMap = HandleStore::new();

    
    let ptr = save_sender_key_store(store);

    console::log_1(&format!("[senderkeystore_create] PTR: {}", ptr).into());

    return ptr;
}

/// Destroy a SenderKeyStore
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_destroy(ptr: u32) {
    remove_sender_key_store(ptr);
}

/// Store SenderKeyRecord
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_store_sender_key(
    store_handle: u32,
    sender_addr_handle: u32,
    distribution_id: String,
    record_ptr: u32,
) -> Result<(), JsValue> {
    let sender =
        get_protocol_address_clone(sender_addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = make_key(&sender, &distribution_id);
    console::log_1(&format!("[senderkeystore_store_sender_key] success{}", key).into());

    // Serialize record defensively
    let bytes = senderkeyrecord_serialize(record_ptr)?;

    let record = SenderKeyRecord::deserialize(&bytes.to_vec())
        .map_err(|e| JsValue::from_str(&format!("Invalid SenderKeyRecord: {:?}", e)))?;

    with_sender_key_store_mut(store_handle, |store| {
        store.insert(key, record);
        Ok(())
    })
}

/// Load SenderKeyRecord (returns 0 if not found)
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_load_sender_key(
    store_handle: u32,
    sender_addr_handle: u32,
    distribution_id: String,
) -> Result<u32, JsValue> {
    let sender =
        get_protocol_address_clone(sender_addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = make_key(&sender, &distribution_id);

    console::log_1(&format!("[senderkeystore_load_sender_key] success{}", key).into());

    with_sender_key_store(store_handle, |store| {
        match store.get(&key) {
            Some(record) => {
                // Defensive copy: serialize → deserialize → new handle
                let bytes = record
                    .serialize()
                    .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

                Ok(senderkeyrecord_deserialize(&Uint8Array::from(bytes.as_slice())))
            }
            None => Ok(0),
        }
    })
}

/// Check existence
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_contains_sender_key(
    store_handle: u32,
    sender_addr_handle: u32,
    distribution_id: String,
) -> Result<bool, JsValue> {
    let sender =
        get_protocol_address_clone(sender_addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = make_key(&sender, &distribution_id);

    with_sender_key_store(store_handle, |store| Ok(store.contains(&key)))
}

/// Delete SenderKeyRecord
#[wasm_bindgen(js_namespace = senderKeyStore)]
pub fn senderkeystore_remove_sender_key(
    store_handle: u32,
    sender_addr_handle: u32,
    distribution_id: String,
) -> Result<(), JsValue> {
    let sender =
        get_protocol_address_clone(sender_addr_handle)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let key = make_key(&sender, &distribution_id);

    with_sender_key_store_mut(store_handle, |store| {
        store.remove(&key);
        Ok(())
    })
}
