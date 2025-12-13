// wasm-wrapper/src/wasm_session_builder/converters.rs
use wasm_bindgen::{JsValue, JsCast};
use js_sys::Uint8Array;

use libsignal_protocol::{SessionRecord, IdentityKeyPair};

pub fn session_record_from_js(js: JsValue) -> Result<Option<SessionRecord>, String> {
    if js.is_null() || js.is_undefined() {
        return Ok(None);
    }

    if !js.is_instance_of::<Uint8Array>() {
        return Err("SessionRecord must be Uint8Array".into());
    }

    let arr = Uint8Array::from(js);
    let vec = arr.to_vec();

    SessionRecord::deserialize(&vec)
        .map(Some)
        .map_err(|e| format!("{:?}", e))
}

pub fn session_record_to_js(rec: &SessionRecord) -> Result<JsValue, String> {
    let bytes = rec.serialize().map_err(|e| format!("{:?}", e))?;
    Ok(Uint8Array::from(bytes.as_slice()).into())
}

pub fn identity_key_pair_from_js(js: JsValue) -> Result<IdentityKeyPair, String> {
    if !js.is_instance_of::<Uint8Array>() {
        return Err("IdentityKeyPair must be Uint8Array".into());
    }

    let bytes = Uint8Array::from(js).to_vec();

    IdentityKeyPair::try_from(bytes.as_slice())
        .map_err(|e| format!("{:?}", e))
}
