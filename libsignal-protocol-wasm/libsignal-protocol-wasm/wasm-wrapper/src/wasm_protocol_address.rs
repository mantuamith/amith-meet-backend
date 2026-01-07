use wasm_bindgen::prelude::*;
use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_core::address::ProtocolAddress;

use crate::HandleTable;

// -------------------------------------------------------
// Global handle table
// -------------------------------------------------------

static ADDRESSES: Lazy<Mutex<HandleTable<ProtocolAddress>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

// -------------------------------------------------------
// Internal helpers
// -------------------------------------------------------

pub fn store_address(addr: ProtocolAddress) -> u32 {
    ADDRESSES.lock().unwrap().insert(addr)
}

fn load_address(handle: u32) -> Result<ProtocolAddress, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("Null protocol address handle"));
    }

    ADDRESSES
        .lock()
        .unwrap()
        .take(handle)
        .map(|addr| {
            // put it back immediately (read-only semantics)
            let h = ADDRESSES.lock().unwrap().insert(addr.clone());
            debug_assert_eq!(h, handle);
            addr
        })
        .ok_or_else(|| JsValue::from_str("Invalid protocol address handle"))
}

pub fn with_protocol_address<F, R>(handle: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&ProtocolAddress) -> Result<R, JsValue>,
{
    if handle == 0 {
        return Err(JsValue::from_str("Invalid ProtocolAddress pointer"));
    }

    let table = ADDRESSES.lock().unwrap();

    if !table.contains(handle) {
        return Err(JsValue::from_str("Invalid ProtocolAddress pointer"));
    }

    Ok(table.with(handle, f)?)
}

pub async fn with_protocol_address_async<R, F, Fut>(
    handle: u32,
    f: F,
) -> Result<R, JsValue>
where
    F: FnOnce(ProtocolAddress) -> Fut,
    Fut: std::future::Future<Output = Result<R, JsValue>>,
{
    if handle == 0 {
        return Err(JsValue::from_str("Invalid ProtocolAddress pointer"));
    }

    // clone outside async
    let addr = ADDRESSES
        .lock()
        .unwrap()
        .with(handle, |a| a.clone());

    f(addr).await
}

pub fn get_protocol_address_clone(handle: u32) -> Result<ProtocolAddress, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null protocolAddress handle"));
    }

    let addr = ADDRESSES
        .lock()
        .unwrap()
        .with(handle, |a| a.clone());

    Ok(addr)
}
// destroy helper
fn remove_address(handle: u32) {
    if handle == 0 {
        return;
    }
    let _ = ADDRESSES.lock().unwrap().take(handle);
}

/// Convert &ProtocolAddress → handle (adapter use)
pub fn protocoladdress_to_handle(addr: &ProtocolAddress) -> Result<u32, JsValue> {
    Ok(store_address(addr.clone()))
}

// -------------------------------------------------------
// WASM EXPORTED FUNCTIONS
// -------------------------------------------------------

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_new(name: String, device_id: u32) -> Result<u32, JsValue> {
    let device_id = device_id.try_into().map_err(|_| {
        JsValue::from_str(&format!(
            "Invalid device id {} (must be 1–127)",
            device_id
        ))
    })?;

    Ok(store_address(ProtocolAddress::new(name, device_id)))
}

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_name(handle: u32) -> Result<String, JsValue> {
    with_protocol_address(handle, |addr| Ok(addr.name().to_string()))
}

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_device_id(handle: u32) -> Result<u32, JsValue> {
    with_protocol_address(handle, |addr| Ok(addr.device_id().into()))
}

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_destroy(handle: u32) {
    remove_address(handle);
}
