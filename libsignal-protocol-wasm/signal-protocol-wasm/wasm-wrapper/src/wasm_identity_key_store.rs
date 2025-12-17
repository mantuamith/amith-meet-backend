use wasm_bindgen::prelude::*;
use wasm_bindgen::JsCast;
use wasm_bindgen_futures::JsFuture;

use js_sys::{Array, Function, Reflect, Uint8Array};

use async_trait::async_trait;

use libsignal_protocol::{
    IdentityKey, IdentityKeyPair, PrivateKey, PublicKey,
    IdentityChange, Direction,
};

use once_cell::sync::Lazy;

use std::sync::Mutex;
use std::cmp::Ordering;
use crate::handle_identity_store::{HandleIdentityStore};
use libsignal_core::address::ProtocolAddress;
use libsignal_protocol::{SessionRecord}; // adjust path if necessary
use libsignal_protocol::error::SignalProtocolError;
use libsignal_protocol::error::Result as ProtocolResult;

/// helpers from other modules (adjust names if necessary)
use crate::wasm_ec_public_key::{get_public_key_clone};
use crate::wasm_ec_private_key::{get_private_key_clone};


pub type IdentityStoreMap = HandleIdentityStore<String, IdentityKey>;

static IDENTITY_KEY_STORES: Lazy<Mutex<Vec<Option<Box<IdentityStoreMap>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

/// Store → return handle
fn save_identity_key_store(pk: IdentityStoreMap) -> u32 {
    let mut table = IDENTITY_KEY_STORES.lock().unwrap();
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

pub fn with_identity_key_store<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&IdentityStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid identity Store pointer"));
    }

    let table = IDENTITY_KEY_STORES.lock().unwrap();

    let key = table
        .get((ptr - 1) as usize)
        .and_then(|slot| slot.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid identity Store pointer"))?;

    // Borrow happens ONLY here
    f(key)
}

pub fn with_identity_key_store_mut<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&mut IdentityStoreMap) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid identity Store pointer"));
    }

    let mut table = IDENTITY_KEY_STORES.lock().unwrap();

    let store = table
        .get_mut((ptr - 1) as usize)
        .and_then(|slot| slot.as_mut())
        .ok_or_else(|| JsValue::from_str("Invalid identity Store pointer"))?;

    f(store)
}

fn remove_key(ptr: u32) {    
    if ptr == 0 {
        return;
    } 

    let mut table = IDENTITY_KEY_STORES.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

pub fn store_identity_key(
    store_handle: u32,
    address: &ProtocolAddress,
    identityKey: &IdentityKey,
) -> Result<u32, JsValue> {
    // Build a stable key (string is safest across WASM boundary)
    let identity_addr_key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    // store identity
    let store_handle = {
        // Mutate existing store
        with_identity_key_store_mut(store_handle, |store| {
            store.insert(identity_addr_key.clone(), identityKey.clone());
            Ok(())
        });

        store_handle
    };

    // Return both handles
    Ok(store_handle)
}

pub fn load_identity_key(
    store_handle: u32,
    address: &ProtocolAddress,
) -> ProtocolResult<Option<IdentityKey>> {
    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

   with_identity_key_store(store_handle, |store| {
        match store.get(&key) {
            Some(identityKey) => {
                // IMPORTANT: return a deep copy
                Ok(Some(identityKey.clone()))
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

pub fn get_identity_key_pair(
    store_handle: u32,
) -> ProtocolResult<IdentityKeyPair> {
    with_identity_key_store(store_handle, |store| {
        // IMPORTANT: return a deep copy
        Ok(store.identity_key_pair().clone())
    })
    .map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "get_identity_key_pair failed: {:?}",
            e
        ))
    })
}

pub fn get_local_registration_id(store_handle: u32,) -> ProtocolResult<u32> {
    with_identity_key_store(store_handle, |store| {
        // IMPORTANT: return a deep copy
        Ok(store.registration_id().clone())
    })
    .map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "get_local_registration_id failed: {:?}",
            e
        ))
    })
}

pub fn is_trusted_identity(
    store_handle: u32,
    addr: &ProtocolAddress,
    their_identity: &IdentityKey,
    _direction: Direction,
) -> ProtocolResult<bool> {
    // Build stable identity lookup key
    let key = format!("{}.{}", addr.name(), u32::from(addr.device_id()));

    with_identity_key_store(store_handle, |store| {
        match store.get(&key) {
            // First contact: trust on first use (TOFU)
            None => Ok(true),

            // Existing identity → must match exactly
            Some(stored_identity) => Ok(stored_identity == their_identity),
        }
    })
    .map_err(|e| {
        SignalProtocolError::InvalidArgument(format!(
            "is_trusted_identity failed: {:?}",
            e
        ))
    })
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_create_identity_key_store(
    identity_public_key_handle: u32,
    identity_private_key_handle: u32,
    registration_id: u32,
) -> Result<u32, JsValue> {
    let public_key = get_public_key_clone(identity_public_key_handle)?;

    let private_key = get_private_key_clone(identity_private_key_handle)
        .ok_or_else(|| JsValue::from_str("Invalid private key handle"))?;

    let identity_key = IdentityKey::new(public_key);
    let identity_key_pair = IdentityKeyPair::new(identity_key, private_key);

    let store: IdentityStoreMap = IdentityStoreMap::new(registration_id, identity_key_pair);

    Ok(save_identity_key_store(store))
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_get_identity_key_pair(store_handle: u32) -> Result<Uint8Array, JsValue> {
    let pair = get_identity_key_pair(store_handle)
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

    // serialize() -> Box<[u8]>
    let bytes = pair.serialize();

    Ok(Uint8Array::from(bytes.as_ref()))
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_get_local_registration_id(store_handle: u32) -> Result<u32, JsValue> {
    get_local_registration_id(store_handle)
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_save_identity(
    store_handle: u32,
    address_handle: u32,
    identity_key_bytes: Uint8Array,
) -> Result<u32, JsValue> {
    let address = crate::wasm_protocol_address::get_protocol_address_clone(address_handle)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let identity = IdentityKey::try_from(identity_key_bytes.to_vec().as_slice())
        .map_err(|e| JsValue::from_str(&format!("Invalid IdentityKey: {:?}", e)))?;

    let key = format!("{}.{}", address.name(), u32::from(address.device_id()));

    with_identity_key_store_mut(store_handle, |store| {
        let change = match store.get(&key) {
            None => IdentityChange::NewOrUnchanged,
            Some(existing) if existing == &identity => IdentityChange::NewOrUnchanged,
            Some(_) => IdentityChange::ReplacedExisting,
        };

        store.insert(key, identity);
        Ok(change as u32)
    })
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_is_trusted_identity(
    store_handle: u32,
    address_handle: u32,
    identity_key_bytes: Uint8Array,
    direction: u32, // ignored per libsignal
) -> Result<bool, JsValue> {
    let address = crate::wasm_protocol_address::get_protocol_address_clone(address_handle)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let identity = IdentityKey::try_from(identity_key_bytes.to_vec().as_slice())
        .map_err(|e| JsValue::from_str(&format!("Invalid IdentityKey: {:?}", e)))?;

    is_trusted_identity(store_handle, &address, &identity, Direction::Sending)
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))
}

#[wasm_bindgen(js_namespace = identityKeyStore)]
pub fn identitykeystore_get_identity(
    store_handle: u32,
    address_handle: u32,
) -> Result<Option<Uint8Array>, JsValue> {
    let address = crate::wasm_protocol_address::get_protocol_address_clone(address_handle)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let identity = load_identity_key(store_handle, &address)
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

    match identity {
        Some(identity) => {
            let bytes = identity.serialize(); // Box<[u8]>
            Ok(Some(Uint8Array::from(bytes.as_ref())))
        }
        None => Ok(None), // ✅ THIS IS THE FIX
    }
}


