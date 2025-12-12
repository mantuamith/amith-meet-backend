use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use once_cell::sync::Lazy;
use web_sys::console;

use libsignal_core::curve::PublicKey;

use std::sync::Mutex;
use std::cmp::Ordering;

// ======================================================================
// Safe global handle table
// ======================================================================

static PUBLIC_KEYS: Lazy<Mutex<Vec<Option<Box<PublicKey>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

/// Store → return handle
pub fn store_public_key(pk: PublicKey) -> u32 {
    let mut table = PUBLIC_KEYS.lock().unwrap();
    let boxed = Box::new(pk);

    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return i as u32;
        }
    }

    table.push(Some(boxed));
    (table.len() - 1) as u32
}

/// Get a CLONED PublicKey
fn get_public_key(handle: u32) -> Result<PublicKey, JsValue> {
    /*
    if handle == 0 {
        return Err(JsValue::from_str("null public key"));
    } */

    let table = PUBLIC_KEYS.lock().unwrap();

    table
        .get(handle as usize)
        .and_then(|slot| slot.as_ref())
        .map(|boxed| (**boxed).clone()) // clone the PublicKey (32 bytes)
        .ok_or_else(|| JsValue::from_str("invalid public key handle"))
}

/// Take ownership (used by destroy)
pub fn take_public_key(handle: u32) -> Option<Box<PublicKey>> {
    /*
    if handle == 0 {
        return None;
    } */

    let mut table = PUBLIC_KEYS.lock().unwrap();
    table.get_mut(handle as usize)?.take()
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
pub fn ec_public_key_deserialize(bytes: &[u8], offset: usize, length: usize) -> u32 {
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
    let _ = take_public_key(ptr);
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_verify(ptr: u32, message: &[u8], signature: &[u8]) -> bool {
    let pk = match get_public_key(ptr) {
        Ok(pk) => pk,
        Err(_) => return false,
    };

    pk.verify_signature(message, signature)
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_hpke_seal(
    _ptr: u32,
    _message: &[u8],
    _info: &[u8],
    _aad: &[u8]
) -> Uint8Array {
    console::warn_1(&"HPKE seal not implemented".into());
    Uint8Array::new_with_length(0)
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_serialize(ptr: u32) -> Uint8Array {
    match get_public_key(ptr) {
        Ok(pk) => vec_to_uint8array(&pk.serialize()),
        Err(_) => Uint8Array::new_with_length(0),
    }
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_get_public_key_bytes(ptr: u32) -> Uint8Array {
    match get_public_key(ptr) {
        Ok(pk) => vec_to_uint8array(pk.public_key_bytes()),
        Err(_) => Uint8Array::new_with_length(0),
    }
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_equals(ptr_a: u32, ptr_b: u32) -> bool {
    match (get_public_key(ptr_a), get_public_key(ptr_b)) {
        (Ok(a), Ok(b)) => a == b,
        _ => false,
    }
}

#[wasm_bindgen(js_namespace = ecPublicKey)]
pub fn ec_public_key_compare(ptr_a: u32, ptr_b: u32) -> i32 {
    if ptr_a == 0 && ptr_b == 0 { return 0; }
    if ptr_a == 0 { return -1; }
    if ptr_b == 0 { return 1; }

    let a = match get_public_key(ptr_a) { Ok(a) => a, Err(_) => return 0 };
    let b = match get_public_key(ptr_b) { Ok(b) => b, Err(_) => return 0 };

    match a.cmp(&b) {
        Ordering::Less => -1,
        Ordering::Equal => 0,
        Ordering::Greater => 1,
    }
}
