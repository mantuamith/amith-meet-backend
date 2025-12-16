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
            return (i + 1) as u32; // return (index + 1)
        }
    }

    table.push(Some(boxed));
    table.len() as u32  // return (index + 1)
}

fn load_address(ptr: u32) -> Result<ProtocolAddress, JsValue> {    
    if ptr == 0 {
        return Err(JsValue::from_str("Null protocol address handle"));
    }

    let table = ADDRESSES.lock().unwrap();
    table
        .get((ptr - 1) as usize)
        .and_then(|opt| opt.as_ref())
        .map(|boxed| (**boxed).clone())
        .ok_or_else(|| JsValue::from_str("Invalid protocol address handle"))
}

pub fn with_protocol_address<F, R>(ptr: u32, f: F) -> Result<R, JsValue>
where
    F: FnOnce(&ProtocolAddress) -> Result<R, JsValue>,
{
    if ptr == 0 {
        return Err(JsValue::from_str("Invalid ProtocolAddress pointer"));
    }

    let table = ADDRESSES.lock().unwrap();

    let key = table
        .get((ptr - 1) as usize)
        .and_then(|slot| slot.as_ref())
        .ok_or_else(|| JsValue::from_str("Invalid ProtocolAddress pointer"))?;

    // Borrow happens ONLY here
    f(key)
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

    // Extract OWNED ProtocolAddress
    let addr: ProtocolAddress = {
        let table = ADDRESSES.lock().unwrap();

        let boxed = table
            .get((handle - 1) as usize)
            .and_then(|slot| slot.as_ref())
            .ok_or_else(|| JsValue::from_str("Invalid ProtocolAddress pointer"))?;

        (**boxed).clone()
        // or equivalently: (*boxed).clone()
    };

    // Safe: owned value, no borrow, no lock
    f(addr).await
}

pub fn get_protocol_address_clone(handle: u32) -> Result<ProtocolAddress, JsValue> {
    if handle == 0 {
        return Err(JsValue::from_str("null protocolAddress handle"));
    }
    let table = ADDRESSES.lock().unwrap();
    let opt = table.get((handle - 1) as usize).and_then(|opt| opt.as_ref()).ok_or_else(|| JsValue::from_str("invalid protocolAddress handle"))?;
    // clone the protocolAddress (requires protocolAddress: Clone)
    Ok((**opt).clone())
}

fn remove_address(ptr: u32) {    
    if ptr == 0 {
        return;
    } 
    let mut table = ADDRESSES.lock().unwrap();
    if let Some(slot) = table.get_mut((ptr - 1) as usize) {
        *slot = None;
    }
}

/// Convert &ProtocolAddress → handle.
/// This is what your adapters expect.
pub fn protocoladdress_to_handle(addr: &ProtocolAddress) -> Result<u32, JsValue> {
    Ok(store_address(addr.clone()))
}

// -------------------------------------------------------
// WASM EXPORTED FUNCTIONS
// -------------------------------------------------------

#[wasm_bindgen(js_namespace = protocolAddress)]
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

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_name(ptr: u32) -> Result<String, JsValue> {
    let addr = load_address(ptr)?;
    Ok(addr.name().to_string())
}

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_device_id(ptr: u32) -> Result<u32, JsValue> {
    let addr = load_address(ptr)?;
    Ok(addr.device_id().into())
}

#[wasm_bindgen(js_namespace = protocolAddress)]
pub fn protocoladdress_destroy(ptr: u32) {
    remove_address(ptr);
}
