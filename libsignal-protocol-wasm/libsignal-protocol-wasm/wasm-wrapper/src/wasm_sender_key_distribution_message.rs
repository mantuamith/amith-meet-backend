use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use web_sys::console;

use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::SenderKeyDistributionMessage;
use libsignal_protocol::error::SignalProtocolError;

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::{store_public_key};


static SKDM_TABLE: Lazy<Mutex<HandleTable<SenderKeyDistributionMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

fn with_table<R>(
    f: impl FnOnce(&mut HandleTable<SenderKeyDistributionMessage>) -> R,
) -> R {
    let mut table = SKDM_TABLE
        .lock()
        .expect("SenderKeyDistributionMessage table poisoned");
    f(&mut table)
}

// ------------------------------------------------------------
// Internal helpers (used by GroupSessionBuilder)
// ------------------------------------------------------------

pub fn store_sender_key_distribution_message(
    msg: SenderKeyDistributionMessage,
) -> u32 {
    with_table(|table| table.insert(msg))
}

pub fn take_sender_key_distribution_message(
    ptr: u32,
) -> Option<SenderKeyDistributionMessage> {
    if ptr == 0 {
        return None;
    }

    with_table(|table| table.take(ptr))
}

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------

fn js_err(e: impl ToString) -> JsValue {
    JsValue::from_str(&e.to_string())
}

// ------------------------------------------------------------
// WASM exports
// ------------------------------------------------------------

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_deserialize(
    bytes: &Uint8Array,
) -> Result<u32, JsValue> {
    let data = bytes.to_vec();

    let msg = SenderKeyDistributionMessage::try_from(data.as_slice())
        .map_err(|e| js_err(format!("deserialize failed: {:?}", e)))?;

    Ok(with_table(|table| table.insert(msg)))
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_destroy(ptr: u32) {
    if ptr == 0 {
        return;
    }

    with_table(|table| {
        table.remove(ptr);
    });
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_serialized(
    ptr: u32,
) -> Result<Uint8Array, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            Ok(Uint8Array::from(msg.serialized()))
        })
    })
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_distribution_id(
    ptr: u32,
) -> Result<String, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            msg.distribution_id()
                .map(|id| id.to_string())
                .map_err(|e| js_err(format!("{:?}", e)))
        })
    })
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_chain_id(
    ptr: u32,
) -> Result<u32, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            msg.chain_id()
                .map_err(|e| js_err(format!("{:?}", e)))
        })
    })
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_iteration(
    ptr: u32,
) -> Result<u32, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            msg.iteration()
                .map_err(|e| js_err(format!("{:?}", e)))
        })
    })
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_chain_key(
    ptr: u32,
) -> Result<Uint8Array, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            msg.chain_key()
                .map(|k| Uint8Array::from(k))
                .map_err(|e| js_err(format!("{:?}", e)))
        })
    })
}

#[wasm_bindgen(js_namespace = senderKeyDistributionMessage)]
pub fn senderkeydistributionmessage_get_signature_key(
    ptr: u32,
) -> Result<u32, JsValue> {
    with_table(|table| {
        table.with(ptr, |msg| {
            msg.signing_key()
                .map(|pk| store_public_key(*pk))
                .map_err(|e| js_err(format!("{:?}", e)))
        })
    })
}

