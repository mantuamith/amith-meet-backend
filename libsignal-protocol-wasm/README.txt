Guide on how to build and run the "libsignaldemoapp" demo app.

Pre-requesites:
 - Install Rust SDK

Steps:
  1. Using CLI/ Command prompt navigate to "libsignal-protocol-wasm/wasm-wrapper".
     command: cd <libsignal-protocol-project home dir>/libsignal-protocol-wasm/wasm-wrapper
  
  2. Build WASM js package using the command:
     wasm-pack build --target web --release

  3. Using CLI/ Command prompt navigate to "libsignaldemoapp".
     command: cd <libsignal-protocol-project home dir>/libsignaldemoapp
  
  4. Install react dependencies using the command.
     npm install
  
  5. Install libsignal-protocol-wasm using the command.
     npm install ../libsignal-protocol-wasm/wasm-wrapper/pkg

  6. Run the demo app using the command.
     npm run dev
  

