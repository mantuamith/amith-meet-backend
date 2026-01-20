pub struct HandleTable<T> {
    slots: Vec<Option<T>>,
}

impl<T> HandleTable<T> {
    pub fn new() -> Self {
        // index 0 reserved => handle 0 is invalid
        Self {
            slots: vec![None],
        }
    }

    pub fn insert(&mut self, value: T) -> u32 {
        // try to reuse an empty slot
        for (i, slot) in self.slots.iter_mut().enumerate().skip(1) {
            if slot.is_none() {
                *slot = Some(value);
                return (i + 1) as u32; // (return index + 1)
            }
        }

        // no empty slot → push
        self.slots.push(Some(value));
        self.slots.len() as u32 //(return index + 1)
    }
    
    pub fn with<R>(&self, handle: u32, f: impl FnOnce(&T) -> R) -> R {
        let slot = self
            .slots
            .get((handle - 1) as usize)
            .and_then(|s| s.as_ref())
            .expect("Invalid handle");
        f(slot)
    }    

    pub fn contains(&self, handle: u32) -> bool {
        self.slots
            .get((handle - 1) as usize)
            .map_or(false, |s| s.is_some())
    }

    pub fn remove(&mut self, handle: u32) {
        if let Some(slot) = self.slots.get_mut((handle - 1) as usize) {
            *slot = None;
        }
    }

    pub fn take(&mut self, handle: u32) -> Option<T> {
        self.slots
            .get_mut((handle - 1) as usize)
            .and_then(|slot| slot.take())
    }
}