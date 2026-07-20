use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::SenderKeyRecord;

use crate::handle_table::HandleTable;

// ------------------------------------------------------------
// Handle table for SenderKeyRecord (handle 0 = invalid)
// ------------------------------------------------------------

static SENDER_KEY_RECORDS: Lazy<Mutex<HandleTable<SenderKeyRecord>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

fn table_lock<'a>() -> std::sync::MutexGuard<'a, HandleTable<SenderKeyRecord>> {
    SENDER_KEY_RECORDS
        .lock()
        .expect("sender key record table poisoned")
}

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------

fn store_sender_key_record(rec: SenderKeyRecord) -> u32 {
    table_lock().insert(rec)
}

fn take_sender_key_record(handle: u32) -> Option<SenderKeyRecord> {
    if handle == 0 {
        return None;
    }
    table_lock().take(handle)
}

fn get_sender_key_record_clone(handle: u32) -> Result<SenderKeyRecord, JsValue> {
    if handle == 0 {
        return Err(js_err("null senderkeyrecord handle"));
    }

    let table = table_lock();
    if !table.contains(handle) {
        return Err(js_err("invalid senderkeyrecord handle"));
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

#[wasm_bindgen(js_namespace = senderKeyRecord)]
pub fn senderkeyrecord_deserialize(bytes: &Uint8Array) -> u32 {
    let vec = bytes.to_vec();

    match SenderKeyRecord::deserialize(&vec) {
        Ok(rec) => store_sender_key_record(rec),
        Err(e) => {
            console::error_1(
                &format!("senderkeyrecord_deserialize failed: {:?}", e).into(),
            );
            0
        }
    }
}

#[wasm_bindgen(js_namespace = senderKeyRecord)]
pub fn senderkeyrecord_serialize(ptr: u32) -> Result<Uint8Array, JsValue> {
    let rec = get_sender_key_record_clone(ptr)?;
    let bytes = rec
        .serialize()
        .map_err(|e| js_err(format!("senderkeyrecord_serialize failed: {:?}", e)))?;
    Ok(vec_to_uint8array(bytes))
}

#[wasm_bindgen(js_namespace = senderKeyRecord)]
pub fn senderkeyrecord_destroy(ptr: u32) {
    if ptr == 0 {
        return;
    }
    let _ = take_sender_key_record(ptr);
}
