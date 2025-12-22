use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::kem::SecretKey as KyberSecretKey;

use crate::handle_table::HandleTable;

// ======================================================================
// Global handle table (0 = invalid)
// ======================================================================

static KEM_SECRET_KEYS: Lazy<Mutex<HandleTable<KyberSecretKey>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// ======================================================================
// Helpers
// ======================================================================

pub fn store_kyber_secret_key(sk: KyberSecretKey) -> u32 {
    KEM_SECRET_KEYS.lock().unwrap().insert(sk)
}

pub fn with_kyber_secret_key<R>(
    handle: u32,
    f: impl FnOnce(&KyberSecretKey) -> Result<R, JsValue>,
) -> Result<R, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null Kyber secret key handle"));
    }

    KEM_SECRET_KEYS
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn take_kyber_secret_key(handle: u32) -> Option<KyberSecretKey> {
    if handle == 0 {
        return None;
    }
    KEM_SECRET_KEYS.lock().unwrap().take(handle)
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

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_deserialize(bytes: &[u8]) -> u32 {
    match KyberSecretKey::deserialize(bytes) {
        Ok(sk) => store_kyber_secret_key(sk),
        Err(e) => {
            web_sys::console::error_1(
                &format!("kybersecretkey_deserialize failed: {e}").into(),
            );
            0
        }
    }
}

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_destroy(handle: u32) {
    let _ = take_kyber_secret_key(handle);
}

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_serialize(handle: u32) -> Uint8Array {
    with_kyber_secret_key(handle, |sk| {
        Ok(boxslice_to_uint8array(sk.serialize()))
    })
    .unwrap_or_else(|e| {
        web_sys::console::error_1(
            &format!("kybersecretkey_serialize failed: {e:?}").into(),
        );
        Uint8Array::new_with_length(0)
    })
}
