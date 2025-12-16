use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::wasm_session_builder::adapters::{
    JsSessionStoreAdapter,
    JsIdentityStoreAdapter,
};

use crate::wasm_pre_key_signal_message::{
    store_prekey_signal_message
};

use web_sys::console;
use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use base64::engine::general_purpose::STANDARD as B64;
use getrandom;

use libsignal_protocol::CiphertextMessage;

fn store_ciphertext(msg: CiphertextMessage) -> u32 {
    match msg {
        CiphertextMessage::SignalMessage(m) => {
            crate::wasm_signal_message::store_signal_message(m)
        }

        CiphertextMessage::PreKeySignalMessage(m) => {
            crate::wasm_pre_key_signal_message::store_prekey_signal_message(m)
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
}

#[wasm_bindgen]
pub async fn message_encrypt(
    plaintext: Uint8Array,
    remote_address_handle: u32,
    session_store: JsValue,
    identity_key_store: JsValue,
    now_millis: u64,
) -> u32 {
    let plaintext_vec = plaintext.to_vec();
    let now = UNIX_EPOCH + std::time::Duration::from_millis(now_millis);

    let result = crate::wasm_protocol_address::with_protocol_address_async(
        remote_address_handle,
        |address| async move {
            let mut session_store =
                JsSessionStoreAdapter::new(session_store, remote_address_handle);

            let mut identity_key_store =
                JsIdentityStoreAdapter::new(identity_key_store, remote_address_handle);

            let mut seed = [0u8; 32];
            getrandom::getrandom(&mut seed).map_err(|e| {
                let msg = format!("Random seed error: {}", e);
                console::error_1(&msg.clone().into());
                JsValue::from_str(&msg)
            })?;

            let mut rng = ChaCha20Rng::from_seed(seed);

            let msg = libsignal_protocol::message_encrypt(
                &plaintext_vec,
                &address, // 👈 borrow from owned value
                &mut session_store,
                &mut identity_key_store,
                now,
                &mut rng,
            )
            .await
            .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            Ok::<u32, JsValue>(store_ciphertext(msg))
        },
    )
    .await;

    result.unwrap_or(0)
}

/*
#[wasm_bindgen]
pub async fn sessioncipher_decrypt_prekey_signal_message(
    ciphertext_handle: u32,
    remote_address_handle: u32,
    session_store: JsValue,
    identity_key_store: JsValue,
    prekey_store: JsValue,
    signed_prekey_store: JsValue,
    kyber_prekey_store: JsValue,
) -> Uint8Array {
    let result = with_prekey_signal_message(ciphertext_handle, |message| {
        with_protocol_address(remote_address_handle, |address| async {
            let mut session_store = js_session_store(session_store)?;
            let mut identity_key_store = js_identity_key_store(identity_key_store)?;
            let mut prekey_store = js_prekey_store(prekey_store)?;
            let mut signed_prekey_store =
                js_signed_prekey_store(signed_prekey_store)?;
            let mut kyber_prekey_store =
                js_kyber_prekey_store(kyber_prekey_store)?;

            let plaintext = SessionCipher_DecryptPreKeySignalMessage(
                message,
                address,
                &mut session_store,
                &mut identity_key_store,
                &mut prekey_store,
                &mut signed_prekey_store,
                &mut kyber_prekey_store,
            )
            .await?;

            Ok::<_, JsValue>(vec_to_uint8array(&plaintext))
        })
    })
    .await;

    result.unwrap_or_else(|_| Uint8Array::new_with_length(0))
}

#[wasm_bindgen]
pub async fn sessioncipher_decrypt_signal_message(
    ciphertext_handle: u32,
    remote_address_handle: u32,
    session_store: JsValue,
    identity_key_store: JsValue,
) -> Uint8Array {
    let result = with_signal_message(ciphertext_handle, |message| {
        with_protocol_address(remote_address_handle, |address| async {
            let mut session_store = js_session_store(session_store)?;
            let mut identity_key_store = js_identity_key_store(identity_key_store)?;

            let plaintext = message_decrypt(
                message,
                address,
                &mut session_store,
                &mut identity_key_store,
            )
            .await
            .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            Ok::<_, JsValue>(vec_to_uint8array(&plaintext))
        })
    })
    .await;

    result.unwrap_or_else(|_| Uint8Array::new_with_length(0))
} */

