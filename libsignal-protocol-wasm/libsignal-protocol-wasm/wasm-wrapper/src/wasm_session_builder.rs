// wasm-wrapper/src/wasm_session_builder/mod.rs
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::future_to_promise;
use js_sys::{Promise};
use wasm_bindgen_futures::JsFuture;

use std::rc::Rc;
use std::time::{UNIX_EPOCH, Duration};

use crate::wasm_prekey_bundle::get_prekeybundle_clone;
use crate::wasm_protocol_address::{get_protocol_address_clone};
use libsignal_protocol::process_prekey_bundle;

use rand_chacha::ChaCha20Rng;
use rand_chacha::rand_core::{SeedableRng, RngCore};
use getrandom;
use web_sys::console;

pub mod adapters;

use adapters::{JsSessionStoreAdapter, JsIdentityStoreAdapter};

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

#[wasm_bindgen(js_namespace = sessionBuilder)]
pub fn sessionbuilder_process_prekey_bundle(
    prekey_ptr: u32,
    remote_ptr: u32,
    session_store_handle: u32,
    identity_key_store_handle: u32,
    now_ms: u64,
) -> Promise {
    let fut = async move {
        let bundle = get_prekeybundle_clone(prekey_ptr)
            .map_err(|e| JsValue::from_str(&format!("Invalid PreKeyBundle: {:?}", e)))?;

        let address = get_protocol_address_clone(remote_ptr)
            .map_err(|e| JsValue::from_str(&format!("Invalid ProtocolAddress: {:?}", e)))?;

        let now = UNIX_EPOCH + Duration::from_millis(now_ms);

        console::log_1(
            &format!(
                "process_prekey_bundle: session_store_handle={}, identity_store_handle={}",
                session_store_handle, identity_key_store_handle
            ).into()
            );

        // Guard — MUST propagate with conversion
        require_store_handle(
            "sessionbuilder_process_prekey_bundle (session_store_handle)",
            session_store_handle,
        )
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

        require_store_handle(
            "sessionbuilder_process_prekey_bundle (identity_store_handle)",
            identity_key_store_handle,
        )
        .map_err(|e| JsValue::from_str(&format!("{:?}", e)))?;

        let mut session_store = JsSessionStoreAdapter::new(session_store_handle);
        let mut identity_store = JsIdentityStoreAdapter::new(identity_key_store_handle);
        
        // --- Generate 32 bytes of strong randomness ---
        let mut seed = [0u8; 32];
        getrandom::getrandom(&mut seed)
            .map_err(|e| JsValue::from_str(&format!("Random seed error: {}", e)))?;

        // --- Create a ChaCha20 RNG from the seed ---
        let mut rng = ChaCha20Rng::from_seed(seed);

        process_prekey_bundle(
            &address,
            &mut session_store,
            &mut identity_store,
            &bundle,
            now,
            &mut rng,
        )
        .await
        .map_err(|e| JsValue::from_str(&format!("process_prekey_bundle failed: {:?}", e)))?;

        Ok(JsValue::undefined())
    };

    future_to_promise(fut)
}
