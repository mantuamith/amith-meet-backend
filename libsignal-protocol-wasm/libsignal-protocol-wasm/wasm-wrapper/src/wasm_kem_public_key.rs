// wasm_kem_public_key.rs
use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::kem::PublicKey as KyberPublicKey;

use crate::handle_table::HandleTable; // adjust path if needed

// ======================================================================
// Global handle table (0 = invalid)
// ======================================================================

static KYBER_PUBLIC_KEYS: Lazy<Mutex<HandleTable<KyberPublicKey>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// ======================================================================
// Helpers
// ======================================================================

pub fn store_kyber_public_key(pk: KyberPublicKey) -> u32 {
    KYBER_PUBLIC_KEYS.lock().unwrap().insert(pk)
}

pub fn with_kyber_public_key<R>(
    handle: u32,
    f: impl FnOnce(&KyberPublicKey) -> Result<R, JsValue>,
) -> Result<R, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null Kyber public key handle"));
    }

    KYBER_PUBLIC_KEYS
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn take_kyber_public_key(handle: u32) -> Option<KyberPublicKey> {
    if handle == 0 {
        return None;
    }
    KYBER_PUBLIC_KEYS.lock().unwrap().take(handle)
}

// ======================================================================
// Utilities
// ======================================================================

fn boxslice_to_uint8array(b: Box<[u8]>) -> Uint8Array {
    Uint8Array::from(b.as_ref())
}

// ======================================================================
// WASM exports
// ======================================================================

#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_deserialize(
    bytes: &Uint8Array,
    offset: usize,
    length: usize,
) -> u32 {
    let full = bytes.to_vec();

    if offset > full.len() {
        console::error_1(&format!("invalid offset {}", offset).into());
        return 0;
    }

    let end = offset.saturating_add(length).min(full.len());
    let slice = &full[offset..end];

    match KyberPublicKey::deserialize(slice) {
        Ok(pk) => store_kyber_public_key(pk),
        Err(e) => {
            console::error_1(
                &format!("kyberpublickey_deserialize failed: {e}").into(),
            );
            0
        }
    }
}

#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_destroy(handle: u32) {
    let _ = take_kyber_public_key(handle);
}

#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_serialize(handle: u32) -> Uint8Array {
    with_kyber_public_key(handle, |pk| {
        Ok(boxslice_to_uint8array(pk.serialize()))
    })
    .unwrap_or_else(|e| {
        console::error_1(
            &format!("kyberpublickey_serialize failed: {e:?}").into(),
        );
        Uint8Array::new_with_length(0)
    })
}

#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_equals(a: u32, b: u32) -> bool {
    if a == 0 || b == 0 {
        return false;
    }

    with_kyber_public_key(a, |ka| {
        with_kyber_public_key(b, |kb| Ok(ka == kb))
    })
    .unwrap_or(false)
}
