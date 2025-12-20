use async_trait::async_trait;
use uuid::Uuid;

use libsignal_protocol::{
    SenderKeyRecord,
    SenderKeyStore,
    ProtocolAddress,
    error::Result as ProtocolResult,
};

use crate::wasm_sender_key_store::SenderKeyStoreMap;

/// Adapter that allows HandleStore to act as a SenderKeyStore
pub struct WasmSenderKeyStore<'a> {
    inner: &'a mut SenderKeyStoreMap,
}

impl<'a> WasmSenderKeyStore<'a> {
    pub fn new(inner: &'a mut SenderKeyStoreMap) -> Self {
        Self { inner }
    }

    fn make_key(sender: &ProtocolAddress, distribution_id: Uuid) -> String {
        format!(
            "{}::{}::{}",
            sender.name(),
            u32::from(sender.device_id()),
            distribution_id
        )
    }
}

#[async_trait(?Send)]
impl<'a> SenderKeyStore for WasmSenderKeyStore<'a> {
    async fn load_sender_key(
        &mut self,
        sender: &ProtocolAddress,
        distribution_id: Uuid,
    ) -> ProtocolResult<Option<SenderKeyRecord>> {
        let key = Self::make_key(sender, distribution_id);
        Ok(self.inner.get(&key).cloned())
    }

    async fn store_sender_key(
        &mut self,
        sender: &ProtocolAddress,
        distribution_id: Uuid,
        record: &SenderKeyRecord,
    ) -> ProtocolResult<()> {
        let key = Self::make_key(sender, distribution_id);
        self.inner.insert(key, record.clone());
        Ok(())
    }
}
