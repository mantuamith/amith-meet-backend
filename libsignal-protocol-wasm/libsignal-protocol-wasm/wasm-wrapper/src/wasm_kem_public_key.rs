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

    let table = KYBER_PUBLIC_KEYS.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str(&format!("Invalid Kyber public key handle: {}", handle)));
    }

    table.with(handle, f)
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

    let table = KYBER_PUBLIC_KEYS.lock().unwrap();

    // check both handles exist
    if !table.contains(a) || !table.contains(b) {
        return false;
    }

    let pk_a = table.with(a, |pk| pk.clone()); // clone or copy if needed
    let pk_b = table.with(b, |pk| pk.clone());

    pk_a == pk_b
}

#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use js_sys::Uint8Array;

    use libsignal_protocol::kem::KeyPair as KyberKeyPair;
    use rand_chacha::ChaCha20Rng;
    use rand_chacha::rand_core::{RngCore, CryptoRng};
    use rand::SeedableRng;

    use crate::wasm_kem_public_key::*;

    wasm_bindgen_test_configure!(run_in_browser);

    /// Helper: deterministic RNG for testing
    fn make_test_rng() -> ChaCha20Rng {
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed).unwrap();
        ChaCha20Rng::from_seed(seed)
    }

    /// Generate a Kyber keypair and return the public key handle
    fn generate_pubkey_handle(rng: &mut ChaCha20Rng) -> u32 {
        let kp = KyberKeyPair::generate(libsignal_protocol::kem::KeyType::Kyber1024, rng);
        store_kyber_public_key(kp.public_key)
    }
    
    #[wasm_bindgen_test]
    fn store_and_retrieve_public_key() {
        let mut rng = make_test_rng();
        let handle = generate_pubkey_handle(&mut rng);
        
        
        let serialized = kyberpublickey_serialize(handle);
        assert!(serialized.length() > 0, "Serialized public key should not be empty");

        let retrieved_handle = kyberpublickey_deserialize(&serialized, 0, serialized.length() as usize);
        assert!(retrieved_handle != 0, "Deserialized public key handle should be valid");

        // Check equality
        assert!(kyberpublickey_equals(handle, retrieved_handle), "Handles should be equal");        
    }

    #[wasm_bindgen_test]
    fn handle_zero_invalid() {
        assert!(!kyberpublickey_equals(0, 0));
        assert_eq!(kyberpublickey_serialize(0).length(), 0);
        kyberpublickey_destroy(0); // should not panic
    }
    
    #[wasm_bindgen_test]
    fn deserialize_with_offset() {
        let mut rng = make_test_rng();
        let handle = generate_pubkey_handle(&mut rng);

        let serialized = kyberpublickey_serialize(handle);
        let mut buf = vec![0u8; 5];
        buf.extend_from_slice(&serialized.to_vec());

        let uarray = Uint8Array::from(buf.as_slice());
        let new_handle = kyberpublickey_deserialize(&uarray, 5, serialized.length() as usize);
        assert!(new_handle != 0);
        assert!(kyberpublickey_equals(handle, new_handle));
    }

    #[wasm_bindgen_test]
    fn destroy_public_key() {
        let mut rng = make_test_rng();
        let handle = generate_pubkey_handle(&mut rng);

        kyberpublickey_destroy(handle);
        let empty_serialized = kyberpublickey_serialize(handle);
        assert_eq!(empty_serialized.length(), 0, "Destroyed key should return empty serialization");
    } 
}

