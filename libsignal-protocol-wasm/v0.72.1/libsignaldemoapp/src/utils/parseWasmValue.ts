export function parseWasmValue(raw: any) {
    if (typeof raw === "string") {
        try {
            return JSON.parse(raw);
        } catch {
            throw new Error("WASM responded with non-JSON: " + raw);
        }
    }
    return raw; // Already a JS object
}
