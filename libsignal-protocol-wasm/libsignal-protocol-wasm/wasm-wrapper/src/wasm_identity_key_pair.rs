// wasm_identity_keypair.rs
use wasm_bindgen::prelude::*;
use js_sys::{Uint8Array, Object, Reflect};
use wasm_bindgen::JsValue;
use web_sys::console;

// adapt these imports to your crate layout
use libsignal_core::curve::{PublicKey, PrivateKey};
use libsignal_protocol::{IdentityKey, IdentityKeyPair};

/// helpers from other modules (adjust names if necessary)
use crate::wasm_ec_public_key::{store_public_key, with_public_key};
use crate::wasm_ec_private_key::{store_key, with_private_key};

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{RngCore, SeedableRng};

fn new_crypto_rng() -> Result<ChaCha20Rng, JsValue> {
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| JsValue::from_str(&format!("RNG seed error: {}", e)))?;
    Ok(ChaCha20Rng::from_seed(seed))
}

fn vec_to_uint8array(v: Vec<u8>) -> Uint8Array {
    Uint8Array::from(v.as_slice())
}

// -------------------------
// identitykeypair_deserialize
// -------------------------
// Accepts serialized identity-keypair bytes and returns a JS object:
// { publicKeyPtr: number, privateKeyPtr: number }
#[wasm_bindgen(js_namespace = identityKeyPair)]
pub fn identitykeypair_deserialize(bytes: &Uint8Array) -> Result<Object, JsValue> {
    let vec = bytes.to_vec();

    // Correct deserialization API
    let ikp = IdentityKeyPair::try_from(vec.as_slice())
        .map_err(|e| JsValue::from_str(&format!("IdentityKeyPair::try_from failed: {e}")))?;

    // Extract keys
    let public_curve: PublicKey = ikp.public_key().clone();
    let private_curve: PrivateKey = ikp.private_key().clone();

    // Store into WASM handle tables
    let public_handle = store_public_key(public_curve);
    let private_handle = store_key(private_curve);

    // Build return JS object
    let obj = Object::new();
    Reflect::set(
        &obj,
        &JsValue::from_str("publicKeyPtr"),
        &JsValue::from_f64(public_handle as f64),
    )?;
    Reflect::set(
        &obj,
        &JsValue::from_str("privateKeyPtr"),
        &JsValue::from_f64(private_handle as f64),
    )?;

    Ok(obj)
}

// -------------------------
// identitykeypair_serialize
// -------------------------
// Accepts two handles (publicPtr, privatePtr) and returns Uint8Array containing the serialized pair.

#[wasm_bindgen(js_namespace = identityKeyPair)]
pub fn identitykeypair_serialize(
    public_ptr: u32,
    private_ptr: u32,
) -> Result<Uint8Array, JsValue> {
    with_public_key(public_ptr, |public_curve| {
        with_private_key(private_ptr, |private_curve| {
            // Build IdentityKeyPair inside the closures
            let identity_public = IdentityKey::new(public_curve.clone());
            let ikp = IdentityKeyPair::new(identity_public, private_curve.clone());

            let serialized = ikp.serialize();              // Box<[u8]>
            Ok(vec_to_uint8array(serialized.into_vec()))  // Vec<u8>
        })
    })
}


// -------------------------
// identitykeypair_sign_alternate_identity
// -------------------------
// Use the given (publicPtr, privatePtr) to sign `otherPublicPtr` and return signature bytes.

#[wasm_bindgen(js_namespace = identityKeyPair)]
pub fn identitykeypair_sign_alternate_identity(
    public_ptr: u32,
    private_ptr: u32,
    other_public_ptr: u32,
) -> Result<Uint8Array, JsValue> {
    // Fetch all key references first (single lock each)
    let public_curve = with_public_key(public_ptr, |k| Ok(k.clone()))?;
    let other_pub_curve = with_public_key(other_public_ptr, |k| Ok(k.clone()))?;
    let private_curve = with_private_key(private_ptr, |k| Ok(k.clone()))?;

    // Build our identity keypair
    let our_identity = IdentityKey::new(public_curve);
    let ikp = IdentityKeyPair::new(our_identity, private_curve);

    // Build the other identity
    let other_identity = IdentityKey::new(other_pub_curve);

    // RNG
    let mut rng = new_crypto_rng()?;

    // Sign
    let sig = ikp
        .sign_alternate_identity(&other_identity, &mut rng)
        .map_err(|e| {
            JsValue::from_str(&format!("sign_alternate_identity failed: {:?}", e))
        })?;

    Ok(vec_to_uint8array(sig.into_vec()))
}


#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use wasm_bindgen::JsValue;
    use js_sys::Uint8Array;

    use libsignal_protocol::{IdentityKeyPair, IdentityKey};
    use libsignal_core::curve::KeyPair;

    wasm_bindgen_test_configure!(run_in_browser);

    fn assert_js_ok<T>(res: Result<T, JsValue>) -> T {
        match res {
            Ok(v) => v,
            Err(e) => panic!("Unexpected JsValue error: {:?}", e),
        }
    }

    fn make_identity_keypair_bytes() -> Vec<u8> {
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed)
            .expect("failed to seed RNG");

        let mut rng = ChaCha20Rng::from_seed(seed);
        let kp = KeyPair::generate(&mut rng);

        let ikp = IdentityKeyPair::new(
            IdentityKey::new(kp.public_key),
            kp.private_key,
        );

        ikp.serialize().into_vec()
    }

    // ------------------------------------------------------------
    // identitykeypair_deserialize
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn deserialize_creates_valid_handles() {
        let bytes = make_identity_keypair_bytes();
        let js_bytes = Uint8Array::from(bytes.as_slice());

        let obj = assert_js_ok(identitykeypair_deserialize(&js_bytes));

        let pub_ptr = js_sys::Reflect::get(&obj, &JsValue::from_str("publicKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let priv_ptr = js_sys::Reflect::get(&obj, &JsValue::from_str("privateKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        assert!(pub_ptr != 0, "public key handle must be non-zero");
        assert!(priv_ptr != 0, "private key handle must be non-zero");
    }

    // ------------------------------------------------------------
    // identitykeypair_serialize
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn serialize_round_trip_matches_original() {
        let original = make_identity_keypair_bytes();
        let js_bytes = Uint8Array::from(original.as_slice());

        let obj = assert_js_ok(identitykeypair_deserialize(&js_bytes));

        let pub_ptr = js_sys::Reflect::get(&obj, &JsValue::from_str("publicKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let priv_ptr = js_sys::Reflect::get(&obj, &JsValue::from_str("privateKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let serialized = assert_js_ok(identitykeypair_serialize(pub_ptr, priv_ptr));
        let roundtrip = serialized.to_vec();

        assert_eq!(
            original, roundtrip,
            "serialized IdentityKeyPair must round-trip exactly"
        );
    }

    // ------------------------------------------------------------
    // identitykeypair_sign_alternate_identity
    // ------------------------------------------------------------
    
    #[wasm_bindgen_test]
    fn sign_alternate_identity_produces_signature() {
        let bytes_a = make_identity_keypair_bytes();
        let bytes_b = make_identity_keypair_bytes();

        let a = assert_js_ok(identitykeypair_deserialize(
            &Uint8Array::from(bytes_a.as_slice()),
        ));

        let b = assert_js_ok(identitykeypair_deserialize(
            &Uint8Array::from(bytes_b.as_slice()),
        ));

        let a_pub = js_sys::Reflect::get(&a, &JsValue::from_str("publicKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let a_priv = js_sys::Reflect::get(&a, &JsValue::from_str("privateKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let b_pub = js_sys::Reflect::get(&b, &JsValue::from_str("publicKeyPtr"))
            .unwrap()
            .as_f64()
            .unwrap() as u32;

        let sig = assert_js_ok(identitykeypair_sign_alternate_identity(
            a_pub,
            a_priv,
            b_pub,
        ));

        let sig_bytes = sig.to_vec();

        assert!(
            !sig_bytes.is_empty(),
            "signature must not be empty"
        );
    }

    // ------------------------------------------------------------
    // Invalid handle behavior
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn serialize_invalid_handles_fails() {
        let res = identitykeypair_serialize(0, 0);
        assert!(res.is_err(), "invalid handles must error");
    }

    
    #[wasm_bindgen_test]
    fn sign_invalid_handles_fails() {
        let res = identitykeypair_sign_alternate_identity(0, 0, 0);
        assert!(res.is_err(), "invalid handles must error");
    } 
}

