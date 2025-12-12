// wasm-wrapper/src/wasm_session_builder/mod.rs
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::future_to_promise;
use js_sys::{Promise};
use wasm_bindgen_futures::JsFuture;

use std::rc::Rc;
use std::time::{UNIX_EPOCH, Duration};

use crate::wasm_pre_key_bundle::get_prekeybundle_clone;
use crate::wasm_protocol_address::get_protocol_address_clone;
use libsignal_protocol::process_prekey_bundle;
mod adapters;
mod converters;

use adapters::{JsSessionStoreAdapter, JsIdentityStoreAdapter};

#[wasm_bindgen(js_name = sessionbuilder_process_prekey_bundle)]
pub fn sessionbuilder_process_prekey_bundle(
    prekey_ptr: u32,
    remote_ptr: u32,
    session_store_js: JsValue,
    identity_store_js: JsValue,
    now_ms: u64,
) -> Promise {
    let fut = async move {
        let bundle = get_prekeybundle_clone(prekey_ptr)
            .map_err(|e| JsValue::from_str(&format!("Invalid PreKeyBundle: {:?}", e)))?;

        let address = get_protocol_address_clone(remote_ptr)
            .map_err(|e| JsValue::from_str(&format!("Invalid ProtocolAddress: {:?}", e)))?;

        let now = UNIX_EPOCH + Duration::from_millis(now_ms);

        let mut session_store = JsSessionStoreAdapter::new(session_store_js, remote_ptr);
        let mut identity_store = JsIdentityStoreAdapter::new(identity_store_js, remote_ptr);

        process_prekey_bundle(
            &address,
            &mut session_store,
            &mut identity_store,
            &bundle,
            now,
        )
        .await
        .map_err(|e| JsValue::from_str(&format!("process_prekey_bundle failed: {:?}", e)))?;

        Ok(JsValue::undefined())
    };

    future_to_promise(fut)
}
