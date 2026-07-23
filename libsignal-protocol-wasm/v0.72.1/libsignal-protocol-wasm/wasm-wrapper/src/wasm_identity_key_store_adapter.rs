use async_trait::async_trait;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_protocol::{
    IdentityKeyStore,
    IdentityKey,
    IdentityKeyPair,
    IdentityChange,
    Direction,
    ProtocolAddress,
    SignalProtocolError,
};

use libsignal_protocol::error::Result as ProtocolResult;
use libsignal_protocol::GenericSignedPreKey;

use crate::wasm_session_store::{
    store_session as wasm_store_session,
    load_session as wasm_load_session,
};

use crate::wasm_identity_key_store::{
    store_identity_key as wasm_store_identity_key,
    load_identity_key as wasm_load_identity_key,
    get_identity_key_pair as wasm_get_identity_key_pair,
    get_local_registration_id as wasm_get_local_registration_id,
    is_trusted_identity as wasm_is_trusted_identity,
};

use crate::wasm_prekey_store;
use crate::wasm_signed_prekey_store;
use crate::wasm_kyber_prekey_store;
use crate::wasm_ec_public_key;

/// Helper: map JsValue → SignalProtocolError
fn js_err(e: impl core::fmt::Debug) -> SignalProtocolError {
    SignalProtocolError::InvalidArgument(format!("{:?}", e))
}

//
// =====================
// IdentityKeyStore Adapter
// =====================
//
#[derive(Clone)]
pub struct IdentityStoreAdapter {
    handle: u32,
}

impl IdentityStoreAdapter {
    pub fn new(handle: u32) -> Self {
        assert!(handle != 0, "IdentityKeyStore handle must not be 0");
        Self { handle }
    }
}

#[async_trait(?Send)]
impl IdentityKeyStore for IdentityStoreAdapter {
    async fn save_identity(
        &mut self,
        addr: &ProtocolAddress,
        identity: &IdentityKey,
    ) -> ProtocolResult<IdentityChange> {
        // Save idenriry key
        wasm_store_identity_key(self.handle, addr, identity);

        Ok(IdentityChange::NewOrUnchanged)
    }
    
    async fn get_identity_key_pair(&self) -> ProtocolResult<IdentityKeyPair> {
        wasm_get_identity_key_pair(self.handle)
    }

    async fn get_local_registration_id(&self) -> ProtocolResult<u32> {
        wasm_get_local_registration_id(self.handle)
    }

    async fn get_identity(
        &self,
        addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<IdentityKey>> {
        wasm_load_identity_key(self.handle, addr)
    }

    async fn is_trusted_identity(
        &self,
        addr: &ProtocolAddress,
        their_identity: &IdentityKey,
        direction: Direction,
    ) -> ProtocolResult<bool> {
        wasm_is_trusted_identity(self.handle, addr, their_identity, direction)        
    }
}

