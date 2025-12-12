use wasm_bindgen::prelude::*;
use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_core::address::ProtocolAddress;

// -------------------------------------------------------
// Global handle table
// -------------------------------------------------------

static ADDRESSES: Lazy<Mutex<Vec<Option<Box<ProtocolAddress>>>>> = Lazy::new(|| {
    let mut v = Vec::new();
    v.push(None); // handle 0 = null
    Mutex::new(v)
});

fn store_address(addr: ProtocolAddress) -> u32 {
    let mut table = ADDRESSES.lock().unwrap();
    let boxed = Box::new(addr);

    // reuse empty slot
    for (i, slot) in table.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(boxed);
            return i as u32;
        }
    }

    table.push(Some(boxed));
    (table.len() - 1) as u32
}

fn load_address(ptr: u32) -> Result<ProtocolAddress, JsValue> {
    if ptr == 0 {
        return Err(JsValue::from_str("Null protocol address handle"));
    }

    let table = ADDRESSES.lock().unwrap();
    table
        .get(ptr as usize)
        .and_then(|opt| opt.as_ref())
        .map(|boxed| (**boxed).clone())
        .ok_or_else(|| JsValue::from_str("Invalid protocol address handle"))
}

fn remove_address(ptr: u32) {
    if ptr == 0 {
        return;
    }
    let mut table = ADDRESSES.lock().unwrap();
    if let Some(slot) = table.get_mut(ptr as usize) {
        *slot = None;
    }
}

// -------------------------------------------------------
// WASM EXPORTED FUNCTIONS
// -------------------------------------------------------

#[wasm_bindgen(js_namespace = ProtocolAddress)]
pub fn protocoladdress_new(name: String, device_id: u32) -> Result<u32, JsValue> {
    // Convert device_id into the internal type (1–127)
    let converted = device_id.try_into().map_err(|_err| {
        JsValue::from_str(&format!(
            "Invalid device id {} (must be 1–127)",
            device_id
        ))
    })?;

    let addr = ProtocolAddress::new(name.clone(), converted);

    Ok(store_address(addr))
}

#[wasm_bindgen(js_namespace = ProtocolAddress)]
pub fn protocoladdress_name(ptr: u32) -> Result<String, JsValue> {
    let addr = load_address(ptr)?;
    Ok(addr.name().to_string())
}

#[wasm_bindgen(js_namespace = ProtocolAddress)]
pub fn protocoladdress_device_id(ptr: u32) -> Result<u32, JsValue> {
    let addr = load_address(ptr)?;
    Ok(addr.device_id().into())
}

#[wasm_bindgen(js_namespace = ProtocolAddress)]
pub fn protocoladdress_destroy(ptr: u32) {
    remove_address(ptr);
}
