use once_cell::sync::Lazy;
use std::sync::Mutex;

use libsignal_protocol::SenderKeyDistributionMessage;
use crate::handle_table::HandleTable;

static SKDM_TABLE: Lazy<Mutex<HandleTable<SenderKeyDistributionMessage>>> =
    Lazy::new(|| Mutex::new(HandleTable::new()));

pub fn store_sender_key_distribution_message(
    msg: SenderKeyDistributionMessage,
) -> u32 {
    SKDM_TABLE.lock().unwrap().insert(msg)
}

pub fn take_sender_key_distribution_message(
    handle: u32,
) -> Option<SenderKeyDistributionMessage> {
    if handle == 0 {
        return None;
    }
    SKDM_TABLE.lock().unwrap().take(handle)
}
