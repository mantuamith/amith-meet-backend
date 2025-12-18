use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::wasm_session_builder::adapters::{
    JsSessionStoreAdapter,
    JsIdentityStoreAdapter,
    JsPreKeyStoreAdapter,
    JsSignedPreKeyStoreAdapter,
    JsKyberPreKeyStoreAdapter,
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
use base64::engine::general_purpose::STANDARD;
use base64::Engine as _;

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

#[wasm_bindgen(js_namespace = sessionCipher)]
pub async fn sessioncipher_decrypt_prekey_signal_message(
    ciphertext_handle: u32,
    remote_address_handle: u32,
    session_store_handle: u32,
    identity_key_store_handle: u32,
    prekey_store_handle: u32,
    signed_prekey_store_handle: u32,
    kyber_prekey_store_handle: u32,
) -> Result<Uint8Array, JsValue> {
    console::log_1(&format!(
        "ciphertext bytes, base64 ",
    ).into());
    // 1. Resolve message
    let message = match crate::wasm_ciphertext_message::take_ciphertext_message(
        ciphertext_handle,
    ) {
        Ok(m) => m,
        Err(_) => return Ok(Uint8Array::new_with_length(0)),
    };

    let bytes: Vec<u8> = message.serialize().to_vec();
    let b64 = STANDARD.encode(&bytes);

    console::log_1(&format!(
        "ciphertext bytes len = {}, base64 = {}",
        bytes.len(),
        b64
    ).into());

    // 2. Resolve address
    let address = match crate::wasm_protocol_address::get_protocol_address_clone(
        remote_address_handle,
    ) {
        Ok(a) => a,
        Err(_) => return Ok(Uint8Array::new_with_length(0)),
    };

    // 3. Create store adapters
    let mut session_store = JsSessionStoreAdapter::new(session_store_handle);
    let mut identity_key_store = JsIdentityStoreAdapter::new(identity_key_store_handle);
    let mut prekey_store = JsPreKeyStoreAdapter::new(prekey_store_handle);
    let mut signed_prekey_store = JsSignedPreKeyStoreAdapter::new(signed_prekey_store_handle);
    let mut kyber_prekey_store = JsKyberPreKeyStoreAdapter::new(kyber_prekey_store_handle);

    // 4. RNG
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|_| JsValue::from_str("RNG failure"))?;
    let mut rng = rand_chacha::ChaCha20Rng::from_seed(seed);

    // 5. Decrypt
    match libsignal_protocol::message_decrypt(
        &message,
        &address,
        &mut session_store,
        &mut identity_key_store,
        &mut prekey_store,
        &mut signed_prekey_store,
        &mut kyber_prekey_store,
        &mut rng,
    )
    .await
    {
        Ok(plaintext) => Ok(Uint8Array::from(plaintext.as_slice())),
        Err(_) => Ok(Uint8Array::new_with_length(0)),
    }
}

/*
#[wasm_bindgen]
pub async fn sessioncipher_decrypt_signal_message(
    ciphertext_handle: u32,
    remote_address_handle: u32,
    session_store_handle: u32,
    identity_key_store_handle: u32,
) -> Uint8Array {
    // 1. Resolve SignalMessage synchronously
    let message = match crate::wasm_signal_message::get_signal_message_clone(
        ciphertext_handle,
    ) {
        Ok(m) => m,
        Err(_) => return Uint8Array::new_with_length(0),
    };

    // 2. Resolve ProtocolAddress synchronously
    let address = match crate::wasm_protocol_address::get_protocol_address_clone(
        remote_address_handle,
    ) {
        Ok(a) => a,
        Err(_) => return Uint8Array::new_with_length(0),
    };

    // 3. Validate store handles
    if session_store_handle == 0 || identity_key_store_handle == 0 {
        console::error_1(
            &"sessioncipher_decrypt_signal_message: store handle == 0".into(),
        );
        return Uint8Array::new_with_length(0);
    }

    // 4. Create store ADAPTERS (not records)
    let mut session_store =
        JsSessionStoreAdapter::new(session_store_handle);

    let mut identity_key_store =
        JsIdentityStoreAdapter::new(identity_key_store_handle);

    // 5. Decrypt
    match libsignal_protocol::message_decrypt(
        &message,
        &address,
        &mut session_store,
        &mut identity_key_store,
    )
    .await
    {
        Ok(plaintext) => Uint8Array::from(plaintext.as_slice()),
        Err(e) => {
            console::error_1(&format!("decrypt failed: {:?}", e).into());
            Uint8Array::new_with_length(0)
        }
    }
}
*/
