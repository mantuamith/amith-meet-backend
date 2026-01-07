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

    let table = KEM_SECRET_KEYS.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str(&format!(
            "Invalid Kyber secret key handle: {}",
            handle
        )));
    }

    table.with(handle, f)
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


#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use js_sys::Uint8Array;

    use libsignal_protocol::kem::KeyPair as KyberKeyPair;
    use rand_chacha::ChaCha20Rng;
    use rand_chacha::rand_core::{RngCore, CryptoRng};
    use rand::SeedableRng;

    wasm_bindgen_test_configure!(run_in_browser);

    /// Helper: deterministic RNG for testing
    fn make_test_rng() -> ChaCha20Rng {
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed).unwrap();
        ChaCha20Rng::from_seed(seed)
    }

    /// Generate a Kyber keypair and return the secret key handle
    fn generate_seckey_handle(rng: &mut ChaCha20Rng) -> u32 {
        let kp = KyberKeyPair::generate(libsignal_protocol::kem::KeyType::Kyber1024, rng);
        store_kyber_secret_key(kp.secret_key)
    }

    #[wasm_bindgen_test]
    fn store_and_retrieve_secret_key() {
        let mut rng = make_test_rng();
        let handle = generate_seckey_handle(&mut rng);

        let serialized = kybersecretkey_serialize(handle);
        assert!(serialized.length() > 0, "Serialized secret key should not be empty");

        let retrieved_handle = kybersecretkey_deserialize(&serialized.to_vec());
        assert!(retrieved_handle != 0, "Deserialized secret key handle should be valid");

        // Compare the serialized bytes
        let orig_bytes = kybersecretkey_serialize(handle).to_vec();
        let new_bytes = kybersecretkey_serialize(retrieved_handle).to_vec();
        assert_eq!(orig_bytes, new_bytes, "Serialized bytes should match");
    }

    #[wasm_bindgen_test]
    fn destroy_secret_key() {
        let mut rng = make_test_rng();
        let handle = generate_seckey_handle(&mut rng);

        // Destroy the key
        kybersecretkey_destroy(handle);

        // Serialization should now return empty
        let serialized = kybersecretkey_serialize(handle);
        assert_eq!(serialized.length(), 0, "Destroyed key should serialize to empty");
    }

    #[wasm_bindgen_test]
    fn handle_zero_is_invalid() {
        assert_eq!(kybersecretkey_serialize(0).length(), 0);
        kybersecretkey_destroy(0); // should not panic

        let result = with_kyber_secret_key(0, |_| Ok(()));
        assert!(result.is_err(), "Using handle 0 should return error");
    }
}

