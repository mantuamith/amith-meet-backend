import { CiphertextMessageConstants, type CiphertextMessage } from "./CiphertextMessage";
import { PreKeySignalMessage } from "./PreKeySignalMessage";
import { SignalMessage } from "./SignalMessage";
import { ciphertextMessage as ciphertextMessageWasm} from "libsignal_wasm_pqxdh";

export class CiphertextMessageFactory {
  static fromHandle(handle: number): CiphertextMessage {
    const type = ciphertextMessageWasm.ciphertextmessage_get_type(handle);
    const innerHandle =
          ciphertextMessageWasm.ciphertextmessage_get_signal_message(handle);

    switch (type) {
      case CiphertextMessageConstants.PREKEY_TYPE:        
        return new PreKeySignalMessage(innerHandle);

      case CiphertextMessageConstants.WHISPER_TYPE:
        return new SignalMessage(innerHandle);

      default:
        throw new Error(`Unsupported CiphertextMessage type: ${type}`);
    }
  }
}