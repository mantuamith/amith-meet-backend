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
use crate::wasm_protocol_address::{get_protocol_address_clone};

use libsignal_protocol::error::Result as ProtocolResult;
use libsignal_protocol::{SignalProtocolError};

fn require_store_handle(name: &str, store_handle: u32) -> ProtocolResult<()> {
    if store_handle == 0 {
        let msg = format!(
            "[{}] called with store_handle == 0 (store not initialized)",
            name
        );

        // 🔊 Browser console error
        console::error_1(&msg.clone().into());

        // Signal-style failure
        return Err(SignalProtocolError::InvalidArgument(msg));
    }

    Ok(())
}

#[wasm_bindgen(js_namespace = sessionCipher)]
pub async fn sessioncipher_encrypt_message(
    plaintext: Uint8Array,
    remote_address_handle: u32,
    session_store_handle: u32,
    identity_key_store_handle: u32,
    now_millis: u64,
) -> Result<u32, JsValue> {
    let plaintext_vec = plaintext.to_vec();
    let now = UNIX_EPOCH + std::time::Duration::from_millis(now_millis);

    crate::wasm_protocol_address::with_protocol_address_async(
        remote_address_handle,
        |address| async move {

            let clonedAddress = get_protocol_address_clone(remote_address_handle)
                .map_err(|e| JsValue::from_str(&format!("Invalid ProtocolAddress: {:?}", e)))?;

            //let js_addr = crate::adapters::protocol_address_to_js(&clonedAddress);

            // Guard — MUST propagate with conversion
            require_store_handle(
                "sessioncipher_encrypt_message (session_store_handle)",
                session_store_handle,
            )
            .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            require_store_handle(
                "sessioncipher_encrypt_message (identity_store_handle)",
                identity_key_store_handle,
            )
            .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            let mut session_store =
                JsSessionStoreAdapter::new(session_store_handle);

            let mut identity_key_store =
                JsIdentityStoreAdapter::new(identity_key_store_handle);

            let mut seed = [0u8; 32];
            getrandom::getrandom(&mut seed)
                .map_err(|e| JsValue::from_str(&format!("Random seed error: {}", e)))?;

            let mut rng = ChaCha20Rng::from_seed(seed);

            let msg = libsignal_protocol::message_encrypt(
                &plaintext_vec,
                &address,
                &mut session_store,
                &mut identity_key_store,
                now,
                &mut rng,
            )
            .await
            .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            // ✅ ALWAYS return a valid ciphertext handle
            Ok(crate::wasm_ciphertext_message::store_ciphertext_message(msg))
        },
    )
    .await
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

