use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use uuid::Uuid;

use crate::wasm_protocol_address::get_protocol_address_clone;
use crate::wasm_sender_key_store_adapter::SenderKeyStoreAdapter;
use crate::wasm_ciphertext_message::store_ciphertext_message;

use libsignal_protocol::{
    group_encrypt,
    group_decrypt,
    CiphertextMessage,
};

use web_sys::console;
use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use base64::engine::general_purpose::STANDARD as B64;
use getrandom;

use futures::executor::block_on;
// ------------------------------------------------------------
// WASM exports
// ------------------------------------------------------------

/// Encrypt a group message using SenderKey
///
/// JS signature:
/// groupCipher_encrypt_message(
///   senderPtr: number,
///   distributionId: string,
///   plaintext: Uint8Array,
///   senderKeyStoreHandle: number
/// ): number
#[wasm_bindgen(js_namespace = groupCipher)]
pub async fn groupcipher_encrypt_message(
    sender_ptr: u32,
    distribution_id: String,
    plaintext: Uint8Array,
    sender_key_store_handle: u32,
) -> Result<u32, JsValue> {
    // --- Resolve sender ---
    let sender = get_protocol_address_clone(sender_ptr)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    // --- Parse UUID ---
    let distribution_id = Uuid::parse_str(&distribution_id)
        .map_err(|_| JsValue::from_str("Invalid UUID"))?;

    // --- Plaintext ---
    let plaintext = plaintext.to_vec();

    // --- RNG ---
    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|_| JsValue::from_str("RNG failure"))?;
    let mut rng = ChaCha20Rng::from_seed(seed);

    // --- SenderKeyStore adapter (NO mutex held here) ---
    let mut adapter = SenderKeyStoreAdapter::new(sender_key_store_handle);

    // --- Encrypt (async, no blocking) ---
    let msg = group_encrypt(
        &mut adapter,
        &sender,
        distribution_id,
        &plaintext,
        &mut rng,
    )
    .await
    .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

    // --- Store ciphertext message ---
    Ok(store_ciphertext_message(
        CiphertextMessage::SenderKeyMessage(msg),
    ))
}

/// Decrypt a SenderKey group message
///
/// JS signature:
/// groupCipher_decrypt_message(
///   senderPtr: number,
///   senderKeyMessageBytes: Uint8Array,
///   senderKeyStoreHandle: number
/// ): Uint8Array
#[wasm_bindgen(js_namespace = groupCipher)]
pub async fn groupcipher_decrypt_message(
    sender_ptr: u32,
    sender_key_message_bytes: Uint8Array,
    sender_key_store_handle: u32,
) -> Result<Uint8Array, JsValue> {
    // --- Resolve sender ---
    let sender = get_protocol_address_clone(sender_ptr)
        .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    // --- Message bytes ---
    let message_bytes = sender_key_message_bytes.to_vec();

    // --- SenderKeyStore adapter (NO mutex, NO blocking) ---
    let mut adapter = SenderKeyStoreAdapter::new(sender_key_store_handle);

    // --- Decrypt (async) ---
    let plaintext = group_decrypt(
        &message_bytes,
        &mut adapter,
        &sender,
    )
    .await
    .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

    Ok(Uint8Array::from(plaintext.as_slice()))
}
