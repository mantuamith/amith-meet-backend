use js_sys::Uint8Array;

pub fn vec_to_uint8array(bytes: &[u8]) -> js_sys::Uint8Array {
    let arr = js_sys::Uint8Array::new_with_length(bytes.len() as u32);
    arr.copy_from(bytes);
    arr
}
