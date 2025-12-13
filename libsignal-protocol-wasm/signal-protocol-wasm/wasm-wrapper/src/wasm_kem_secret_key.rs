use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::kem::{SecretKey as KyberSecretKey};

/// Global table of KyberSecretKey handles
static KEM_SECRET_KEYS: Lazy<Mutex<Vec<Option<Box<KyberSecretKey>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // index 0 = NULL pointer
    Mutex::new(v)
});

/// Store secret key → return handle
pub fn store_kyber_secret_key(sk: KyberSecretKey) -> u32 {
    let mut table = KEM_SECRET_KEYS.lock().unwrap();
    let boxed = Box::new(sk);

    // Reuse free slots
    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32; // return (index + 1)
        }
    }

    // Push new
    table.push(Some(boxed));
    table.len() as u32 // return (index + 1)
}

/// Load immutable reference (no clone)
fn load_kyber_secret_key(ptr: u32) -> Option<KyberSecretKey> {
    if ptr == 0 {
        return None;
    }

    let table = KEM_SECRET_KEYS.lock().unwrap();
    table
        .get((ptr - 1) as usize)?
        .as_ref()
        .map(|boxed| (**boxed).clone())
}

/// Remove key (destroy)
fn remove_kyber_secret_key(ptr: u32) {
    if ptr == 0 {
        return;
    }
    let mut table = KEM_SECRET_KEYS.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

//
// =======================================================
//  WASM-EXPORTED API (matches TypeScript interface)
// =======================================================
//

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_deserialize(bytes: &[u8]) -> u32 {
    match KyberSecretKey::deserialize(bytes) {
        Ok(sk) => store_kyber_secret_key(sk),
        Err(e) => {
            web_sys::console::error_1(
                &format!("kybersecretkey_deserialize failed: {}", e).into()
            );
            0 // NULL handle
        }
    }
}

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_destroy(ptr: u32) {
    remove_kyber_secret_key(ptr);
}

#[wasm_bindgen(js_namespace = kemSecretKey)]
pub fn kybersecretkey_serialize(ptr: u32) -> Uint8Array {
    if let Some(sk) = load_kyber_secret_key(ptr) {
        let bytes = sk.serialize(); // returns Box<[u8]>
        Uint8Array::from(bytes.as_ref())
    } else {
        web_sys::console::error_1(&"kybersecretkey_serialize: invalid ptr".into());
        Uint8Array::new_with_length(0)
    }
}
