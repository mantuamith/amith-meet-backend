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


#[cfg(test)]
mod tests {
    use super::*;
    use wasm_bindgen_test::*;
    use libsignal_core::DeviceId;

    wasm_bindgen_test::wasm_bindgen_test_configure!(run_in_browser);

    #[wasm_bindgen_test]
    fn test_store_and_with_protocol_address() {
        let device_id: DeviceId = 42_u32.try_into().unwrap();
        let addr = ProtocolAddress::new("alice".into(), device_id);
        let handle = store_address(addr.clone());

        let read_addr = with_protocol_address(handle, |a| Ok(a.clone())).unwrap();
        assert_eq!(read_addr.name(), "alice");
        assert_eq!(<u32>::from(read_addr.device_id()), 42);

        remove_address(handle);
        assert!(with_protocol_address(handle, |_| Ok(())).is_err());
    }

    #[wasm_bindgen_test]
    fn test_protocoladdress_destroy() {
        let device_id: DeviceId = 7_u32.try_into().unwrap();
        let addr = ProtocolAddress::new("bob".into(), device_id);
        let handle = store_address(addr);

        remove_address(handle);

        let result = with_protocol_address(handle, |_| Ok(()));
        assert!(result.is_err());
    }

    #[wasm_bindgen_test]
    fn test_get_protocol_address_clone() {
        let device_id: DeviceId = 10_u32.try_into().unwrap();
        let addr = ProtocolAddress::new("carol".into(), device_id);
        let handle = store_address(addr.clone());

        let cloned = with_protocol_address(handle, |a| Ok(a.clone())).unwrap();
        assert_eq!(cloned.name(), "carol");
        assert_eq!(<u32>::from(cloned.device_id()), 10);
    }

    #[wasm_bindgen_test]
    fn test_multiple_addresses() {
        let addr1 = ProtocolAddress::new("dave".into(), 5_u32.try_into().unwrap());
        let addr2 = ProtocolAddress::new("eve".into(), 1_u32.try_into().unwrap());

        let handle1 = store_address(addr1.clone());
        let handle2 = store_address(addr2.clone());

        let read1 = with_protocol_address(handle1, |a| Ok(a.clone())).unwrap();
        let read2 = with_protocol_address(handle2, |a| Ok(a.clone())).unwrap();

        assert_eq!(read1.name(), "dave");
        assert_eq!(<u32>::from(read1.device_id()), 5);
        assert_eq!(read2.name(), "eve");
        assert_eq!(<u32>::from(read2.device_id()), 1);

        remove_address(handle1);
        remove_address(handle2);

        assert!(with_protocol_address(handle1, |_| Ok(())).is_err());
        assert!(with_protocol_address(handle2, |_| Ok(())).is_err());
    }

    #[wasm_bindgen_test]
    fn test_format_address_string() {
        let addr = ProtocolAddress::new("frank".into(), 12_u32.try_into().unwrap());
        let handle = store_address(addr.clone());

        let formatted = with_protocol_address(handle, |addr| {
            Ok(format!("{}-{}", addr.name(), <u32>::from(addr.device_id())))
        })
        .unwrap();

        assert_eq!(formatted, "frank-12");

        remove_address(handle);
    }
}
