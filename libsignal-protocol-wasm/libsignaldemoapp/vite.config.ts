//import { defineConfig } from 'vite'
//import react from '@vitejs/plugin-react'

// https://vite.dev/config/
/*
export default defineConfig({
  plugins: [react()],
}) */
/*
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],

  define: {
    "process.env": {},
  },

  resolve: {
    alias: {
      // Force WASM version; bypass node-gyp-build
      "node-gyp-build": "/src/shims/node-gyp-build-browser.js",
      process: "process/browser",
      buffer: "buffer/",
    }
  },

  optimizeDeps: {
    exclude: ["@signalapp/libsignal-client"]
  }
});*/

/*
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      // Force any libsignal loader call to use our shim
      "node-gyp-build": path.resolve(__dirname, "src/shims/node-gyp-build-browser.js"),
      "node-gyp-build/index.js": path.resolve(__dirname, "src/shims/node-gyp-build-browser.js"),

      // Prevent Node detection
      process: "process/browser",
      buffer: "buffer/"
    }
  },

  define: {
    // Avoid process.env making the environment look like Node
    "process.env": {}
  }
}); */

import { defineConfig } from "vite";

export default defineConfig({
  server: {
    fs: {
      allow: [
        ".",
        "../libsignal-protocol-wasm/wasm-wrapper/pkg"
      ]
    }
  }
});