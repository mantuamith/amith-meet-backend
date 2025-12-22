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
    with_public_key(public_ptr, |public_curve| {
        with_public_key(other_public_ptr, |other_pub_curve| {
            with_private_key(private_ptr, |private_curve| {
                // Build our identity keypair
                let our_identity = IdentityKey::new(public_curve.clone());
                let ikp = IdentityKeyPair::new(
                    our_identity,
                    private_curve.clone(),
                );

                // Build the other identity
                let other_identity = IdentityKey::new(other_pub_curve.clone());

                let mut rng = new_crypto_rng()?;

                let sig = ikp
                    .sign_alternate_identity(&other_identity, &mut rng)
                    .map_err(|e| {
                        JsValue::from_str(&format!(
                            "sign_alternate_identity failed: {:?}",
                            e
                        ))
                    })?;

                Ok(vec_to_uint8array(sig.into_vec()))
            })
        })
    })
}
