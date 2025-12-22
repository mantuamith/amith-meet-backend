use std::{env, fs, path::PathBuf};

fn main() {
    let target = env::var("TARGET").unwrap();

    // WASM build → do NOT generate protos
    if target.contains("wasm32") {
        println!("cargo:warning=libsignal-protocol: skipping prost-build for wasm");
        return;
    }

    // Native build → run prost-build normally
    println!("cargo:warning=libsignal-protocol: running prost-build for native target");

    let proto_files = [
        "src/proto/fingerprint.proto",
        "src/proto/sealed_sender.proto",
        "src/proto/storage.proto",
        "src/proto/service.proto",
        "src/proto/wire.proto",
    ];

    let proto_include = ["src/proto"];

    prost_build::Config::new()
        .compile_protos(&proto_files, &proto_include)
        .unwrap();

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let generated_dir = PathBuf::from("src/proto/generated");

    fs::create_dir_all(&generated_dir).unwrap();

    // Copy all prost-generated .rs files into src/proto/generated/
    for entry in fs::read_dir(&out_dir).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        if path.extension().map(|x| x == "rs").unwrap_or(false) {
            let filename = path.file_name().unwrap();
            let target_file = generated_dir.join(filename);
            fs::copy(&path, &target_file).unwrap();

            println!(
                "cargo:warning=copied {} → {}",
                path.display(),
                target_file.display()
            );
        }
    }
}
