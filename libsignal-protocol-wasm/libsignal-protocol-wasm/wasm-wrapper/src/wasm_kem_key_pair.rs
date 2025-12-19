use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::SeedableRng;
use getrandom;

use libsignal_protocol::kem::{
    KeyPair as KyberKeyPair,
    KeyType,
};

use crate::handle_table::HandleTable;
use crate::wasm_kem_public_key::store_kyber_public_key;
use crate::wasm_kem_secret_key::store_kyber_secret_key;
use crate::wasm_kem_public_key::with_kyber_public_key;
use crate::wasm_kem_secret_key::with_kyber_secret_key;

// ======================================================================
// Configuration
// ======================================================================

const KYBER_KEY_TYPE: KeyType = KeyType::Kyber1024;

// ======================================================================
// Global handle table (0 = invalid)
// ======================================================================

static KYBER_KEYPAIRS: Lazy<Mutex<HandleTable<KyberKeyPair>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// ======================================================================
// Helpers
// ======================================================================

pub fn store_kem_keypair(pair: KyberKeyPair) -> u32 {
    KYBER_KEYPAIRS.lock().unwrap().insert(pair)
}

pub fn with_kyber_keypair<R>(
    handle: u32,
    f: impl FnOnce(&KyberKeyPair) -> Result<R, JsValue>,
) -> Result<R, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null KyberKeyPair handle"));
    }

    KYBER_KEYPAIRS
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn take_kem_keypair(handle: u32) -> Option<KyberKeyPair> {
    if handle == 0 {
        return None;
    }
    KYBER_KEYPAIRS.lock().unwrap().take(handle)
}

pub fn get_kyber_keypair_clone(handle: u32) -> Result<KyberKeyPair, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("Invalid KyberKeyPair handle (0)"));
    }

    let table = KYBER_KEYPAIRS.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str("Invalid KyberKeyPair handle"));
    }

    Ok(table.with(handle, |kp| kp.clone()))
}

// ======================================================================
// Utilities
// ======================================================================

fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ======================================================================
// WASM exports (matches KEMKeyPairWasm interface)
// ======================================================================

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_generate() -> Result<u32, JsValue> {
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| js_err(format!("Random seed error: {e}")))?;

    let mut rng = ChaCha20Rng::from_seed(seed);

    let keypair = KyberKeyPair::generate(KYBER_KEY_TYPE, &mut rng);

    Ok(store_kem_keypair(keypair))
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_from_keys(
    pub_key_handle: u32,
    sec_key_handle: u32,
) -> Result<u32, JsValue> {
    // Borrow public key first
    with_kyber_public_key(pub_key_handle, |public_key| {
        // Then borrow secret key (NO nested lock on same table — different tables are OK)
        with_kyber_secret_key(sec_key_handle, |secret_key| {
            // Construct KyberKeyPair from existing keys
            let keypair = KyberKeyPair::new(
                public_key.clone(),
                secret_key.clone(),
            );

            Ok(store_kem_keypair(keypair))
        })
    })
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_destroy(handle: u32) {
    let _ = take_kem_keypair(handle);
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_public_key(handle: u32) -> Result<u32, JsValue> {
    with_kyber_keypair(handle, |kp| {
        Ok(store_kyber_public_key(kp.public_key.clone()))
    })
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_secret_key(handle: u32) -> Result<u32, JsValue> {
    with_kyber_keypair(handle, |kp| {
        Ok(store_kyber_secret_key(kp.secret_key.clone()))
    })
}
