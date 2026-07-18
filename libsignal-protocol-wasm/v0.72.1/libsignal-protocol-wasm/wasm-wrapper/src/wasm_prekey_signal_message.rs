use std::sync::Mutex;
use once_cell::sync::Lazy;

use wasm_bindgen::prelude::*;
use libsignal_protocol::{
    PreKeySignalMessage,
    SignalMessage,
};

use crate::handle_table::HandleTable;
use crate::wasm_ec_public_key::store_public_key;
use crate::wasm_signal_message::store_signal_message;

// -----------------------------------------------------------------------------
// Storage
// -----------------------------------------------------------------------------

static PREKEY_SIGNAL_MESSAGES: Lazy<Mutex<HandleTable<PreKeySignalMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

pub fn store_prekey_signal_message(msg: PreKeySignalMessage) -> u32 {
    PREKEY_SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .insert(msg)
}

pub fn take_prekey_signal_message(
    handle: u32,
) -> Result<PreKeySignalMessage, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str(
            "Invalid PreKeySignalMessage handle (0)",
        ));
    }

    PREKEY_SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .take(handle)
        .ok_or_else(|| {
            JsValue::from_str("Invalid PreKeySignalMessage handle")
        })
}

pub fn with_prekey_signal_message<R>(
    handle: u32,
    f: impl FnOnce(&PreKeySignalMessage) -> R,
) -> R {
    PREKEY_SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .with(handle, f)
}

fn remove_message(handle: u32) {
    PREKEY_SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .remove(handle);
}

pub fn has_prekey_signal_message(handle: u32) -> bool {
    PREKEY_SIGNAL_MESSAGES
        .lock()
        .unwrap()
        .contains(handle)
}

// -----------------------------------------------------------------------------
// WASM exports
// -----------------------------------------------------------------------------

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_deserialize(serialized: &[u8]) -> u32 {
    let msg = PreKeySignalMessage::try_from(serialized)
        .expect("Invalid PreKeySignalMessage");

    store_prekey_signal_message(msg)
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_destroy(handle: u32) {
    remove_message(handle);
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_version(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| {
        let bytes = m.serialized();
        if bytes.is_empty() {
            0
        } else {
            (bytes[0] >> 4) as u32
        }
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_identity_key(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| {
        let pk = *m.identity_key().public_key();
        store_public_key(pk)
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_registration_id(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| m.registration_id())
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_pre_key_id(handle: u32) -> i32 {
    with_prekey_signal_message(handle, |m| {
        m.pre_key_id()
            .and_then(|id| {
                let v: u32 = id.into();
                i32::try_from(v).ok()
            })
            .unwrap_or(-1)
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_signed_pre_key_id(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| {
        let id: u32 = m.signed_pre_key_id().into();
        id
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_base_key(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| {
        let pk = *m.base_key();
        store_public_key(pk)
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_get_signal_message(handle: u32) -> u32 {
    with_prekey_signal_message(handle, |m| {
        let msg: SignalMessage = m.message().clone();
        store_signal_message(msg)
    })
}

#[wasm_bindgen(js_namespace = preKeySignalMessage)]
pub fn prekeysignalmessage_serialize(handle: u32) -> Vec<u8> {
    with_prekey_signal_message(handle, |m| m.serialized().to_vec())
}
