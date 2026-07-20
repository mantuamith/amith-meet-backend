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
pub fn with_public_key<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PublicKey) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid EC public key handle (0)"));
    }

    let guard = PUBLIC_KEYS.lock().unwrap();

    // Check if handle exists
    if !guard.contains(ptr) {
        return Err(JsValue::from_str(
            "EC public key handle is invalid or has been destroyed",
        ));
    }

    // Safe to call with() because handle exists
    guard.with(ptr, f)
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

    let table = PUBLIC_KEYS.lock().unwrap();

    if !table.contains(ptr_a) || !table.contains(ptr_b) {
        return false;
    }

    table
        .with(ptr_a, |a| {
            table.with(ptr_b, |b| {
                Ok::<bool, JsValue>(a == b)
            })
        })
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen::JsValue;
    use wasm_bindgen_test::*;

    use crate::wasm_ec_private_key::{
        ecprivatekey_generate,
        ecprivatekey_get_public_key,
        ecprivatekey_sign,
    };

    wasm_bindgen_test_configure!(run_in_browser);

    fn assert_non_empty(arr: &Uint8Array, msg: &str) {
        assert!(arr.length() > 0, "{msg}");
    }

    #[wasm_bindgen_test]
    fn deserialize_public_key_success() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        let bytes = ec_public_key_serialize(pub_ptr);
        assert_non_empty(&bytes, "Serialized public key must not be empty");

        let new_ptr = ec_public_key_deserialize(&bytes.to_vec(), 0, bytes.length() as usize);
        assert!(new_ptr != 0, "Deserialized public key handle must not be 0");
    }

    #[wasm_bindgen_test]
    fn deserialize_public_key_invalid_slice_fails() {
        let bytes = vec![1, 2, 3, 4, 5];
        let ptr = ec_public_key_deserialize(&bytes, 100, 10);
        assert_eq!(ptr, 0, "Invalid slice must return handle 0");
    }

    #[wasm_bindgen_test]
    fn serialize_invalid_handle_returns_empty() {
        let bytes = ec_public_key_serialize(0);
        assert_eq!(bytes.length(), 0, "Handle 0 must serialize to empty array");
    }

    #[wasm_bindgen_test]
    fn public_key_verify_signature_success() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        let msg = b"verify me";
        let sig = ecprivatekey_sign(priv_ptr, msg).unwrap();

        let ok = ec_public_key_verify(pub_ptr, msg, &sig.to_vec());
        assert!(ok, "Signature verification must succeed");
    }

    #[wasm_bindgen_test]
    fn public_key_verify_signature_fails_for_wrong_message() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        let sig = ecprivatekey_sign(priv_ptr, b"correct").unwrap();

        let ok = ec_public_key_verify(pub_ptr, b"wrong", &sig.to_vec());
        assert!(!ok, "Verification must fail for wrong message");
    }

    #[wasm_bindgen_test]
    fn public_key_verify_invalid_handle_returns_false() {
        let ok = ec_public_key_verify(0, b"msg", b"sig");
        assert!(!ok, "Invalid handle must return false");
    }

    #[wasm_bindgen_test]
    fn public_key_equals_success() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        
        let bytes = ec_public_key_serialize(pub_ptr);
       
        let cloned_ptr =
            ec_public_key_deserialize(&bytes.to_vec(), 0, bytes.length() as usize);
    
        assert!(
            ec_public_key_equals(pub_ptr, cloned_ptr),
            "Public keys derived from same bytes must be equal"
        ); 
    }

    #[wasm_bindgen_test]
    fn public_key_equals_false_for_different_keys() {
        let priv_a = ecprivatekey_generate();
        let priv_b = ecprivatekey_generate();

        let pub_a = ecprivatekey_get_public_key(priv_a).unwrap();
        let pub_b = ecprivatekey_get_public_key(priv_b).unwrap();

        assert!(
            !ec_public_key_equals(pub_a, pub_b),
            "Different public keys must not be equal"
        );
    }


    #[wasm_bindgen_test]
    fn destroy_public_key_invalidates_handle() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        ec_public_key_destroy(pub_ptr);

        let bytes = ec_public_key_serialize(pub_ptr);
        assert_eq!(
            bytes.length(),
            0,
            "Destroyed public key must serialize to empty array"
        );
    }

    #[wasm_bindgen_test]
    fn get_public_key_bytes_success() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        let bytes = ec_public_key_get_public_key_bytes(pub_ptr);
        assert_non_empty(&bytes, "Public key bytes must not be empty");
    }

    #[wasm_bindgen_test]
    fn hpke_seal_returns_empty() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = ecprivatekey_get_public_key(priv_ptr).unwrap();

        let out = ec_public_key_hpke_seal(pub_ptr, b"msg", b"info", b"aad");
        assert_eq!(
            out.length(),
            0,
            "HPKE seal stub must return empty Uint8Array"
        );
    }
}

