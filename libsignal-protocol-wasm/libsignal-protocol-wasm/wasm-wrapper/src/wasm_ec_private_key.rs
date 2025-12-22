use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_core::curve::{PrivateKey, PublicKey, KeyPair};

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::SeedableRng;
use getrandom;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::{store_public_key, with_public_key};

// ------------------------------
// Utility
// ------------------------------
fn vec_to_uint8array(data: &[u8]) -> Uint8Array {
    Uint8Array::from(data)
}

// -----------------------------------------------------------------------------
// Storage
// -----------------------------------------------------------------------------

static PRIVATE_KEYS: Lazy<Mutex<HandleTable<PrivateKey>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

pub fn store_key(pk: PrivateKey) -> u32 {
    PRIVATE_KEYS.lock().unwrap().insert(pk)
}


pub fn with_private_key<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PrivateKey) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid EC private key handle (0)"));
    }

    PRIVATE_KEYS
        .lock()
        .unwrap()
        .with(ptr, f)
}

fn remove_key(ptr: u32) {
    if ptr == 0 {
        return;
    }

    PRIVATE_KEYS.lock().unwrap().remove(ptr);
}

// ===============================================================
// ================  WASM-BINDGEN EXPORTED FUNCTIONS =============
// ===============================================================

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_generate() -> u32 {
    let mut seed = [0u8; 32];
    if let Err(e) = getrandom::getrandom(&mut seed) {
        console::error_1(&format!("Random seed error: {e}").into());
        return 0;
    }

    let mut rng = ChaCha20Rng::from_seed(seed);
    let keypair = KeyPair::generate(&mut rng);

    store_key(keypair.private_key)
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_deserialize(bytes: &[u8]) -> u32 {
    match PrivateKey::deserialize(bytes) {
        Ok(key) => store_key(key),
        Err(_) => 0,
    }
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_destroy(ptr: u32) {
    remove_key(ptr);
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_serialize(ptr: u32) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr, |key| {
        Ok(vec_to_uint8array(&key.serialize()))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_sign(
    ptr: u32,
    message: &[u8],
) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr, |key| {
        if message.is_empty() {
            return Err(JsValue::from_str("ecprivatekey_sign: empty message"));
        }

        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed)
            .map_err(|e| JsValue::from_str(&format!("Random seed error: {e}")))?;
        let mut rng = ChaCha20Rng::from_seed(seed);

        let sig = key
            .calculate_signature(message, &mut rng)
            .map_err(|e| JsValue::from_str(&format!("sign failed: {e:?}")))?;

        Ok(vec_to_uint8array(&sig))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_agree(
    ptr_priv: u32,
    ptr_pub: u32,
) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr_priv, |priv_key| {
        with_public_key(ptr_pub, |pub_key| {
            let shared = priv_key
                .calculate_agreement(pub_key)
                .map_err(|_| JsValue::from_str("ECDH agreement failed"))?;

            Ok(vec_to_uint8array(&shared))
        })
    })
}


#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_get_public_key(ptr: u32) -> Result<u32, JsValue> {
    with_private_key(ptr, |priv_key| {
        let pub_key = priv_key
            .public_key()
            .map_err(|e| JsValue::from_str(&format!("derive public key failed: {e}")))?;

        Ok(store_public_key(pub_key))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_hpke_open(
    _ptr: u32,
    _ciphertext: &[u8],
    _info: &[u8],
    _aad: &[u8],
) -> Result<Uint8Array, JsValue> {
    Err(JsValue::from_str(
        "HPKE is not supported in libsignal_protocol::PrivateKey",
    ))
}
