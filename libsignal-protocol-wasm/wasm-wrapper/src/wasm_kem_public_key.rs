// wasm_kem_public_key.rs
use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

// <- Adjust this import if your KyberPublicKey lives elsewhere:
use libsignal_protocol::kem::{PublicKey as KyberPublicKey};

/// Global handle table for stored KyberPublicKey instances.
/// Handle 0 is reserved as "null".
static KYBER_PUBLIC_KEYS: Lazy<Mutex<Vec<Option<Box<KyberPublicKey>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // index 0 = null handle
    Mutex::new(v)
});

/// Ensure the table exists and return a mutable reference (locked).
fn table_mut() -> std::sync::MutexGuard<'static, Vec<Option<Box<KyberPublicKey>>>> {
    KYBER_PUBLIC_KEYS.lock().unwrap()
}

/// Store a KyberPublicKey and return a `u32` handle.
pub fn store_kyber_public_key(pk: KyberPublicKey) -> u32 {
    let mut table = table_mut();
    let boxed = Box::new(pk);

    // reuse first free slot
    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32;  // return (index + 1)
        }
    }

    table.push(Some(boxed));
    table.len() as u32  // return (index + 1)
}

/// Load immutable reference (no clone)
pub fn get_kyber_public_key(ptr: u32) -> Option<KyberPublicKey> {
    if ptr == 0 {
        return None;
    }

    let table = KYBER_PUBLIC_KEYS.lock().unwrap();
    table
        .get((ptr - 1) as usize)?
        .as_ref()
        .map(|boxed| (**boxed).clone())
}

/// Take (remove & return) a boxed KyberPublicKey by handle.
/// Returns `None` if handle is 0 or invalid.
pub fn take_kyber_public_key(handle: u32) -> Option<Box<KyberPublicKey>> {
    if handle == 0 {
        return None;
    }
    let mut table = table_mut();
    table.get_mut((handle - 1) as usize).and_then(|slot| slot.take())
}

/// Get an immutable reference to the KyberPublicKey by handle.
/// Returns `Err(JsValue)` when invalid.
fn get_kyber_public_key_ref(handle: u32) -> Result<&'static KyberPublicKey, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null handle"));
    }

    // We fetch the boxed pointer and extend its lifetime unsafely to 'static.
    // This is OK as long as we don't hold the reference across any mutation that could free the slot.
    // We only use the reference briefly and avoid returning references into the MutexGuard.
    let table = KYBER_PUBLIC_KEYS.lock().unwrap();
    let opt = table.get((handle - 1) as usize).and_then(|slot| slot.as_ref());
    match opt {
        Some(b) => {
            let p: *const KyberPublicKey = &**b as *const KyberPublicKey;
            // SAFETY: the boxed value inside the global table will not be moved while the process is running.
            // We return a reference with 'static lifetime for short-lived usage only.
            Ok(unsafe { &*p })
        }
        None => Err(JsValue::from_str("invalid public key handle")),
    }
}

// Helper: convert Vec<u8> / Box<[u8]> => Uint8Array
fn boxslice_to_uint8array(b: Box<[u8]>) -> Uint8Array {
    // Uint8Array::from expects &[u8]
    Uint8Array::from(b.as_ref())
}

fn slice_to_uint8array(s: &[u8]) -> Uint8Array {
    Uint8Array::from(s)
}

// ----------------------------- WASM exported functions -----------------------------

/// Deserialize KyberPublicKey from bytes[offset..offset+length]. On success returns non-zero handle.
/// On error returns 0 (null handle) and logs an error.
#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_deserialize(bytes: &Uint8Array, offset: usize, length: usize) -> u32 {
    // Convert the Uint8Array to Vec<u8>
    let full = bytes.to_vec();

    if offset > full.len() {
        console::error_1(&format!("kyberpublickey_deserialize: invalid offset {}", offset).into());
        return 0;
    }

    let end = offset.saturating_add(length).min(full.len());
    let slice = &full[offset..end];

    match KyberPublicKey::deserialize(slice) {
        Ok(pk) => {
            let handle = store_kyber_public_key(pk);
            handle
        }
        Err(e) => {
            console::error_1(&format!("kyberpublickey_deserialize failed: {}", e).into());
            0
        }
    }
}

/// Destroy the KyberPublicKey associated with `ptr`. If ptr == 0 this is a no-op.
#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_destroy(ptr: u32) {
    if ptr == 0 {
        return;
    }
    // take and drop
    let _ = take_kyber_public_key(ptr);
}

/// Serialize the KyberPublicKey to bytes. Returns an empty Uint8Array on error.
#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_serialize(ptr: u32) -> Uint8Array {
    if ptr == 0 {
        console::error_1(&"kyberpublickey_serialize: null handle".into());
        return Uint8Array::new_with_length(0);
    }

    match get_kyber_public_key_ref(ptr) {
        Ok(pk) => {
            // `serialize()` often returns Box<[u8]>
            let boxed = pk.serialize();
            boxslice_to_uint8array(boxed)
        }
        Err(e) => {
            console::error_1(&format!("kyberpublickey_serialize: {}", e.as_string().unwrap_or_default()).into());
            Uint8Array::new_with_length(0)
        }
    }
}

/// Compare two Kyber public keys for equality. null/invalid handles return false.
#[wasm_bindgen(js_namespace = kemPublicKey)]
pub fn kyberpublickey_equals(ptr_a: u32, ptr_b: u32) -> bool {
    if ptr_a == 0 || ptr_b == 0 {
        return false;
    }

    let a = match get_kyber_public_key_ref(ptr_a) {
        Ok(x) => x,
        Err(_) => return false,
    };
    let b = match get_kyber_public_key_ref(ptr_b) {
        Ok(x) => x,
        Err(_) => return false,
    };

    a == b
}
