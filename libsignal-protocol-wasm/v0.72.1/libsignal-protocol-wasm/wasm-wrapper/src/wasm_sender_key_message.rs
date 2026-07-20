use std::sync::Mutex;
use once_cell::sync::Lazy;

use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use libsignal_protocol::SenderKeyMessage;

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::store_public_key;

// -----------------------------------------------------------------------------
// Storage
// -----------------------------------------------------------------------------

static SENDER_KEY_MESSAGES: Lazy<Mutex<HandleTable<SenderKeyMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

pub fn store_sender_key_message(msg: SenderKeyMessage) -> u32 {
    SENDER_KEY_MESSAGES
        .lock()
        .unwrap()
        .insert(msg)
}

pub fn take_sender_key_message(
    handle: u32,
) -> Result<SenderKeyMessage, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str(
            "Invalid SenderKeyMessage handle (0)",
        ));
    }

    SENDER_KEY_MESSAGES
        .lock()
        .unwrap()
        .take(handle)
        .ok_or_else(|| {
            JsValue::from_str("Invalid SenderKeyMessage handle")
        })
}

pub fn with_sender_key_message<R>(
    handle: u32,
    f: impl FnOnce(&SenderKeyMessage) -> R,
) -> R {
    SENDER_KEY_MESSAGES
        .lock()
        .unwrap()
        .with(handle, f)
}

fn remove_message(handle: u32) {
    SENDER_KEY_MESSAGES
        .lock()
        .unwrap()
        .remove(handle);
}

pub fn has_sender_key_message(handle: u32) -> bool {
    SENDER_KEY_MESSAGES
        .lock()
        .unwrap()
        .contains(handle)
}

// -----------------------------------------------------------------------------
// WASM exports
// -----------------------------------------------------------------------------

/// Deserialize a SenderKeyMessage from bytes
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_deserialize(serialized: &[u8]) -> u32 {
    let msg = SenderKeyMessage::try_from(serialized)
        .expect("Invalid SenderKeyMessage");

    store_sender_key_message(msg)
}

/// Destroy a SenderKeyMessage handle
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_destroy(handle: u32) {
    remove_message(handle);
}

/// Get distribution UUID (string)
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_get_distribution_id(handle: u32) -> String {
    with_sender_key_message(handle, |m| {
        m.distribution_id().to_string()
    })
}

/// Get sender chain ID
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_get_chain_id(handle: u32) -> u32 {
    with_sender_key_message(handle, |m| m.chain_id())
}

/// Get message iteration
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_get_iteration(handle: u32) -> u32 {
    with_sender_key_message(handle, |m| m.iteration())
}

/// Get ciphertext bytes
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_get_ciphertext(handle: u32) -> Vec<u8> {
    with_sender_key_message(handle, |m| {
        m.ciphertext().to_vec()
    })
}

/// Verify the message signature
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_verify_signature(
    handle: u32,
    public_key_handle: u32,
) -> bool {
    let public_key =
        crate::wasm_ec_public_key::get_public_key_clone(public_key_handle)
            .expect("Invalid PublicKey handle");

    with_sender_key_message(handle, |m| {
        m.verify_signature(&public_key)
            .expect("Invalid SenderKeyMessage signature")
    })
}

/// Get serialized form
#[wasm_bindgen(js_namespace = senderKeyMessage)]
pub fn senderkeymessage_get_serialized(handle: u32) -> Vec<u8> {
    with_sender_key_message(handle, |m| {
        m.serialized().to_vec()
    })
}
