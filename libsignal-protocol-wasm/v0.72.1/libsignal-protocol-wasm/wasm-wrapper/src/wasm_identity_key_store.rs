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
use crate::wasm_ec_public_key::{with_public_key};
use crate::wasm_ec_private_key::{with_private_key};

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{RngCore, SeedableRng};

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
    // Borrow public key
    with_public_key(identity_public_key_handle, |public_key| {
        // Borrow private key
        with_private_key(identity_private_key_handle, |private_key| {
            // Build IdentityKeyPair inside the closures
            let identity_key = IdentityKey::new(public_key.clone());
            let identity_key_pair =
                IdentityKeyPair::new(identity_key, private_key.clone());

            let store =
                IdentityStoreMap::new(registration_id, identity_key_pair);

            Ok(save_identity_key_store(store))
        })
    })
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

#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use wasm_bindgen::JsValue;
    use js_sys::Uint8Array;

    use libsignal_protocol::{IdentityKeyPair, IdentityKey, KeyPair};
    use libsignal_core::address::ProtocolAddress;
    use libsignal_core::address::DeviceId;

    wasm_bindgen_test_configure!(run_in_browser);

    /// Helper: create a dummy keypair for testing
    fn make_test_identity_keypair() -> IdentityKeyPair {
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed).unwrap();
        let mut rng = rand_chacha::ChaCha20Rng::from_seed(seed);
        let kp = KeyPair::generate(&mut rng);

        IdentityKeyPair::new(IdentityKey::new(kp.public_key), kp.private_key)
    }

    /// Helper: create a dummy ProtocolAddress
    fn make_test_address() -> ProtocolAddress {
        let device_id = DeviceId::new(1).unwrap();
        ProtocolAddress::new("user".to_string(), device_id)
    }

    #[wasm_bindgen_test]
    fn create_identity_key_store_and_verify_handles() {
        let ikp = make_test_identity_keypair();
        let pub_handle = crate::wasm_ec_public_key::store_public_key(ikp.public_key().clone());
        let priv_handle = crate::wasm_ec_private_key::store_key(ikp.private_key().clone());
        let registration_id = 42;

        let store_handle = identitykeystore_create_identity_key_store(pub_handle, priv_handle, registration_id)
            .expect("Failed to create identity key store");

        assert!(store_handle != 0, "Store handle must be non-zero");
    }

    #[wasm_bindgen_test]
    fn get_identity_key_pair_returns_serialized_bytes() {
        let ikp = make_test_identity_keypair();
        let pub_handle = crate::wasm_ec_public_key::store_public_key(ikp.public_key().clone());
        let priv_handle = crate::wasm_ec_private_key::store_key(ikp.private_key().clone());
        let store_handle = identitykeystore_create_identity_key_store(pub_handle, priv_handle, 42).unwrap();

        let serialized = identitykeystore_get_identity_key_pair(store_handle)
            .expect("Failed to get identity key pair");

        assert!(serialized.length() > 0, "Serialized identity keypair must be non-empty");
    }

    #[wasm_bindgen_test]
    fn save_and_load_identity() {
        let ikp = make_test_identity_keypair();
        let pub_handle = crate::wasm_ec_public_key::store_public_key(ikp.public_key().clone());
        let priv_handle = crate::wasm_ec_private_key::store_key(ikp.private_key().clone());
        let store_handle = identitykeystore_create_identity_key_store(pub_handle, priv_handle, 123).unwrap();

        let address = make_test_address();
        let identity_bytes = Uint8Array::from(ikp.public_key().serialize().as_ref());

        let addr_handle = crate::wasm_protocol_address::store_address(address.clone());
        let save_result = identitykeystore_save_identity(store_handle, addr_handle, identity_bytes.clone())
            .expect("Failed to save identity");

        // First save should be 'new or unchanged'
        assert_eq!(save_result, 0);

        let loaded = identitykeystore_get_identity(store_handle, addr_handle)
            .expect("Failed to load identity")
            .expect("Identity should exist");

        assert_eq!(loaded.length(), identity_bytes.length(), "Loaded identity must match saved bytes");
    }

    #[wasm_bindgen_test]
    fn trusted_identity_detection() {
        let ikp = make_test_identity_keypair();
        let pub_handle = crate::wasm_ec_public_key::store_public_key(ikp.public_key().clone());
        let priv_handle = crate::wasm_ec_private_key::store_key(ikp.private_key().clone());
        let store_handle = identitykeystore_create_identity_key_store(pub_handle, priv_handle, 321).unwrap();

        let address = make_test_address();
        let addr_handle = crate::wasm_protocol_address::store_address(address.clone());

        let identity_bytes = Uint8Array::from(ikp.public_key().serialize().as_ref());

        // First contact → should be trusted (TOFU)
        let is_trusted = identitykeystore_is_trusted_identity(store_handle, addr_handle, identity_bytes.clone(), 0)
            .expect("Failed trust check");

        assert!(is_trusted, "First contact should be trusted");

        // Save identity and check again
        identitykeystore_save_identity(store_handle, addr_handle, identity_bytes.clone()).unwrap();
        let is_trusted_again = identitykeystore_is_trusted_identity(store_handle, addr_handle, identity_bytes, 0)
            .expect("Failed second trust check");

        assert!(is_trusted_again, "Saved identity should still be trusted");
    }

    #[wasm_bindgen_test]
    fn load_missing_identity_returns_none() {
        let ikp = make_test_identity_keypair();
        let pub_handle = crate::wasm_ec_public_key::store_public_key(ikp.public_key().clone());
        let priv_handle = crate::wasm_ec_private_key::store_key(ikp.private_key().clone());
        let store_handle = identitykeystore_create_identity_key_store(pub_handle, priv_handle, 999).unwrap();

        let address = make_test_address();
        let addr_handle = crate::wasm_protocol_address::store_address(address.clone());

        let result = identitykeystore_get_identity(store_handle, addr_handle)
            .expect("Failed to get identity");

        assert!(result.is_none(), "Non-existent identity must return None");
    }
}
