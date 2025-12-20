use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use once_cell::sync::Lazy;
use web_sys::console;

use libsignal_core::curve::PublicKey;

use std::sync::Mutex;

use crate::handle_table::HandleTable;

// ======================================================================
// Storage
// ======================================================================

static PUBLIC_KEYS: Lazy<Mutex<HandleTable<PublicKey>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// ======================================================================
// Helpers
// ======================================================================

/// Store → return handle
pub fn store_public_key(pk: PublicKey) -> u32 {
    PUBLIC_KEYS
        .lock()
        .unwrap()
        .insert(pk)
}

/// Borrow-only access
pub fn with_public_key<F, R>(handle: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PublicKey) -> Result<R, JsValue>,
{
    if handle == 0 {
        return Err(JsValue::from_str("Invalid EC public key handle (0)"));
    }

    PUBLIC_KEYS
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn get_public_key_clone(handle: u32) -> Result<PublicKey, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("Invalid EC public key handle (0)"));
    }

    let table = PUBLIC_KEYS.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str("Invalid EC public key handle"));
    }

    Ok(table.with(handle, |pk| pk.clone()))
}

/// Destroy / consume
pub fn remove_public_key(handle: u32) {
    if handle == 0 {
        return;
    }

    PUBLIC_KEYS
        .lock()
        .unwrap()
        .remove(handle);
}

// ======================================================================
// Conversions
// ======================================================================

fn vec_to_uint8array(v: &[u8]) -> Uint8Array {
    Uint8Array::from(v)
}

// ======================================================================
// WASM EXPORTED API
// ======================================================================

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_deserialize(
    bytes: &[u8],
    offset: usize,
    length: usize,
) -> u32 {
    if offset > bytes.len() {
        return 0;
    }

    let end = offset.saturating_add(length).min(bytes.len());
    let slice = &bytes[offset..end];

    match PublicKey::deserialize(slice) {
        Ok(pk) => store_public_key(pk),
        Err(e) => {
            console::error_1(&format!("deserialize error: {e}").into());
            0
        }
    }
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_destroy(ptr: u32) {
    remove_public_key(ptr);
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_verify(
    ptr: u32,
    message: &[u8],
    signature: &[u8],
) -> bool {
    with_public_key(ptr, |pk| {
        Ok(pk.verify_signature(message, signature))
    })
    .unwrap_or(false)
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_hpke_seal(
    _ptr: u32,
    _message: &[u8],
    _info: &[u8],
    _aad: &[u8],
) -> Uint8Array {
    console::warn_1(&"HPKE seal not implemented".into());
    Uint8Array::new_with_length(0)
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_serialize(ptr: u32) -> Uint8Array {
    with_public_key(ptr, |pk| {
        Ok(vec_to_uint8array(&pk.serialize()))
    })
    .unwrap_or_else(|_| Uint8Array::new_with_length(0))
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_get_public_key_bytes(ptr: u32) -> Uint8Array {
    with_public_key(ptr, |pk| {
        Ok(vec_to_uint8array(pk.public_key_bytes()))
    })
    .unwrap_or_else(|_| Uint8Array::new_with_length(0))
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_equals(ptr_a: u32, ptr_b: u32) -> bool {
    if ptr_a == 0 || ptr_b == 0 {
        return false;
    }

    with_public_key(ptr_a, |a| {
        with_public_key(ptr_b, |b| {
            Ok(a == b)
        })
    })
    .unwrap_or(false)
}
