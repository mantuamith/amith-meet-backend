use async_trait::async_trait;
use js_sys::Uint8Array;
use web_sys::console;

use libsignal_protocol::{
    SessionStore,
    IdentityKeyStore,
    PreKeyStore,
    SignedPreKeyStore,
    KyberPreKeyStore,
    SessionRecord,
    IdentityKey,
    IdentityKeyPair,
    IdentityChange,
    Direction,
    ProtocolAddress,
    PreKeyId,
    PreKeyRecord,
    SignedPreKeyId,
    SignedPreKeyRecord,
    KyberPreKeyId,
    KyberPreKeyRecord,
    PublicKey,
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

use crate::wasm_pre_key_store;
use crate::wasm_signed_pre_key_store;
use crate::wasm_kyber_pre_key_store;
use crate::wasm_ec_public_key;

/// Helper: map JsValue → SignalProtocolError
fn js_err(e: impl core::fmt::Debug) -> SignalProtocolError {
    SignalProtocolError::InvalidArgument(format!("{:?}", e))
}

//
// =====================
// SessionStore Adapter
// =====================
//
#[derive(Clone)]
pub struct JsSessionStoreAdapter {
    handle: u32,
}

impl JsSessionStoreAdapter {
    pub fn new(handle: u32) -> Self {
        assert!(handle != 0, "SessionStore handle must not be 0");
        Self { handle }
    }
}

#[async_trait(?Send)]
impl SessionStore for JsSessionStoreAdapter {
    async fn load_session(
        &self,
        addr: &ProtocolAddress,
    ) -> ProtocolResult<Option<SessionRecord>> {
        wasm_load_session(self.handle, addr)
    }

    async fn store_session(
        &mut self,
        addr: &ProtocolAddress,
        record: &SessionRecord,
    ) -> ProtocolResult<()> {

        wasm_store_session(self.handle, addr, record);

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
    handle: u32,
}

impl JsIdentityStoreAdapter {
    pub fn new(handle: u32) -> Self {
        assert!(handle != 0, "IdentityKeyStore handle must not be 0");
        Self { handle }
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


//
// =====================
// PreKeyStore Adapter
// =====================
//
pub struct JsPreKeyStoreAdapter {
    handle: u32,
}

impl JsPreKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        Self { handle }
    }
}

#[async_trait(?Send)]
impl PreKeyStore for JsPreKeyStoreAdapter {
    async fn get_pre_key(
        &self,
        id: PreKeyId,
    ) -> ProtocolResult<PreKeyRecord> {
        let bytes =
            wasm_pre_key_store::prekeystore_load_prekey(
                self.handle,
                id.into(),
            )
            .map_err(js_err)?;

        PreKeyRecord::deserialize(&bytes.to_vec())
    }

    async fn save_pre_key(
        &mut self,
        id: PreKeyId,
        record: &PreKeyRecord,
    ) -> ProtocolResult<()> {
        wasm_pre_key_store::prekeystore_store_prekey(
            self.handle,
            id.into(),
            Uint8Array::from(record.serialize()?.as_slice()),
        )
        .map_err(js_err)
    }

    async fn remove_pre_key(
        &mut self,
        id: PreKeyId,
    ) -> ProtocolResult<()> {
        wasm_pre_key_store::prekeystore_remove_prekey(
            self.handle,
            id.into(),
        )
        .map_err(js_err)
    }
}

//
// =====================
// SignedPreKeyStore Adapter
// =====================
//
pub struct JsSignedPreKeyStoreAdapter {
    handle: u32,
}

impl JsSignedPreKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        Self { handle }
    }
}

#[async_trait(?Send)]
impl SignedPreKeyStore for JsSignedPreKeyStoreAdapter {
    async fn get_signed_pre_key(
        &self,
        id: SignedPreKeyId,
    ) -> ProtocolResult<SignedPreKeyRecord> {
        let bytes =
            wasm_signed_pre_key_store::signedprekeystore_load_signed_prekey(
                self.handle,
                id.into(),
            )
            .map_err(js_err)?;

        SignedPreKeyRecord::deserialize(&bytes.to_vec())
    }

    async fn save_signed_pre_key(
        &mut self,
        id: SignedPreKeyId,
        record: &SignedPreKeyRecord,
    ) -> ProtocolResult<()> {
        wasm_signed_pre_key_store::signedprekeystore_store_signed_prekey(
            self.handle,
            id.into(),
            Uint8Array::from(record.serialize()?.as_slice()),
        )
        .map_err(js_err)
    }
}

//
// =====================
// KyberPreKeyStore Adapter
// =====================
//
pub struct JsKyberPreKeyStoreAdapter {
    handle: u32,
}

impl JsKyberPreKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        Self { handle }
    }
}

#[async_trait(?Send)]
impl KyberPreKeyStore for JsKyberPreKeyStoreAdapter {
    async fn get_kyber_pre_key(
        &self,
        id: KyberPreKeyId,
    ) -> ProtocolResult<KyberPreKeyRecord> {
        let bytes =
            wasm_kyber_pre_key_store::kyberprekeystore_load_kyber_prekey(
                self.handle,
                id.into(),
            )
            .map_err(js_err)?;

        KyberPreKeyRecord::deserialize(&bytes.to_vec())
    }

    async fn save_kyber_pre_key(
        &mut self,
        id: KyberPreKeyId,
        record: &KyberPreKeyRecord,
    ) -> ProtocolResult<()> {
        wasm_kyber_pre_key_store::kyberprekeystore_store_kyber_prekey(
            self.handle,
            id.into(),
            Uint8Array::from(record.serialize()?.as_slice()),
        )
        .map_err(js_err)
    }

    async fn mark_kyber_pre_key_used(
        &mut self,
        kyber_prekey_id: KyberPreKeyId,
        signed_prekey_id: SignedPreKeyId,
        base_key: &PublicKey,
    ) -> ProtocolResult<()> {
        let base_key_handle =
            wasm_ec_public_key::store_public_key(base_key.clone());

        wasm_kyber_pre_key_store::kyberprekeystore_mark_kyber_prekey_used(
            self.handle,
            kyber_prekey_id.into(),
            signed_prekey_id.into(),
            base_key_handle,
        )
        .map_err(js_err)
    }
}
