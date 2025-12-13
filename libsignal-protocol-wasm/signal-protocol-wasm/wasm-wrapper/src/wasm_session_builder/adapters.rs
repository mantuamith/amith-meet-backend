// wasm-wrapper/src/wasm_session_builder/adapters.rs
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::JsFuture;
use js_sys::{Promise, Uint8Array, Reflect, Function};
use wasm_bindgen::JsCast;

use async_trait::async_trait;

use libsignal_protocol::{
    SessionStore, IdentityKeyStore, SessionRecord, ProtocolAddress, IdentityKey, IdentityKeyPair,
    Direction, SignalProtocolError, IdentityChange,
};
use libsignal_protocol::error::Result as ProtocolResult;

use crate::wasm_session_builder::converters::{
    session_record_from_js, session_record_to_js, identity_key_pair_from_js,
};

/// Adapter used when JS provides a SessionStore object. The adapter keeps a `remote_handle`
/// precomputed (number) that JS expects to receive when methods are called.
#[derive(Clone)]
pub struct JsSessionStoreAdapter {
    obj: JsValue,
    /// A numeric handle to identify the remote address on the JS side.
    /// (This prevents us from depending on a missing `protocoladdress_to_handle` helper.)
    remote_handle: u32,
}

impl JsSessionStoreAdapter {
    pub fn new(obj: JsValue, remote_handle: u32) -> Self {
        Self { obj, remote_handle }
    }
}

#[async_trait(?Send)]
impl SessionStore for JsSessionStoreAdapter {
    /// load_session(remote) -> Promise<null | serializedSessionRecord>
    async fn load_session(
        &self,
        _addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<SessionRecord>> {
        let method = Reflect::get(&self.obj, &JsValue::from_str("loadSession"))
            .map_err(|_| SignalProtocolError::InvalidArgument("loadSession missing".into()))?;
        let func = Function::from(method);

        let promise = func
            .call1(&self.obj, &JsValue::from_f64(self.remote_handle as f64))
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!(
                    "loadSession call failed: {:?}",
                    e
                ))
            })?;

        let js_val = JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!(
                    "loadSession promise rejected: {:?}",
                    e
                ))
            })?;

        session_record_from_js(js_val).map_err(|e| {
            SignalProtocolError::InvalidArgument(format!("session_record_from_js failed: {:?}", e))
        })
    }

    /// store_session(remote, record) -> Promise<void>
    async fn store_session(
        &mut self,
        _remote: &ProtocolAddress,
        record: &SessionRecord,
    ) -> ProtocolResult<()> {
        let js_record = session_record_to_js(record).map_err(|e| {
            SignalProtocolError::InvalidArgument(format!("session_record_to_js failed: {:?}", e))
        })?;

        let method = Reflect::get(&self.obj, &JsValue::from_str("storeSession"))
            .map_err(|_| SignalProtocolError::InvalidArgument("storeSession missing".into()))?;
        let func = Function::from(method);

        let promise = func
            .call2(
                &self.obj,
                &JsValue::from_f64(self.remote_handle as f64),
                &js_record,
            )
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("storeSession call failed: {:?}", e))
            })?;

        JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("storeSession promise rejected: {:?}", e))
            })?;

        Ok(())
    }

    // If the SessionStore trait requires more async methods, add them here.
}

/// Adapter used when JS provides an IdentityKeyStore object.
/// Methods call into JS and await Promises.
#[derive(Clone)]
pub struct JsIdentityStoreAdapter {
    obj: JsValue,
    remote_handle: u32,
}

impl JsIdentityStoreAdapter {
    pub fn new(obj: JsValue, remote_handle: u32) -> Self {
        Self { obj, remote_handle }
    }
}

#[async_trait(?Send)]
impl IdentityKeyStore for JsIdentityStoreAdapter {
    /// save_identity(remote, identity) -> Promise<void> (but trait expects IdentityChange)
   async fn save_identity(
        &mut self,
        addr: &ProtocolAddress,
        identity: &IdentityKey,
    ) -> ProtocolResult<IdentityChange> {

        let remote_handle =
            crate::wasm_protocol_address::protocoladdress_to_handle(addr)
                .map_err(|e| SignalProtocolError::InvalidArgument(format!("{:?}", e)))?;

        let js_identity = Uint8Array::from(identity.serialize().as_ref());

        let method = Reflect::get(&self.obj, &JsValue::from_str("saveIdentity"))
            .map_err(|e| SignalProtocolError::InvalidArgument(format!("{:?}", e)))?;

        let func = Function::from(method);

        let promise = func
            .call2(&self.obj,
                &JsValue::from_f64(remote_handle as f64),
                &JsValue::from(js_identity))
            .map_err(|e| SignalProtocolError::InvalidArgument(format!("{:?}", e)))?;

        JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| SignalProtocolError::InvalidArgument(format!("{:?}", e)))?;

        // MUST return valid enum variant → use Added or Changed
        Ok(IdentityChange::NewOrUnchanged)
    }

    /// get_identity_key_pair() -> Promise<{ public: Uint8Array, private: Uint8Array }>
    async fn get_identity_key_pair(&self) -> ProtocolResult<IdentityKeyPair> {
        let method = Reflect::get(&self.obj, &JsValue::from_str("getIdentityKeyPair"))
            .map_err(|_| SignalProtocolError::InvalidArgument("getIdentityKeyPair missing".into()))?;
        let func = Function::from(method);

        let promise = func
            .call0(&self.obj)
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("getIdentityKeyPair call failed: {:?}", e))
            })?;

        let js_val = JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("getIdentityKeyPair promise rejected: {:?}", e))
            })?;

        identity_key_pair_from_js(js_val).map_err(|e| {
            SignalProtocolError::InvalidArgument(format!("identity_key_pair_from_js failed: {:?}", e))
        })
    }

    /// get_local_registration_id() -> Promise<number>
    async fn get_local_registration_id(&self) -> ProtocolResult<u32> {
        let method = Reflect::get(&self.obj, &JsValue::from_str("getLocalRegistrationId"))
            .map_err(|_| SignalProtocolError::InvalidArgument("getLocalRegistrationId missing".into()))?;
        let func = Function::from(method);

        let promise = func.call0(&self.obj).map_err(|e| {
            SignalProtocolError::InvalidArgument(format!("getLocalRegistrationId call failed: {:?}", e))
        })?;

        let js_val = JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("getLocalRegistrationId promise rejected: {:?}", e))
            })?;

        js_val.as_f64()
            .map(|v| v as u32)
            .ok_or_else(|| SignalProtocolError::InvalidArgument("expected number".into()))
    }

    /// get_identity(remote) -> Promise<Uint8Array | null>
    async fn get_identity(
        &self,
        _addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<IdentityKey>> {
        let method = Reflect::get(&self.obj, &JsValue::from_str("getIdentity"))
            .map_err(|_| SignalProtocolError::InvalidArgument("identityStore.getIdentity missing".into()))?;
        let func = Function::from(method);

        let promise = func
            .call1(&self.obj, &JsValue::from_f64(self.remote_handle as f64))
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("getIdentity call failed: {:?}", e))
            })?;

        let js_val = JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("getIdentity promise rejected: {:?}", e))
            })?;

        if js_val.is_null() || js_val.is_undefined() {
            return Ok(None);
        }

        // Construct Uint8Array from returned value and convert to Vec<u8>
        let u8arr = Uint8Array::new(&js_val);
        let vec = u8arr.to_vec();

        let key = IdentityKey::try_from(vec.as_slice())
            .map_err(|e| SignalProtocolError::InvalidArgument(format!("invalid IdentityKey: {:?}", e)))?;

        Ok(Some(key))
    }

    /// is_trusted_identity(remote, identity, direction) -> Promise<boolean>
    async fn is_trusted_identity(
        &self,
        _addr: &ProtocolAddress,
        their_identity: &IdentityKey,
        direction: Direction,
    ) -> ProtocolResult<bool> {
        let js_identity = Uint8Array::from(their_identity.serialize().as_ref());

        let method = Reflect::get(&self.obj, &JsValue::from_str("isTrustedIdentity"))
            .map_err(|_| SignalProtocolError::InvalidArgument("identityStore.isTrustedIdentity missing".into()))?;
        let func = Function::from(method);

        let dir_str = match direction {
            Direction::Sending => "sending",
            Direction::Receiving => "receiving",
        };

        let promise = func
            .call3(
                &self.obj,
                &JsValue::from_f64(self.remote_handle as f64),
                &JsValue::from(js_identity),
                &JsValue::from_str(dir_str),
            )
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("isTrustedIdentity call failed: {:?}", e))
            })?;

        let js_val = JsFuture::from(Promise::from(promise))
            .await
            .map_err(|e| {
                SignalProtocolError::InvalidArgument(format!("isTrustedIdentity promise rejected: {:?}", e))
            })?;

        Ok(js_val.as_bool().unwrap_or(false))
    }
}
