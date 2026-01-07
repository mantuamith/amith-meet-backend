use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::SeedableRng;
use getrandom;

use libsignal_protocol::kem::{
    KeyPair as KyberKeyPair,
    KeyType,
};

use crate::handle_table::HandleTable;
use crate::wasm_kem_public_key::store_kyber_public_key;
use crate::wasm_kem_secret_key::store_kyber_secret_key;
use crate::wasm_kem_public_key::with_kyber_public_key;
use crate::wasm_kem_secret_key::with_kyber_secret_key;

// ======================================================================
// Configuration
// ======================================================================

const KYBER_KEY_TYPE: KeyType = KeyType::Kyber1024;

// ======================================================================
// Global handle table (0 = invalid)
// ======================================================================

static KYBER_KEYPAIRS: Lazy<Mutex<HandleTable<KyberKeyPair>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// ======================================================================
// Helpers
// ======================================================================

pub fn store_kem_keypair(pair: KyberKeyPair) -> u32 {
    KYBER_KEYPAIRS.lock().unwrap().insert(pair)
}

pub fn with_kyber_keypair<R>(
    handle: u32,
    f: impl FnOnce(&KyberKeyPair) -> Result<R, JsValue>,
) -> Result<R, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null KyberKeyPair handle"));
    }

    KYBER_KEYPAIRS
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn take_kem_keypair(handle: u32) -> Option<KyberKeyPair> {
    if handle == 0 {
        return None;
    }
    KYBER_KEYPAIRS.lock().unwrap().take(handle)
}

pub fn get_kyber_keypair_clone(handle: u32) -> Result<KyberKeyPair, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("Invalid KyberKeyPair handle (0)"));
    }

    let table = KYBER_KEYPAIRS.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str("Invalid KyberKeyPair handle"));
    }

    Ok(table.with(handle, |kp| kp.clone()))
}

// ======================================================================
// Utilities
// ======================================================================

fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ======================================================================
// WASM exports (matches KEMKeyPairWasm interface)
// ======================================================================

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_generate() -> Result<u32, JsValue> {
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| js_err(format!("Random seed error: {e}")))?;

    let mut rng = ChaCha20Rng::from_seed(seed);

    let keypair = KyberKeyPair::generate(KYBER_KEY_TYPE, &mut rng);

    Ok(store_kem_keypair(keypair))
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_from_keys(
    pub_key_handle: u32,
    sec_key_handle: u32,
) -> Result<u32, JsValue> {
    // Borrow public key first
    with_kyber_public_key(pub_key_handle, |public_key| {
        // Then borrow secret key (NO nested lock on same table — different tables are OK)
        with_kyber_secret_key(sec_key_handle, |secret_key| {
            // Construct KyberKeyPair from existing keys
            let keypair = KyberKeyPair::new(
                public_key.clone(),
                secret_key.clone(),
            );

            Ok(store_kem_keypair(keypair))
        })
    })
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_destroy(handle: u32) {
    let _ = take_kem_keypair(handle);
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_public_key(handle: u32) -> Result<u32, JsValue> {
    with_kyber_keypair(handle, |kp| {
        Ok(store_kyber_public_key(kp.public_key.clone()))
    })
}

#[wasm_bindgen(js_namespace = kemKeyPair)]
pub fn kyberkeypair_get_secret_key(handle: u32) -> Result<u32, JsValue> {
    with_kyber_keypair(handle, |kp| {
        Ok(store_kyber_secret_key(kp.secret_key.clone()))
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use wasm_bindgen::JsValue;
    use js_sys::Uint8Array;

    use libsignal_protocol::kem::KeyPair as KyberKeyPair;
    use rand_chacha::ChaCha20Rng;
    use rand_chacha::rand_core::{RngCore, CryptoRng};
    use rand::Rng; // needed for KyberKeyPair::generate

    use crate::wasm_kem_key_pair::*; // adjust as needed for wasm helpers

    const KYBER1024_PUBLIC_KEY_LEN: usize = 1568;
    const KYBER1024_SECRET_KEY_LEN: usize = 3168;

    wasm_bindgen_test_configure!(run_in_browser);

    /// Deterministic keypair generator for testing
    fn kyberkeypair_generate_with_rng<R>(rng: &mut R) -> Result<u32, JsValue>
    where
        R: RngCore + CryptoRng, // must implement both
    {
        let keypair = KyberKeyPair::generate(KYBER_KEY_TYPE, rng);
        Ok(store_kem_keypair(keypair))
    }

    /// Helper: create a deterministic RNG for testing
    fn make_test_rng() -> ChaCha20Rng {
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed).unwrap();
        ChaCha20Rng::from_seed(seed)
    }

    #[wasm_bindgen_test]
    fn generate_and_clone_keypair() {
        let mut rng = make_test_rng();
        let handle = kyberkeypair_generate_with_rng(&mut rng).expect("generate keypair");
        assert!(handle != 0);

        let kp_clone = get_kyber_keypair_clone(handle).expect("clone keypair");

        assert_eq!(
            kp_clone.public_key.serialize()[1..].len(),
            KYBER1024_PUBLIC_KEY_LEN
        );
        assert_eq!(
            kp_clone.secret_key.serialize()[1..].len(),
            KYBER1024_SECRET_KEY_LEN
        );
    }

    #[wasm_bindgen_test]
    fn public_and_secret_keys() {
        let mut rng = make_test_rng();
        let handle = kyberkeypair_generate_with_rng(&mut rng).unwrap();
        let pub_handle = kyberkeypair_get_public_key(handle).unwrap();
        let sec_handle = kyberkeypair_get_secret_key(handle).unwrap();

        with_kyber_public_key(pub_handle, |pk| {
            assert_eq!(
                pk.serialize()[1..].len(),
                KYBER1024_PUBLIC_KEY_LEN

            );
            Ok(())
        }).unwrap();

        with_kyber_secret_key(sec_handle, |sk| {
            assert_eq!(
                sk.serialize()[1..].len(),
                KYBER1024_SECRET_KEY_LEN
            );
            Ok(())
        }).unwrap();
    }

    #[wasm_bindgen_test]
    fn reconstruct_from_keys() {
        let mut rng = make_test_rng();
        let handle = kyberkeypair_generate_with_rng(&mut rng).unwrap();
        let pub_handle = kyberkeypair_get_public_key(handle).unwrap();
        let sec_handle = kyberkeypair_get_secret_key(handle).unwrap();

        let new_handle = kyberkeypair_from_keys(pub_handle, sec_handle).unwrap();

        let original = get_kyber_keypair_clone(handle).unwrap();
        let reconstructed = get_kyber_keypair_clone(new_handle).unwrap();

        assert_eq!(
            original.public_key.serialize(),
            reconstructed.public_key.serialize()
        );
        assert_eq!(
            original.secret_key.serialize(),
            reconstructed.secret_key.serialize()
        );
    }

    #[wasm_bindgen_test]
    fn destroy_keypair() {
        let mut rng = make_test_rng();
        let handle = kyberkeypair_generate_with_rng(&mut rng).unwrap();
        kyberkeypair_destroy(handle);

        assert!(get_kyber_keypair_clone(handle).is_err());
    }

    #[wasm_bindgen_test]
    fn handle_zero_invalid() {
        assert!(get_kyber_keypair_clone(0).is_err());
        assert!(with_kyber_keypair(0, |_| Ok(())).is_err());
        assert!(take_kem_keypair(0).is_none());
    }
}
