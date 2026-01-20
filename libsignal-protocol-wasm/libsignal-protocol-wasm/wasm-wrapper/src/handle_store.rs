use std::collections::HashMap;
use std::hash::Hash;

pub struct HandleStore<K, T>
where
    K: Eq + Hash,
{
    map: HashMap<K, T>,
}

impl<K, T> HandleStore<K, T>
where
    K: Eq + Hash,
{
    pub fn new() -> Self {
        Self {
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

    pub fn values(&self) -> impl Iterator<Item = &T> {
        self.map.values()
    }
}
