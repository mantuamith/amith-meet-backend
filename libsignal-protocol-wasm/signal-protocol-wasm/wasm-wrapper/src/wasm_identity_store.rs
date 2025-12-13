use wasm_bindgen::prelude::*;
use wasm_bindgen::JsCast;
use wasm_bindgen_futures::JsFuture;

use js_sys::{Array, Function, Reflect, Uint8Array};

use async_trait::async_trait;

use libsignal_protocol::{
    IdentityKey, IdentityKeyPair, PrivateKey, PublicKey,
    ProtocolAddress, IdentityChange, Direction, SignalProtocolError,
};

type SignalResult<T> = std::result::Result<T, SignalProtocolError>;

/// Convert Rust string into SignalProtocolError
fn js_err(msg: impl Into<String>) -> SignalProtocolError {
    SignalProtocolError::InvalidArgument(msg.into())
}

/// Call JS method async and await if promise.
async fn call_js_method_async(
    target: &JsValue,
    method: &str,
    args: &[JsValue],
) -> Result<JsValue, JsValue> {
    let fn_val = Reflect::get(target, &JsValue::from_str(method))?;
    let func = fn_val
        .dyn_ref::<Function>()
        .ok_or_else(|| JsValue::from_str(&format!("JS method {} is not a function", method)))?;

    let arr = Array::new();
    for a in args {
        arr.push(a);
    }

    let res = func.apply(target, &arr)?;
    let promise = js_sys::Promise::resolve(&res);
    let fut = JsFuture::from(promise);

    fut.await.map_err(|e| e.into())
}

/// Convert JS Uint8Array or ArrayBuffer → Vec<u8>
fn js_to_vec_u8(input: &JsValue) -> SignalResult<Vec<u8>> {
    if input.is_null() || input.is_undefined() {
        return Err(js_err("expected Uint8Array, got null/undefined"));
    }

    if let Some(arr) = input.dyn_ref::<Uint8Array>() {
        let mut vec = vec![0u8; arr.length() as usize];
        arr.copy_to(&mut vec[..]);
        return Ok(vec);
    }

    let arr = Uint8Array::new(input);
    let mut vec = vec![0u8; arr.length() as usize];
    arr.copy_to(&mut vec[..]);
    Ok(vec)
}

/// Map Rust enum → JS string
fn direction_to_js_str(dir: Direction) -> &'static str {
    match dir {
        Direction::Sending => "Sending",
        Direction::Receiving => "Receiving",
    }
}

#[wasm_bindgen]
pub struct WasmIdentityKeyStore {
    js_store: JsValue,
}

#[wasm_bindgen]
impl WasmIdentityKeyStore {
    #[wasm_bindgen(constructor)]
    pub fn new(js_store: JsValue) -> WasmIdentityKeyStore {
        WasmIdentityKeyStore { js_store }
    }
}

#[async_trait(?Send)]
impl libsignal_protocol::storage::traits::IdentityKeyStore for WasmIdentityKeyStore {
    async fn get_identity_key_pair(&self) -> SignalResult<IdentityKeyPair> {
        let val = call_js_method_async(&self.js_store, "getIdentityKeyPair", &[])
            .await
            .map_err(|e| js_err(format!("JS: getIdentityKeyPair error: {:?}", e)))?;

        let pub_js = Reflect::get(&val, &"public_key".into())
            .map_err(|_| js_err("missing public_key"))?;

        let priv_js = Reflect::get(&val, &"private_key".into())
            .map_err(|_| js_err("missing private_key"))?;

        let public_bytes = js_to_vec_u8(&pub_js)?;
        let private_bytes = js_to_vec_u8(&priv_js)?;

        let public_key =
            PublicKey::deserialize(&public_bytes).map_err(|e| js_err(format!("{:?}", e)))?;

        let private_key =
            PrivateKey::deserialize(&private_bytes).map_err(|e| js_err(format!("{:?}", e)))?;

        Ok(IdentityKeyPair::new(IdentityKey::new(public_key), private_key))
    }

    async fn get_local_registration_id(&self) -> SignalResult<u32> {
        let v = call_js_method_async(&self.js_store, "getLocalRegistrationId", &[])
            .await
            .map_err(|e| js_err(format!("JS getLocalRegistrationId: {:?}", e)))?;

        let n = v
            .as_f64()
            .ok_or_else(|| js_err("registration id must be a number"))?;

        Ok(n as u32)
    }

    async fn save_identity(
        &mut self,
        address: &ProtocolAddress,
        identity: &IdentityKey,
    ) -> SignalResult<IdentityChange> {
        let name = JsValue::from_str(address.name());
        let device = JsValue::from_f64(u32::from(address.device_id()) as f64);

        let pk_bytes = identity.public_key().serialize();
        let pk_js = Uint8Array::from(pk_bytes.as_ref());

        let args = [name, device, JsValue::from(pk_js)];

        let ret = call_js_method_async(&self.js_store, "saveIdentity", &args)
            .await
            .map_err(|e| js_err(format!("JS saveIdentity: {:?}", e)))?;

        let replaced = ret.as_bool().unwrap_or(false);

        Ok(if replaced {
            IdentityChange::ReplacedExisting
        } else {
            IdentityChange::NewOrUnchanged
        })
    }

    async fn is_trusted_identity(
        &self,
        address: &ProtocolAddress,
        identity: &IdentityKey,
        direction: Direction,
    ) -> SignalResult<bool> {
        let name = JsValue::from_str(address.name());
        let device_id_u32: u32 = address.device_id().into();
        let device = JsValue::from_f64(device_id_u32 as f64);

        let pk_js = Uint8Array::from(identity.public_key().serialize().as_ref());
        let dir_js = JsValue::from_str(direction_to_js_str(direction));

        let args = [name, device, JsValue::from(pk_js), dir_js];

        let ret = call_js_method_async(&self.js_store, "isTrustedIdentity", &args)
            .await
            .map_err(|e| js_err(format!("JS isTrustedIdentity: {:?}", e)))?;

        Ok(ret.as_bool().unwrap_or(false))
    }

    async fn get_identity(&self, address: &ProtocolAddress) -> SignalResult<Option<IdentityKey>> {
        let name = JsValue::from_str(address.name());
        let device_id_u32: u32 = address.device_id().into();
        let device = JsValue::from_f64(device_id_u32 as f64);

        let args = [name, device];

        let ret = call_js_method_async(&self.js_store, "getIdentity", &args)
            .await
            .map_err(|e| js_err(format!("JS getIdentity: {:?}", e)))?;

        if ret.is_null() || ret.is_undefined() {
            return Ok(None);
        }

        let bytes = js_to_vec_u8(&ret)?;
        let pubkey = PublicKey::deserialize(&bytes)
            .map_err(|e| js_err(format!("deserialize error: {:?}", e)))?;

        Ok(Some(IdentityKey::new(pubkey)))
    }
}
