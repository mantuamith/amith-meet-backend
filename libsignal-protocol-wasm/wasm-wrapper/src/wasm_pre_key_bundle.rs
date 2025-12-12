// src/wasm_prekeybundle.rs
use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use crate::wasm_ec_public_key; // assumed module that exposes get_public_key(...) -> Result<PublicKey, JsValue>
use crate::wasm_kem_public_key; // assumed module that exposes get_kem_public(...) -> Result<kem::PublicKey, JsValue>

use libsignal_protocol::{PreKeyBundle, PreKeyBundleContent}; // adjust paths to your crate
use libsignal_core::curve::{PublicKey, /* ... */};
use libsignal_protocol::kem as kem; // adjust according to your kem crate location

// -----------------------------------------------------------------------------
// Handle table for storing PreKeyBundle instances exposed to JS
// -----------------------------------------------------------------------------
static PREKEYBUNDLES: Lazy<Mutex<Vec<Option<Box<PreKeyBundle>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // index 0 reserved as null
    Mutex::new(v)
});

fn table_mut() -> std::sync::MutexGuard<'static, Vec<Option<Box<PreKeyBundle>>>> {
    PREKEYBUNDLES.lock().expect("prekeybundle table poisoned")
}

fn store_prekeybundle(pb: PreKeyBundle) -> u32 {
    let mut table = table_mut();
    let boxed = Box::new(pb);
    // reuse empty slots
    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i + 1) as u32; // return (index + 1)
        }
    }
    table.push(Some(boxed));
    table.len() as u32 // return (index + 1)
}

fn take_prekeybundle(handle: u32) -> Option<Box<PreKeyBundle>> {
    if handle == 0 { return None; }
    let mut table = table_mut();
    table.get_mut((handle - 1) as usize).and_then(|s| s.take())
}

pub fn get_prekeybundle_clone(handle: u32) -> Result<PreKeyBundle, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null prekeybundle handle"));
    }
    let table = PREKEYBUNDLES.lock().unwrap();
    let opt = table.get((handle - 1) as usize).and_then(|opt| opt.as_ref()).ok_or_else(|| JsValue::from_str("invalid prekeybundle handle"))?;
    // clone the PreKeyBundle (requires PreKeyBundle: Clone)
    Ok((**opt).clone())
}

fn js_uint8array_from_vec(v: Vec<u8>) -> Uint8Array {
    Uint8Array::from(v.as_slice())
}

// -----------------------------------------------------------------------------
// WASM exports
// -----------------------------------------------------------------------------

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_new(
    registration_id: u32,
    device_id: u32,
    pre_key_id: i32,      // -1 means None
    pre_key_ptr: u32,     // 0 means None
    signed_prekey_id: u32,
    signed_prekey_ptr: u32,
    signed_prekey_signature: &Uint8Array,
    identity_ptr: u32,
    kyber_prekey_id: u32,
    kyber_ptr: u32,
    kyber_prekey_signature: &Uint8Array,
) -> Result<u32, JsValue> {
    // Convert signatures to Vec<u8>
    let signed_sig = signed_prekey_signature.to_vec();
    let kyber_sig = kyber_prekey_signature.to_vec();

    // Get public keys from handles (these functions should return Result<PublicKey, JsValue>)
    // preKey (optional)
    let pre_key_opt: Option<(u32, PublicKey)> = if pre_key_id == -1 || pre_key_ptr == 0 {
        None
    } else {
        let pubk = crate::wasm_ec_public_key::get_public_key(pre_key_ptr)
            .map_err(|e| JsValue::from_str(&format!("prekey get_public_key failed: {:?}", e)))?;
        Some((pre_key_id as u32, pubk))
    };

    // signed prekey - required
    if signed_prekey_ptr == 0 {
        return Err(JsValue::from_str("signedPreKey pointer is null"));
    }
    let signed_pub = crate::wasm_ec_public_key::get_public_key(signed_prekey_ptr)
        .map_err(|e| JsValue::from_str(&format!("signedPreKey get_public_key failed: {:?}", e)))?;

    // identity key - required
    if identity_ptr == 0 {
        return Err(JsValue::from_str("identity pointer is null"));
    }
    let identity_pub = crate::wasm_ec_public_key::get_public_key(identity_ptr)
        .map_err(|e| JsValue::from_str(&format!("identity get_public_key failed: {:?}", e)))?;

    // kyber public - required
    if kyber_ptr == 0 {
        return Err(JsValue::from_str("kyber pointer is null"));
    }

    let kyber_pub = crate::wasm_kem_public_key::get_kyber_public_key(kyber_ptr)
        .ok_or_else(|| JsValue::from_str("kyber get_kyber_public failed: null or invalid pointer"))?;


    // Convert optional pre_key tuple into types expected by PreKeyBundle::new
    // NOTE: your PreKeyBundle::new signature expects specific types (PreKeyId, DeviceId, etc.)
    // We'll assume u32 -> PreKeyId/etc conversions are implemented via `into()`.

    // Convert device id into DeviceId type:
    let device_id = device_id
        .try_into()
        .map_err(|_| JsValue::from_str("invalid device id"))?;

    // Build arguments and call PreKeyBundle::new
    // YOUR PreKeyBundle::new signature in Rust (from your snippet) is:
    // PreKeyBundle::new(registration_id, device_id, pre_key, signed_pre_key_id, signed_pre_key_public, signed_pre_key_signature, kyber_pre_key_id, kyber_pre_key_public, kyber_pre_key_signature, identity_key)

    // Convert pre_key_opt from Option<(u32, PublicKey)> -> Option<(PreKeyId, PublicKey)>
    let pre_key_for_new = pre_key_opt.map(|(id, pk)| (id.into(), pk));

    // Wrap identity_pub into IdentityKey
    let identity_key = libsignal_protocol::IdentityKey::new(identity_pub);

    // Call constructor
    let bundle = PreKeyBundle::new(
        registration_id,
        device_id,
        pre_key_for_new,
        signed_prekey_id.into(),
        signed_pub,
        signed_sig,
        kyber_prekey_id.into(),
        kyber_pub,
        kyber_sig,
        identity_key,
    ).map_err(|e| JsValue::from_str(&format!("PreKeyBundle::new failed: {:?}", e)))?;

    let handle = store_prekeybundle(bundle);
    Ok(handle)
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_destroy(ptr: u32) {
    if ptr == 0 { return; }
    let _ = take_prekeybundle(ptr); // drop it by taking it out
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_device_id(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let dev = b.device_id()
        .map_err(|e| JsValue::from_str(&format!("device_id failed: {e}")))?;

    Ok(dev.into())
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_prekey_id(ptr: u32) -> Result<i32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let opt = b.pre_key_id()
        .map_err(|e| JsValue::from_str(&format!("pre_key_id failed: {e}")))?;

    Ok(opt.map(|id| u32::from(id) as i32).unwrap_or(-1))
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_prekey_public(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let opt = b.pre_key_public()
        .map_err(|e| JsValue::from_str(&format!("pre_key_public failed: {e}")))?;

    if let Some(pk) = opt {
        Ok(crate::wasm_ec_public_key::store_public_key(pk))
    } else {
        Ok(0)
    }
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_signed_prekey_id(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let id = b.signed_pre_key_id()
        .map_err(|e| JsValue::from_str(&format!("signed_pre_key_id failed: {e}")))?;

    Ok(id.into())
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_signed_prekey_public(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let pk = b.signed_pre_key_public()
        .map_err(|e| JsValue::from_str(&format!("signed_pre_key_public failed: {e}")))?;

    Ok(crate::wasm_ec_public_key::store_public_key(pk))
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_signed_prekey_signature(ptr: u32) -> Result<Uint8Array, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let sig = b.signed_pre_key_signature()
        .map_err(|e| JsValue::from_str(&format!("signed_pre_key_signature failed: {e}")))?;

    Ok(Uint8Array::from(sig))
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_identity_key(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let identity = b.identity_key()
        .map_err(|e| JsValue::from_str(&format!("identity_key failed: {e}")))?;

    let pk = identity.public_key().clone();
    Ok(crate::wasm_ec_public_key::store_public_key(pk))
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_registration_id(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let reg = b.registration_id()
        .map_err(|e| JsValue::from_str(&format!("registration_id failed: {e}")))?;

    Ok(reg)
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_kyber_prekey_id(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let id = b.kyber_pre_key_id()
        .map_err(|e| JsValue::from_str(&format!("kyber_pre_key_id failed: {e}")))?;

    Ok(id.into())
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_kyber_prekey_public(ptr: u32) -> Result<u32, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let pk = b.kyber_pre_key_public()
        .map_err(|e| JsValue::from_str(&format!("kyber_pre_key_public failed: {e}")))?;

    Ok(crate::wasm_kem_public_key::store_kyber_public_key(pk.clone()))
}

#[wasm_bindgen(js_namespace = preKeyBundle)]
pub fn prekeybundle_get_kyber_prekey_signature(ptr: u32) -> Result<Uint8Array, JsValue> {
    let b = get_prekeybundle_clone(ptr)?;

    let sig = b.kyber_pre_key_signature()
        .map_err(|e| JsValue::from_str(&format!("kyber_pre_key_signature failed: {e}")))?;

    Ok(Uint8Array::from(sig))
}

