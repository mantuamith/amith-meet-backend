use std::collections::HashMap;

pub struct HandleMessageStore<T> {
    next: u32,
    map: HashMap<u32, T>,
}

impl<T> HandleMessageStore<T> {
    pub fn new() -> Self {
        Self {
            next: 1,
            map: HashMap::new(),
        }
    }

    pub fn insert(&mut self, value: T) -> u32 {
        let handle = self.next;
        self.next += 1;
        self.map.insert(handle, value);
        handle
    }

    pub fn with<R>(&self, handle: u32, f: impl FnOnce(&T) -> R) -> R {
        let value = self.map.get(&handle).expect("Invalid handle");
        f(value)
    }

    pub fn remove(&mut self, handle: u32) {
        self.map.remove(&handle);
    }

    pub fn contains(&self, handle: u32) -> bool {
        self.map.contains_key(&handle)
    }
}