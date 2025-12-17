// wasm-wrapper/src/wasm_session_builder/adapters.rs

use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::JsFuture;
use js_sys::{Promise, Uint8Array, Reflect, Function};

use async_trait::async_trait;

use libsignal_protocol::{
    SessionStore, IdentityKeyStore, SessionRecord, ProtocolAddress, IdentityKey,
    IdentityKeyPair, Direction, SignalProtocolError, IdentityChange,
};
use libsignal_protocol::error::Result as ProtocolResult;

use crate::wasm_session_store::{store_session, load_session};
use crate::wasm_identity_key_store::{
    store_identity_key, load_identity_key, 
    get_identity_key_pair,
    get_local_registration_id,
    is_trusted_identity,
};

use web_sys::console;

fn require_store_handle(name: &str, store_handle: u32) -> ProtocolResult<()> {
    if store_handle == 0 {
        let msg = format!(
            "[{}] called with store_handle == 0 (store not initialized)",
            name
        );

        // 🔊 Browser console error
        console::error_1(&msg.clone().into());

        // ❌ Signal-style failure
        return Err(SignalProtocolError::InvalidArgument(msg));
    }

    Ok(())
}

//
// =====================
// SessionStore Adapter
// =====================
//
#[derive(Clone)]
pub struct JsSessionStoreAdapter {
    store_handle: u32,
}

impl JsSessionStoreAdapter {
    pub fn new(store_handle: u32) -> Self {
        assert!(store_handle != 0, "SessionStoreAdapter created with handle 0");
        Self { store_handle }
    }
}

#[async_trait(?Send)]
impl SessionStore for JsSessionStoreAdapter {
    async fn load_session(
        &self,
        addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<SessionRecord>> {
        // Guard
        require_store_handle("SessionStore::load_session", self.store_handle)?;
        load_session(self.store_handle, addr)
    }

    async fn store_session(
        &mut self,
        addr: &ProtocolAddress,
        record: &SessionRecord,
    ) -> ProtocolResult<()> {

        // Guard
        require_store_handle("SessionStore::store_session", self.store_handle)?;
        store_session(self.store_handle, addr, record);

        Ok(())
    }
}

//
// =====================
// IdentityKeyStore Adapter
// =====================
//

#[derive(Clone)]
pub struct JsIdentityStoreAdapter {
    store_handle: u32,
}

impl JsIdentityStoreAdapter {
    pub fn new(store_handle: u32) -> Self {
        assert!(store_handle != 0, "SessionStoreAdapter created with handle 0");
        Self { store_handle }
    }
}

#[async_trait(?Send)]
impl IdentityKeyStore for JsIdentityStoreAdapter {
    async fn save_identity(
        &mut self,
        addr: &ProtocolAddress,
        identity: &IdentityKey,
    ) -> ProtocolResult<IdentityChange> {
        // Save idenriry key
        store_identity_key(self.store_handle, addr, identity);

        Ok(IdentityChange::NewOrUnchanged)
    }
    
    async fn get_identity_key_pair(&self) -> ProtocolResult<IdentityKeyPair> {
        get_identity_key_pair(self.store_handle)
    }

    async fn get_local_registration_id(&self) -> ProtocolResult<u32> {
        get_local_registration_id(self.store_handle)
    }

    async fn get_identity(
        &self,
        addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<IdentityKey>> {
        load_identity_key(self.store_handle, addr)
    }

    async fn is_trusted_identity(
        &self,
        addr: &ProtocolAddress,
        their_identity: &IdentityKey,
        direction: Direction,
    ) -> ProtocolResult<bool> {
        is_trusted_identity(self.store_handle, addr, their_identity, direction)        
    }
}
