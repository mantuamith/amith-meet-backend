use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_core::curve::{PrivateKey, PublicKey, KeyPair};

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::SeedableRng;
use getrandom;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::{store_public_key, with_public_key};
use std::panic::{catch_unwind, AssertUnwindSafe};

// ------------------------------
// Utility
// ------------------------------
fn vec_to_uint8array(data: &[u8]) -> Uint8Array {
    Uint8Array::from(data)
}

// -----------------------------------------------------------------------------
// Storage
// -----------------------------------------------------------------------------

static PRIVATE_KEYS: Lazy<Mutex<HandleTable<PrivateKey>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

pub fn store_key(pk: PrivateKey) -> u32 {
    PRIVATE_KEYS.lock().unwrap().insert(pk)
}

pub fn with_private_key<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&PrivateKey) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid EC private key handle (0)"));
    }

    let guard = PRIVATE_KEYS.lock().unwrap();

    // Check if handle exists
    if !guard.contains(ptr) {
        return Err(JsValue::from_str(
            "EC private key handle is invalid or has been destroyed",
        ));
    }

    // Safe to call with() because handle exists
    guard.with(ptr, f)
}

fn remove_key(ptr: u32) {
    if ptr == 0 {
        return;
    }

    PRIVATE_KEYS.lock().unwrap().remove(ptr);
}

// ===============================================================
// ================  WASM-BINDGEN EXPORTED FUNCTIONS =============
// ===============================================================

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_generate() -> u32 {
    let mut seed = [0u8; 32];
    if let Err(e) = getrandom::getrandom(&mut seed) {
        console::error_1(&format!("Random seed error: {e}").into());
        return 0;
    }

    let mut rng = ChaCha20Rng::from_seed(seed);
    let keypair = KeyPair::generate(&mut rng);

    store_key(keypair.private_key)
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_deserialize(bytes: &[u8]) -> u32 {
    match PrivateKey::deserialize(bytes) {
        Ok(key) => store_key(key),
        Err(_) => 0,
    }
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_destroy(ptr: u32) {
    remove_key(ptr);
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_serialize(ptr: u32) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr, |key| {
        Ok(vec_to_uint8array(&key.serialize()))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_sign(
    ptr: u32,
    message: &[u8],
) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr, |key| {
        if message.is_empty() {
            return Err(JsValue::from_str("ecprivatekey_sign: empty message"));
        }

        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed)
            .map_err(|e| JsValue::from_str(&format!("Random seed error: {e}")))?;
        let mut rng = ChaCha20Rng::from_seed(seed);

        let sig = key
            .calculate_signature(message, &mut rng)
            .map_err(|e| JsValue::from_str(&format!("sign failed: {e:?}")))?;

        Ok(vec_to_uint8array(&sig))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_agree(
    ptr_priv: u32,
    ptr_pub: u32,
) -> Result<Uint8Array, JsValue> {
    with_private_key(ptr_priv, |priv_key| {
        with_public_key(ptr_pub, |pub_key| {
            let shared = priv_key
                .calculate_agreement(pub_key)
                .map_err(|_| JsValue::from_str("ECDH agreement failed"))?;

            Ok(vec_to_uint8array(&shared))
        })
    })
}


#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_get_public_key(ptr: u32) -> Result<u32, JsValue> {
    with_private_key(ptr, |priv_key| {
        let pub_key = priv_key
            .public_key()
            .map_err(|e| JsValue::from_str(&format!("derive public key failed: {e}")))?;

        Ok(store_public_key(pub_key))
    })
}

#[wasm_bindgen(js_namespace = ecPrivateKey)]
pub fn ecprivatekey_hpke_open(
    _ptr: u32,
    _ciphertext: &[u8],
    _info: &[u8],
    _aad: &[u8],
) -> Result<Uint8Array, JsValue> {
    Err(JsValue::from_str(
        "HPKE is not supported in libsignal_protocol::PrivateKey",
    ))
}


#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen::JsValue;
    use wasm_bindgen_test::*;

    wasm_bindgen_test_configure!(run_in_browser);

    fn assert_js_ok<T>(res: Result<T, JsValue>) -> T {
        match res {
            Ok(v) => v,
            Err(e) => panic!("Unexpected JsValue error: {:?}", e),
        }
    }

    #[wasm_bindgen_test]
    fn generate_private_key() {
        let ptr = ecprivatekey_generate();
        assert!(ptr != 0);
    }

    #[wasm_bindgen_test]
    fn serialize_and_deserialize_private_key() {
        let ptr1 = ecprivatekey_generate();
        let bytes = assert_js_ok(ecprivatekey_serialize(ptr1));

        let ptr2 = ecprivatekey_deserialize(&bytes.to_vec());
        assert!(ptr2 != 0, "Deserialized private key handle must not be 0");

        // Serialize again to ensure key is valid
        let _ = assert_js_ok(ecprivatekey_serialize(ptr2));
    }

    #[wasm_bindgen_test]
    fn sign_message_success() {
        let ptr = ecprivatekey_generate();
        let msg = b"hello world";

        let sig = assert_js_ok(ecprivatekey_sign(ptr, msg));
        assert!(sig.length() > 0, "Signature must not be empty");
    }

    #[wasm_bindgen_test]
    fn sign_message_empty_fails() {
        let ptr = ecprivatekey_generate();
        let msg: &[u8] = &[];

        let res = ecprivatekey_sign(ptr, msg);
        assert!(res.is_err(), "Signing empty message must fail");
    }

    #[wasm_bindgen_test]
    fn get_public_key() {
        let priv_ptr = ecprivatekey_generate();
        let pub_ptr = assert_js_ok(ecprivatekey_get_public_key(priv_ptr));

        assert!(pub_ptr != 0, "Public key handle must not be 0");
    }

    #[wasm_bindgen_test]
    fn ecdh_agreement_success() {
        // Party A
        let priv_a = ecprivatekey_generate();
        let pub_a = assert_js_ok(ecprivatekey_get_public_key(priv_a));

        // Party B
        let priv_b = ecprivatekey_generate();
        let pub_b = assert_js_ok(ecprivatekey_get_public_key(priv_b));

        let shared_ab =
            assert_js_ok(ecprivatekey_agree(priv_a, pub_b)).to_vec();
        let shared_ba =
            assert_js_ok(ecprivatekey_agree(priv_b, pub_a)).to_vec();

        assert_eq!(
            shared_ab, shared_ba,
            "ECDH shared secrets must match"
        );
    }

    
    #[wasm_bindgen_test]
    fn destroy_private_key_invalidates_handle() {
        let ptr = ecprivatekey_generate();
        ecprivatekey_destroy(ptr);

        let res = ecprivatekey_serialize(ptr);
        assert!(res.is_err(), "Destroyed key must be invalid");
    }
   
    #[wasm_bindgen_test]
    fn serialize_invalid_handle_fails() {
        let res = ecprivatekey_serialize(0);
        assert!(res.is_err(), "Handle 0 must be rejected");
    }

    #[wasm_bindgen_test]
    fn hpke_open_not_supported() {
        let ptr = ecprivatekey_generate();

        let res = ecprivatekey_hpke_open(ptr, b"ct", b"info", b"aad");
        assert!(res.is_err(), "HPKE open must return error");
    }
}



