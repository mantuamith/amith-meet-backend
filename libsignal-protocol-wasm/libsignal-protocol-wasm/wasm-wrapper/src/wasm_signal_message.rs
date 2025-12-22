use std::sync::Mutex;
use once_cell::sync::Lazy;

use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use libsignal_protocol::{SignalMessage, IdentityKey};

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::{with_public_key, store_public_key};
use crate::utils::vec_to_uint8array;

// -----------------------------------------------------------------------------
// Storage
// -----------------------------------------------------------------------------

static SIGNAL_MESSAGES: Lazy<Mutex<HandleTable<SignalMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

pub fn store_signal_message(msg: SignalMessage) -> u32 {
    SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .insert(msg)
}

pub fn with_signal_message<R>(
    handle: u32,
    f: impl FnOnce(&SignalMessage) -> R,
) -> R {
    SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .with(handle, f)
}

pub fn remove_signal_message(handle: u32) {
    SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .remove(handle);
}

pub fn has_signal_message(handle: u32) -> bool {
    SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .contains(handle)
}

/// Consume-once access (Signal-correct lifecycle)
pub fn take_signal_message(
    handle: u32,
) -> Result<SignalMessage, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str(
            "Invalid SignalMessage handle (0)",
        ));
    }

    SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .take(handle)
        .ok_or_else(|| {
            JsValue::from_str("Invalid SignalMessage handle")
        })
}

// -----------------------------------------------------------------------------
// WASM bindings
// -----------------------------------------------------------------------------

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_deserialize(serialized: &[u8]) -> u32 {
    let msg = SignalMessage::try_from(serialized)
        .expect("Invalid SignalMessage");
    store_signal_message(msg)
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_destroy(handle: u32) {
    remove_signal_message(handle);
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_sender_ratchet_key(handle: u32) -> u32 {
    with_signal_message(handle, |m| {
        let pk = *m.sender_ratchet_key();
        store_public_key(pk)
    })
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_message_version(handle: u32) -> u32 {
    with_signal_message(handle, |m| {
        let bytes = m.serialized();
        if bytes.is_empty() {
            0
        } else {
            (bytes[0] >> 4) as u32
        }
    })
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_counter(handle: u32) -> u32 {
    with_signal_message(handle, |m| m.counter())
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_body(handle: u32) -> Vec<u8> {
    with_signal_message(handle, |m| m.body().to_vec())
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_pq_ratchet(handle: u32) -> Vec<u8> {
    with_signal_message(handle, |m| m.pq_ratchet().to_vec())
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_verify_mac(
    handle: u32,
    sender_identity_handle: u32,
    receiver_identity_handle: u32,
    mac_key: &[u8],
) -> bool {
    with_signal_message(handle, |msg| {
        with_public_key(sender_identity_handle, |sender_pk| {
            with_public_key(receiver_identity_handle, |receiver_pk| {
                Ok(
                    msg.verify_mac(
                        &IdentityKey::new(*sender_pk),
                        &IdentityKey::new(*receiver_pk),
                        mac_key,
                    )
                    .unwrap_or(false),
                )
            })
        })
        .unwrap_or(false)
    })
}

#[wasm_bindgen(js_namespace = signalMessage)]
pub fn signalmessage_get_serialized(handle: u32) -> Uint8Array {
    with_signal_message(handle, |m| {
        vec_to_uint8array(m.serialized())
    })
}
