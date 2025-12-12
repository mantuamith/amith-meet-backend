use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use base64::engine::general_purpose::STANDARD as B64;
use getrandom;

use libsignal_protocol::kem::{
    KeyPair as KyberKeyPair, PublicKey as KyberPublicKey, SecretKey as KyberSecretKey,
};

const KYBER_KEY_TYPE: libsignal_protocol::kem::KeyType = libsignal_protocol::kem::KeyType::Kyber1024;

use crate::wasm_kem_public_key::store_kyber_public_key;
use crate::wasm_kem_secret_key::store_kyber_secret_key;

// ============================================================
// Handle table for KyberKeyPair
// ============================================================

static KYBER_KEYPAIRS: Lazy<Mutex<Vec<Option<Box<KyberKeyPair>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // 0 = null handle
    Mutex::new(v)
});

fn store_kem_keypair(pair: KyberKeyPair) -> u32 {
    let mut table = KYBER_KEYPAIRS.lock().unwrap();
    let boxed = Box::new(pair);

    // Reuse empty slots
    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32; // return (index + 1)
        }
    }

    // Otherwise append
    table.push(Some(boxed));
    table.len() as u32 // return (index + 1)
}

fn get_kem_keypair(ptr: u32) -> Option<KyberKeyPair> {
    if ptr == 0 {
        return None;
    }
    let table = KYBER_KEYPAIRS.lock().unwrap();
    table
        .get((ptr - 1) as usize)?
        .as_ref()
        .map(|boxed| (**boxed).clone())
}

fn remove_kem_keypair(ptr: u32) {
    if ptr == 0 {
        return;
    }
    let mut table = KYBER_KEYPAIRS.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ============================================================
// WASM Exports (matches KEMKeyPairWasm interface)
// ============================================================
#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_generate() -> Result<u32, JsValue> {
    // --- Generate 32 bytes of strong randomness ---
    let mut seed = [0u8; 32];
    if let Err(e) = getrandom::getrandom(&mut seed) {
        return Err(js_err(format!("Random seed error: {}", e)));
    }

    // Create RNG
    let mut rng = ChaCha20Rng::from_seed(seed);

    // KyberKeyPair::generate does not return Result
    let keypair = KyberKeyPair::generate(KYBER_KEY_TYPE, &mut rng);

    Ok(store_kem_keypair(keypair))
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_destroy(ptr: u32) {
    remove_kem_keypair(ptr);
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_public_key(ptr: u32) -> Result<u32, JsValue> {
    let keypair = get_kem_keypair(ptr)
        .ok_or_else(|| js_err("Invalid KyberKeyPair handle"))?;

    let public = keypair.public_key.clone();

    Ok(store_kyber_public_key(public))
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_secret_key(ptr: u32) -> Result<u32, JsValue> {
    let keypair = get_kem_keypair(ptr)
        .ok_or_else(|| js_err("Invalid KyberKeyPair handle"))?;

    let secret = keypair.secret_key.clone();

    Ok(store_kyber_secret_key(secret))
}
