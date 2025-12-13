use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;
use libsignal_core::curve::{PrivateKey, PublicKey, KeyPair};
use crate::wasm_ec_public_key::store_public_key;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use base64::engine::general_purpose::STANDARD as B64;
use getrandom;

use wasm_bindgen::JsValue;

use once_cell::sync::Lazy;

use std::sync::Mutex;
use std::cmp::Ordering;

// ------------------------------
// Utility: Convert Rust Vec<u8> → Uint8Array
// ------------------------------
fn vec_to_uint8array(data: &[u8]) -> Uint8Array {
    Uint8Array::from(data)
}

static PRIVATE_KEYS: Lazy<Mutex<Vec<Option<Box<PrivateKey>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

/// Store → return handle
pub fn store_key(pk: PrivateKey) -> u32 {
    let mut table = PRIVATE_KEYS.lock().unwrap();
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

/*
pub fn get_private_key(ptr: u32) -> Option<PrivateKey> {  
    if ptr == 0 {
        return None;
    } 

    let table = PRIVATE_KEYS.lock().unwrap();
    table
        .get((ptr - 1) as usize)?
        .as_ref()
        .map(|boxed| (**boxed).clone())
}*/

pub fn with_private_key<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PrivateKey) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid EC private key pointer"));
    }

    let table = PRIVATE_KEYS.lock().unwrap();

    let key = table
        .get((ptr - 1) as usize)
        .and_then(|slot| slot.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid EC private key pointer"))?;

    // Borrow happens ONLY here
    f(key)
}

fn remove_key(ptr: u32) {    
    if ptr == 0 {
        return;
    } 

    let mut table = PRIVATE_KEYS.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

extern crate alloc;

// ===============================================================
// ================  WASM-BINDGEN EXPORTED FUNCTIONS =============
// ===============================================================
#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_generate() -> u32 {    
    // --- Generate 32 bytes of strong randomness ---
    let mut seed = [0u8; 32];
    if let Err(e) = getrandom::getrandom(&mut seed) {
        console::error_1(&format!("Random seed error: {}", e).into());
        return 0; // return NULL-handle in JS
    }

    // --- Create a ChaCha20 RNG from the seed ---
    let mut rng = ChaCha20Rng::from_seed(seed);

    // --- Generate a keypair ---
    let keypair = KeyPair::generate(&mut rng);

    // --- Store the private key in your table ---
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
pub fn ecprivatekey_sign(ptr: u32, message: &[u8]) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr, |key| {
        // ---- Secure RNG ----
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed)
            .map_err(|e| JsValue::from_str(&format!("Random seed error: {e}")))?;
        let mut rng = ChaCha20Rng::from_seed(seed);

        // ---- IMPORTANT ----
        // `message` MUST be PublicKey::serialize() bytes
        // (i.e. includes key-type prefix 0x05)
        if message.is_empty() {
            return Err(JsValue::from_str("ecprivatekey_sign: empty message"));
        }

        let sig = key
            .calculate_signature(message, &mut rng)
            .map_err(|e| JsValue::from_str(&format!("sign failed: {e:?}")))?;

        // sig is Box<[u8]> → &[u8] is fine
        Ok(vec_to_uint8array(&sig))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_agree(ptr_priv: u32, ptr_pub: u32) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr_priv, |priv_key| {
        // Fetch the public key OUTSIDE the private-key lock if possible
        // (safe because it uses a different table)
        let pub_key = crate::wasm_ec_public_key::get_public_key(ptr_pub)?;

        let shared = priv_key
            .calculate_agreement(&pub_key)
            .map_err(|_| JsValue::from_str("calculate_agreement - ECDH agreement failed"))?;

        Ok(vec_to_uint8array(&shared))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_get_public_key(ptr: u32) -> Result<u32, JsValue> {
    with_private_key(ptr, |priv_key| {
        let pub_key = priv_key
            .public_key()
            .map_err(|e| JsValue::from_str(&format!("derive public key failed: {}", e)))?;

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
    Err(JsValue::from_str("HPKE is not supported in libsignal_protocol::PrivateKey"))
}

// ---------------------------------------------
// Secure RNG for WASM
// ---------------------------------------------
fn new_rng() -> Result<ChaCha20Rng, JsValue> {
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| js_err(format!("Random seed error: {e}")))?;
    Ok(ChaCha20Rng::from_seed(seed))
}

// ---------------------------------------------
// Local js_err helper (required)
// ---------------------------------------------
fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}