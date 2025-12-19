use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::collections::{HashMap, HashSet};

use crate::handle_store::HandleStore;

use libsignal_protocol::{
    KyberPreKeyRecord,
    KyberPreKeyId,
    PublicKey,
    GenericSignedPreKey, // ✅ THIS ONE
};

pub struct KyberPreKeyStoreInner {
    store: HandleStore<u32, KyberPreKeyRecord>,
    used: HashSet<u32>,
    // (kyberPreKeyId, signedPreKeyId) -> seen base public keys
    base_keys_seen: HashMap<(u32, u32), Vec<PublicKey>>,
}

impl KyberPreKeyStoreInner {
    fn new() -> Self {
        Self {
            store: HandleStore::new(),
            used: HashSet::new(),
            base_keys_seen: HashMap::new(),
        }
    }
}

static KYBER_PREKEY_STORES: Lazy<Mutex<Vec<Option<Box<KyberPreKeyStoreInner>>>>> =
    Lazy::new(|| {
        let mut v = Vec::new();
        v.push(None);
        Mutex::new(v)
    });

fn save_kyber_prekey_store(store: KyberPreKeyStoreInner) -> u32 {
    let mut table = KYBER_PREKEY_STORES.lock().unwrap();
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

fn with_kyber_prekey_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&KyberPreKeyStoreInner) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid KyberPreKeyStore pointer"));
    }

    let table = KYBER_PREKEY_STORES.lock().unwrap();
    let store = table
        .get((ptr - 1) as usize)
        .and_then(|s| s.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid KyberPreKeyStore pointer"))?;

    f(store)
}

fn with_kyber_prekey_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut KyberPreKeyStoreInner) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid KyberPreKeyStore pointer"));
    }

    let mut table = KYBER_PREKEY_STORES.lock().unwrap();
    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|s| s.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid KyberPreKeyStore pointer"))?;

    f(store)
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_create() -> u32 {
    save_kyber_prekey_store(KyberPreKeyStoreInner::new())
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_load_kyber_prekey(
    store_handle: u32,
    id: u32,
) -> Result<Uint8Array, JsValue> {
    with_kyber_prekey_store(store_handle, |store| {
        let record = store
            .store
            .get(&id)
            .ok_or_else(|| {
                JsValue::from_str(&format!("No such KyberPreKeyRecord! {}", id))
            })?;

        let bytes = record
            .serialize()
            .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

        Ok(Uint8Array::from(bytes.as_slice()))
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_load_kyber_prekeys(
    store_handle: u32,
) -> Result<Vec<Uint8Array>, JsValue> {
    with_kyber_prekey_store(store_handle, |store| {
        let mut result = Vec::new();

        for record in store.store.values() {
            let bytes = record
                .serialize()
                .map_err(|e| JsValue::from_str(&format!("serialize failed: {:?}", e)))?;

            result.push(Uint8Array::from(bytes.as_slice()));
        }

        Ok(result)
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_store_kyber_prekey(
    store_handle: u32,
    id: u32,
    record_bytes: Uint8Array,
) -> Result<(), JsValue> {
    let record = KyberPreKeyRecord::deserialize(&record_bytes.to_vec())
        .map_err(|e| JsValue::from_str(&format!("Invalid KyberPreKeyRecord: {:?}", e)))?;

    with_kyber_prekey_store_mut(store_handle, |store| {
        store.store.insert(id, record);
        Ok(())
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_contains_kyber_prekey(
    store_handle: u32,
    id: u32,
) -> Result<bool, JsValue> {
    with_kyber_prekey_store(store_handle, |store| {
        Ok(store.store.contains(&id))
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_mark_kyber_prekey_used(
    store_handle: u32,
    kyber_prekey_id: u32,
    signed_prekey_id: u32,
    base_key_handle: u32,
) -> Result<(), JsValue> {
    crate::wasm_ec_public_key::with_public_key(base_key_handle, |base_key| {
        with_kyber_prekey_store_mut(store_handle, |store| {
            // Mark Kyber prekey as used
            store.used.insert(kyber_prekey_id);

            let entry = store
                .base_keys_seen
                .entry((kyber_prekey_id, signed_prekey_id))
                .or_insert_with(Vec::new);

            let base_bytes = base_key.serialize();

            // Reject reused base keys
            for existing in entry.iter() {
                if existing.serialize() == base_bytes {
                    return Err(JsValue::from_str("ReusedBaseKeyException"));
                }
            }

            // Store a CLONE (ownership required)
            entry.push(base_key.clone());

            Ok(())
        })
    })
}

#[wasm_bindgen(js_namespace = kyberPreKeyStore)]
pub fn kyberprekeystore_has_kyber_prekey_been_used(
    store_handle: u32,
    id: u32,
) -> Result<bool, JsValue> {
    with_kyber_prekey_store(store_handle, |store| {
        Ok(store.used.contains(&id))
    })
}
