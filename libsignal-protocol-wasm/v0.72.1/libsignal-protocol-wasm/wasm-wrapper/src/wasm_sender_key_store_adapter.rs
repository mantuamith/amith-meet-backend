use async_trait::async_trait;
use uuid::Uuid;
use web_sys::console;

use libsignal_protocol::{
    SenderKeyRecord,
    SenderKeyStore,
    ProtocolAddress,
    error::Result as ProtocolResult,
};

use crate::wasm_sender_key_store::{
    store_sender_key as wasm_store_sender_key,
    load_sender_key as wasm_load_sender_key,
};

pub struct SenderKeyStoreAdapter {
    handle: u32,
}

impl SenderKeyStoreAdapter {
    pub fn new(handle: u32) -> Self {
        assert!(handle != 0, "SenderKeyStore handle must not be 0");
        Self { handle }
    }
}

#[async_trait(?Send)]
impl SenderKeyStore for SenderKeyStoreAdapter {
    async fn load_sender_key(
        &mut self,
        sender: &ProtocolAddress,
        distribution_id: Uuid,
    ) -> ProtocolResult<Option<SenderKeyRecord>> {
        let dist_id_str = distribution_id.to_string();

        let result = wasm_load_sender_key(
            self.handle,
            sender,
            &dist_id_str,
        );

        match &result {
            Ok(Some(_)) => {                
            }
            Ok(None) => {
                console::log_1(
                    &format!("[SenderKeyStoreAdapter::load_sender_key] record NOT found {} {} {}", self.handle, sender, distribution_id).into(),
                );
            }
            Err(e) => {
                console::error_1(
                    &format!(
                        "[SenderKeyStoreAdapter::load_sender_key] ERROR: {:?}",
                        e
                    )
                    .into(),
                );
            }
        }

        result
    }

    async fn store_sender_key(
        &mut self,
        sender: &ProtocolAddress,
        distribution_id: Uuid,
        record: &SenderKeyRecord,
    ) -> ProtocolResult<()> {
        let dist_id_str = distribution_id.to_string();

        let result = wasm_store_sender_key(
            self.handle,
            sender,
            &dist_id_str,
            record,
        );

        if let Err(e) = &result {
            console::error_1(
                &format!(
                    "[SenderKeyStoreAdapter::store_sender_key] ERROR: {:?}",
                    e
                )
                .into(),
            );
        } 

        result
    }
}
