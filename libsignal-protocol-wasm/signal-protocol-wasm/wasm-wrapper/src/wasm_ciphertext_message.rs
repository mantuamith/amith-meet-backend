use wasm_bindgen::prelude::*;
use libsignal_protocol::CiphertextMessage;
use std::collections::HashMap;
use std::sync::Mutex;
use once_cell::sync::Lazy;
use web_sys::console;
use wasm_bindgen::JsValue;

use crate::handle_message_store::{HandleMessageStore};

static CIPHERTEXT_MESSAGES: Lazy<Mutex<HandleMessageStore<CiphertextMessage>>> =
    Lazy::new(|| Mutex::new(HandleMessageStore::new()));


pub fn store_ciphertext_message(msg: CiphertextMessage) -> u32 {
    CIPHERTEXT_MESSAGES.lock().unwrap().insert(msg)
}

pub fn take_ciphertext_message(
    handle: u32,
) -> Result<CiphertextMessage, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str(
            "Invalid CiphertextMessage handle (0)",
        ));
    }

    let mut store = CIPHERTEXT_MESSAGES.lock().unwrap();

    store
        .take(handle)
        .ok_or_else(|| JsValue::from_str("Invalid CiphertextMessage handle"))
}

#[wasm_bindgen(js_namespace = ciphertextMessage)]
pub fn ciphertextmessage_get_type(handle: u32) -> u32 {
    CIPHERTEXT_MESSAGES.lock().unwrap().with(handle, |msg| match msg {
        CiphertextMessage::SignalMessage(_) => 2,
        CiphertextMessage::PreKeySignalMessage(_) => 3,
        CiphertextMessage::SenderKeyMessage(_) => 7,
        CiphertextMessage::PlaintextContent(_) => 8,
    })
}

#[wasm_bindgen(js_namespace = ciphertextMessage)]
pub fn ciphertextmessage_get_signal_message(handle: u32) -> u32 {
    CIPHERTEXT_MESSAGES.lock().unwrap().with(handle, |msg| {
        match msg {
            CiphertextMessage::SignalMessage(m) => {
                crate::wasm_signal_message::store_signal_message(m.clone())
            }

            CiphertextMessage::PreKeySignalMessage(m) => {
                crate::wasm_pre_key_signal_message::store_prekey_signal_message(m.clone())
            }

            CiphertextMessage::SenderKeyMessage(_) => {
                console::error_1(
                    &"SenderKeyMessage is not supported by SessionCipher".into(),
                );
                0
            }

            CiphertextMessage::PlaintextContent(_) => {
                console::error_1(
                    &"PlaintextContent is not supported by SessionCipher".into(),
                );
                0
            }
        }
    })
}