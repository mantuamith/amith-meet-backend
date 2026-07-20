use wasm_bindgen::prelude::*;
use libsignal_protocol::CiphertextMessage;
use std::collections::HashMap;
use std::sync::Mutex;
use once_cell::sync::Lazy;
use web_sys::console;
use wasm_bindgen::JsValue;

use crate::handle_table::{HandleTable};

static CIPHERTEXT_MESSAGES: Lazy<Mutex<HandleTable<CiphertextMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));


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
                crate::wasm_prekey_signal_message::store_prekey_signal_message(m.clone())
            }

            CiphertextMessage::SenderKeyMessage(m) => {
                crate::wasm_sender_key_message::store_sender_key_message(m.clone())
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


#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use wasm_bindgen::JsValue;

    use libsignal_protocol::{
        CiphertextMessage,
        CiphertextMessageType,
        DecryptionErrorMessage,
        PlaintextContent,
        Timestamp,
    };

    wasm_bindgen_test_configure!(run_in_browser);

    fn assert_js_ok<T>(res: Result<T, JsValue>) -> T {
        match res {
            Ok(v) => v,
            Err(e) => panic!("Unexpected JsValue error: {:?}", e),
        }
    }

    fn make_plaintext() -> PlaintextContent {
        // Create a *valid* PlaintextContent via DecryptionErrorMessage
        let error = DecryptionErrorMessage::for_original(
            b"\x01\x02\x03",                    // dummy original ciphertext
            CiphertextMessageType::SenderKey,   // avoids ratchet dependency
            Timestamp::from_epoch_millis(0),
            1,
        )
        .expect("create DecryptionErrorMessage");

        PlaintextContent::from(error)
    }

    // ------------------------------------------------------------
    // PlaintextContent storage / take
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn store_and_take_plaintext_ciphertext_message() {
        let msg = CiphertextMessage::PlaintextContent(make_plaintext());

        let handle = store_ciphertext_message(msg);
        assert!(handle != 0);

        let taken = assert_js_ok(take_ciphertext_message(handle));

        match taken {
            CiphertextMessage::PlaintextContent(pt) => {
                // body() is protocol-defined content, not arbitrary bytes
                assert!(!pt.body().is_empty());
            }
            _ => panic!("Expected PlaintextContent"),
        }
    }

    #[wasm_bindgen_test]
    fn take_ciphertext_message_consumes_handle() {
        let msg = CiphertextMessage::PlaintextContent(make_plaintext());
        let handle = store_ciphertext_message(msg);

        let _ = assert_js_ok(take_ciphertext_message(handle));
        let res = take_ciphertext_message(handle);

        assert!(res.is_err(), "Handle must be consumed");
    }

    // ------------------------------------------------------------
    // Type detection
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn ciphertextmessage_get_type_plaintext() {
        let msg = CiphertextMessage::PlaintextContent(make_plaintext());
        let handle = store_ciphertext_message(msg);

        let ty = ciphertextmessage_get_type(handle);
        assert_eq!(ty, 8, "PlaintextContent must map to type 8");
    }

    // ------------------------------------------------------------
    // Plaintext rejection path
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn get_signal_message_from_plaintext_returns_zero() {
        let msg = CiphertextMessage::PlaintextContent(make_plaintext());
        let handle = store_ciphertext_message(msg);

        let inner = ciphertextmessage_get_signal_message(handle);

        assert_eq!(
            inner, 0,
            "PlaintextContent must not produce a SignalMessage handle"
        );
    }

    // ------------------------------------------------------------
    // Invalid handle behavior
    // ------------------------------------------------------------

    #[wasm_bindgen_test]
    fn take_invalid_handle_fails() {
        let res = take_ciphertext_message(0);
        assert!(res.is_err());
    }
}
