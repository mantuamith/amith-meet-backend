// wasm-wrapper/src/wasm_session_record.rs
use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::time::{Duration, SystemTime};

use libsignal_protocol::{SessionRecord, SessionUsabilityRequirements}; // adjust path if necessary
use libsignal_core::curve::PublicKey; // adjust if your PublicKey lives elsewhere

use crate::wasm_ec_public_key; // must expose get_public_key(handle: u32) -> Result<PublicKey, JsValue>

// ------------------------------------------------------------
// Handle table for SessionRecord (0 = null)
// ------------------------------------------------------------
static SESSION_RECORDS: Lazy<Mutex<Vec<Option<Box<SessionRecord>>>>> = Lazy::new(|| {
    let mut v: Vec<Option<Box<SessionRecord>>> = Vec::new();
    v.push(None);
    Mutex::new(v)
});

fn table_lock<'a>() -> std::sync::MutexGuard<'a, Vec<Option<Box<SessionRecord>>>> {
    SESSION_RECORDS.lock().expect("session record table poisoned")
}

fn store_session_record(rec: SessionRecord) -> u32 {
    let mut table = table_lock();
    let boxed = Box::new(rec);

    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return (i - 1) as u32; // return (index + 1)
        }
    }

    table.push(Some(boxed));
    table.len() as u32 // return (index + 1)
}

fn take_session_record(handle: u32) -> Option<Box<SessionRecord>> {
    if handle == 0 {
        return None;
    }
    let mut table = table_lock();
    table.get_mut((handle - 1) as usize).and_then(|slot| slot.take())
}

/// Clone a SessionRecord (SessionRecord is `Clone` in your snippet)
fn get_session_record_clone(handle: u32) -> Result<SessionRecord, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null session record handle"));
    }
    let table = SESSION_RECORDS.lock().unwrap();
    let opt = table
        .get((handle - 1) as usize)
        .and_then(|slot| slot.as_ref())
        .ok_or_else(|| JsValue::from_str("invalid session record handle"))?;
    Ok((**opt).clone())
}

fn vec_to_uint8array(v: Vec<u8>) -> Uint8Array {
    Uint8Array::from(v.as_slice())
}

fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ----------------- wasm exports -----------------

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_new_fresh() -> u32 {
    let rec = SessionRecord::new_fresh();
    store_session_record(rec)
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_destroy(ptr: u32) {
    if ptr == 0 {
        return;
    }
    let _ = take_session_record(ptr);
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_deserialize(bytes: &Uint8Array) -> u32 {
    let vec = bytes.to_vec();
    match SessionRecord::deserialize(&vec) {
        Ok(rec) => store_session_record(rec),
        Err(e) => {
            console::error_1(&format!("sessionrecord_deserialize failed: {:?}", e).into());
            0
        }
    }
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_archive_current_state(ptr: u32) -> Result<(), JsValue> {
    if ptr == 0 {
        return Err(js_err("null sessionrecord handle"));
    }

    // Acquire mutable reference by taking the boxed value, calling method, then putting it back.
    // This avoids exposing &mut across threads.
    let boxed = take_session_record(ptr).ok_or_else(|| js_err("invalid sessionrecord handle"))?;
    let mut rec = *boxed;
    rec.archive_current_state()
        .map_err(|e| js_err(format!("archive_current_state failed: {:?}", e)))?;
    // store it back
    let _ = store_session_record(rec);
    Ok(())
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_session_version(ptr: u32) -> u32 {
    match get_session_record_clone(ptr) {
        Ok(rec) => match rec.session_version() {
            Ok(v) => v,
            Err(e) => {
                // Android compatibility: return 0 for invalid state; log others
                console::warn_1(&format!("session_version error: {:?}", e).into());
                0
            }
        },
        Err(_) => 0,
    }
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_remote_registration_id(ptr: u32) -> Result<u32, JsValue> {
    let rec = get_session_record_clone(ptr)?;
    rec.remote_registration_id()
        .map_err(|e| js_err(format!("remote_registration_id failed: {:?}", e)))
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_local_registration_id(ptr: u32) -> Result<u32, JsValue> {
    let rec = get_session_record_clone(ptr)?;
    rec.local_registration_id()
        .map_err(|e| js_err(format!("local_registration_id failed: {:?}", e)))
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_remote_identity_key_public(ptr: u32) -> Option<Uint8Array> {
    match get_session_record_clone(ptr) {
        Ok(rec) => match rec.remote_identity_key_bytes() {
            Ok(Some(bytes)) => Some(vec_to_uint8array(bytes)),
            Ok(None) => None,
            Err(e) => {
                console::error_1(&format!("remote_identity_key_bytes failed: {:?}", e).into());
                None
            }
        },
        Err(e) => {
            console::error_1(&format!("invalid handle: {:?}", e).into());
            None
        }
    }
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_local_identity_key_public(ptr: u32) -> Result<Uint8Array, JsValue> {
    let rec = get_session_record_clone(ptr)?;
    let v = rec
        .local_identity_key_bytes()
        .map_err(|e| js_err(format!("local_identity_key_bytes failed: {:?}", e)))?;
    Ok(vec_to_uint8array(v))
}

/// nowMs is milliseconds since epoch (Date.now())
#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_has_usable_sender_chain(ptr: u32, now_ms: u64) -> bool {
    match get_session_record_clone(ptr) {
        Ok(rec) => {
            // Build SystemTime from epoch millis
            let system_time = SystemTime::UNIX_EPOCH + Duration::from_millis(now_ms);
            match rec.has_usable_sender_chain(system_time, SessionUsabilityRequirements::NotStale) {
                Ok(v) => v,
                Err(e) => {
                    console::error_1(&format!("has_usable_sender_chain failed: {:?}", e).into());
                    false
                }
            }
        }
        Err(e) => {
            console::error_1(&format!("invalid handle: {:?}", e).into());
            false
        }
    }
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_current_ratchet_key_matches(ptr: u32, key_ptr: u32) -> bool {
    if ptr == 0 || key_ptr == 0 {
        return false;
    }

    let rec = match get_session_record_clone(ptr) {
        Ok(r) => r,
        Err(_) => return false,
    };

    // retrieve PublicKey by handle (assumes wasm_ec_public_key::get_public_key returns Result<PublicKey, JsValue>)
    let pk = match crate::wasm_ec_public_key::get_public_key(key_ptr) {
        Ok(p) => p,
        Err(e) => {
            console::error_1(&format!("get_public_key failed: {:?}", e).into());
            return false;
        }
    };

    match rec.current_ratchet_key_matches(&pk) {
        Ok(v) => v,
        Err(e) => {
            console::error_1(&format!("current_ratchet_key_matches failed: {:?}", e).into());
            false
        }
    }
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_serialize(ptr: u32) -> Result<Uint8Array, JsValue> {
    let rec = get_session_record_clone(ptr)?;
    let bytes = rec
        .serialize()
        .map_err(|e| js_err(format!("serialize failed: {:?}", e)))?;
    Ok(vec_to_uint8array(bytes))
}
