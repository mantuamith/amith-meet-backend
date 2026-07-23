use std::collections::HashMap;
use std::hash::Hash;
use libsignal_protocol::{
    IdentityKeyPair,
};

pub struct HandleIdentityStore<K, T>
where
    K: Eq + Hash,
{
    registration_id: u32,
    identity_key_pair: IdentityKeyPair,
    map: HashMap<K, T>,
}

impl<K, T> HandleIdentityStore<K, T>
where
    K: Eq + Hash,
{
    pub fn new(registration_id: u32, identity_key_pair: IdentityKeyPair) -> Self {
        Self {
            registration_id: registration_id,
            identity_key_pair: identity_key_pair,
            map: HashMap::new(),
        }
    }

    /// Insert with explicit key
    pub fn insert(&mut self, key: K, value: T) {
        self.map.insert(key, value);
    }

    pub fn with<R>(&self, key: &K, f: impl FnOnce(&T) -> R) -> R {
        let value = self.map.get(key).expect("Invalid key");
        f(value)
    }

    pub fn remove(&mut self, key: &K) {
        self.map.remove(key);
    }

    pub fn contains(&self, key: &K) -> bool {
        self.map.contains_key(key)
    }

    pub fn get(&self, key: &K) -> Option<&T> {
        self.map.get(key)
    }

    pub fn get_mut(&mut self, key: &K) -> Option<&mut T> {
        self.map.get_mut(key)
    }

    pub fn identity_key_pair(&self) -> &IdentityKeyPair {
        &self.identity_key_pair
    }

    pub fn registration_id(&self) -> u32 {
        self.registration_id
    }
}
