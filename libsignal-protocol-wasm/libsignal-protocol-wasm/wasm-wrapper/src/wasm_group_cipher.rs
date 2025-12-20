use wasm_bindgen::prelude::*;
use js_sys::Uint8Array;

use uuid::Uuid;

use crate::wasm_protocol_address::get_protocol_address_clone;
use crate::wasm_sender_key_store::with_sender_key_store_mut_blocking;
use crate::wasm_sender_key_store_adapter::WasmSenderKeyStore;
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
    let sender =
        get_protocol_address_clone(sender_ptr)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let distribution_id = Uuid::parse_str(&distribution_id)
        .map_err(|_| JsValue::from_str("Invalid UUID"))?;

    let plaintext = plaintext.to_vec();

    let ciphertext = with_sender_key_store_mut_blocking(
        sender_key_store_handle,
        |store| {
            let mut adapter = WasmSenderKeyStore::new(store);

            let mut seed = [0u8; 32];
            getrandom::getrandom(&mut seed)
                .map_err(|_| JsValue::from_str("RNG failure"))?;
            let mut rng = ChaCha20Rng::from_seed(seed);

            let fut = group_encrypt(
                &mut adapter,
                &sender,
                distribution_id,
                &plaintext,
                &mut rng,
            );

            let msg = futures::executor::block_on(fut)
                .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

            Ok(CiphertextMessage::SenderKeyMessage(msg))
        },
    )?;

    Ok(store_ciphertext_message(ciphertext))
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
    let sender =
        get_protocol_address_clone(sender_ptr)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let message_bytes = sender_key_message_bytes.to_vec();

    let plaintext = with_sender_key_store_mut_blocking(
        sender_key_store_handle,
        |store| {
            let mut adapter = WasmSenderKeyStore::new(store);

            let fut = group_decrypt(
                &message_bytes,
                &mut adapter,
                &sender,
            );

            futures::executor::block_on(fut)
                .map_err(|e| JsValue::from_str(&format!("{:?}", e)))
        },
    )?;

    Ok(Uint8Array::from(plaintext.as_slice()))
}
