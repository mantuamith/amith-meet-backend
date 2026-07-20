use wasm_bindgen::prelude::*;
use web_sys::console;

use uuid::Uuid;
use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::SeedableRng;
use getrandom;

use crate::wasm_protocol_address::get_protocol_address_clone;
use crate::wasm_sender_key_distribution_message::{
    take_sender_key_distribution_message,
    store_sender_key_distribution_message,
};
use crate::wasm_sender_key_store::SENDER_KEY_STORES;

use libsignal_protocol::{
    process_sender_key_distribution_message,
    create_sender_key_distribution_message,
};

use crate::wasm_sender_key_store_adapter::SenderKeyStoreAdapter;

// ------------------------------------------------------------
// WASM exports (ASYNC, CORRECT)
// ------------------------------------------------------------

/// RECEIVING side
#[wasm_bindgen(js_namespace = groupSessionBuilder)]
pub async fn groupsessionbuilder_process_sender_key_distribution_message(
    sender_ptr: u32,
    skdm_ptr: u32,
    sender_key_store_handle: u32,
) -> Result<(), JsValue> {
    let sender =
        get_protocol_address_clone(sender_ptr)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let skdm =
        take_sender_key_distribution_message(skdm_ptr)
            .ok_or_else(|| JsValue::from_str("Invalid SenderKeyDistributionMessage handle"))?;

    let mut adapter = SenderKeyStoreAdapter::new(sender_key_store_handle);

    process_sender_key_distribution_message(&sender, &skdm, &mut adapter)
        .await
        .map_err(|e| {
            console::error_1(
                &format!(
                    "process_sender_key_distribution_message failed: {:?}",
                    e
                )
                .into(),
            );
            JsValue::from_str(&format!("{:?}", e))
        })?;

    Ok(())
}

/// SENDING side
#[wasm_bindgen(js_namespace = groupSessionBuilder)]
pub async fn groupsessionbuilder_create_sender_key_distribution_message(
    sender_ptr: u32,
    distribution_id: String,
    sender_key_store_handle: u32,
) -> Result<u32, JsValue> {
    let sender =
        get_protocol_address_clone(sender_ptr)
            .map_err(|_| JsValue::from_str("Invalid ProtocolAddress handle"))?;

    let distribution_id = Uuid::parse_str(&distribution_id)
        .map_err(|_| JsValue::from_str("Invalid UUID"))?;

    let mut seed = [0u8; 32];
    getrandom::getrandom(&mut seed)
        .map_err(|e| JsValue::from_str(&format!("Random seed error: {}", e)))?;

    let mut rng = ChaCha20Rng::from_seed(seed);

    let mut adapter = SenderKeyStoreAdapter::new(sender_key_store_handle);

    let skdm = create_sender_key_distribution_message(
        &sender,
        distribution_id,
        &mut adapter,
        &mut rng,
    )
    .await
    .map_err(|e| {
        console::error_1(
            &format!(
                "create_sender_key_distribution_message failed: {:?}",
                e
            )
            .into(),
        );
        JsValue::from_str(&format!("{:?}", e))
    })?;

    Ok(store_sender_key_distribution_message(skdm))
}
