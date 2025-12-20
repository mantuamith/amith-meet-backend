use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;
use std::time::{Duration, SystemTime};

use libsignal_protocol::{SessionRecord, SessionUsabilityRequirements};
use libsignal_core::curve::PublicKey;

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::with_public_key;

// ------------------------------------------------------------
// Handle table for SessionRecord (handle 0 = invalid)
// ------------------------------------------------------------

static SESSION_RECORDS: Lazy<Mutex<HandleTable<SessionRecord>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

fn table_lock<'a>() -> std::sync::MutexGuard<'a, HandleTable<SessionRecord>> {
    SESSION_RECORDS
        .lock()
        .expect("session record table poisoned")
}

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------

fn store_session_record(rec: SessionRecord) -> u32 {
    table_lock().insert(rec)
}

fn take_session_record(handle: u32) -> Option<SessionRecord> {
    if handle == 0 {
        return None;
    }
    table_lock().take(handle)
}

fn get_session_record_clone(handle: u32) -> Result<SessionRecord, JsValue> {
    if handle == 0 {
        return Err(js_err("null session record handle"));
    }

    let table = table_lock();
    if !table.contains(handle) {
        return Err(js_err("invalid session record handle"));
    }

    Ok(table.with(handle, |rec| rec.clone()))
}

fn vec_to_uint8array(v: Vec<u8>) -> Uint8Array {
    Uint8Array::from(v.as_slice())
}

fn js_err(msg: impl ToString) -> JsValue {
    JsValue::from_str(&msg.to_string())
}

// ------------------------------------------------------------
// WASM exports
// ------------------------------------------------------------

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_new_fresh() -> u32 {
    store_session_record(SessionRecord::new_fresh())
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

    let mut rec =
        take_session_record(ptr).ok_or_else(|| js_err("invalid sessionrecord handle"))?;

    rec.archive_current_state()
        .map_err(|e| js_err(format!("archive_current_state failed: {:?}", e)))?;

    store_session_record(rec);
    Ok(())
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_get_session_version(ptr: u32) -> u32 {
    match get_session_record_clone(ptr) {
        Ok(rec) => match rec.session_version() {
            Ok(v) => v,
            Err(e) => {
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
pub fn sessionrecord_get_local_identity_key_public(
    ptr: u32,
) -> Result<Uint8Array, JsValue> {
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
            let system_time = SystemTime::UNIX_EPOCH + Duration::from_millis(now_ms);
            match rec.has_usable_sender_chain(
                system_time,
                SessionUsabilityRequirements::NotStale,
            ) {
                Ok(v) => v,
                Err(e) => {
                    console::error_1(
                        &format!("has_usable_sender_chain failed: {:?}", e).into(),
                    );
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

    with_public_key(key_ptr, |pk: &PublicKey| {
        match rec.current_ratchet_key_matches(pk) {
            Ok(v) => Ok(v),
            Err(e) => {
                console::error_1(
                    &format!("current_ratchet_key_matches failed: {:?}", e).into(),
                );
                Ok(false)
            }
        }
    })
    .unwrap_or(false)
}

#[wasm_bindgen(js_namespace = sessionRecord)]
pub fn sessionrecord_serialize(ptr: u32) -> Result<Uint8Array, JsValue> {
    let rec = get_session_record_clone(ptr)?;
    let bytes = rec
        .serialize()
        .map_err(|e| js_err(format!("serialize failed: {:?}", e)))?;
    Ok(vec_to_uint8array(bytes))
}
