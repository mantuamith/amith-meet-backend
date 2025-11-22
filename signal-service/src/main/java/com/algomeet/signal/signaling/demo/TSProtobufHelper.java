package com.algomeet.signal.signaling.demo;

import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TSProtobufHelper {

    // Build TS-compatible PreKeyWhisperMessage bytes
    public static byte[] buildPreKeyWhisperMessage(
            byte[] baseKey,        // sender ephemeral key (serialized)
            byte[] identityKey,    // sender identity key (serialized)
            byte[] message,        // encrypted message
            int preKeyId,          // optional prekey id
            int signedPreKeyId,    // optional signed prekey id
            int registrationId     // sender registration id
    ) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);

        // --- Protobuf fields based on TS PreKeyWhisperMessage ---
        // field 1: baseKey (bytes)
        cos.writeBytes(1, com.google.protobuf.ByteString.copyFrom(baseKey));
        // field 2: identityKey (bytes)
        cos.writeBytes(2, com.google.protobuf.ByteString.copyFrom(identityKey));
        // field 3: message (bytes)
        cos.writeBytes(3, com.google.protobuf.ByteString.copyFrom(message));
        // field 4: preKeyId (int32)
        cos.writeInt32(4, preKeyId);
        // field 5: signedPreKeyId (int32)
        cos.writeInt32(5, signedPreKeyId);
        // field 6: registrationId (int32)
        cos.writeInt32(6, registrationId);

        cos.flush();
        return baos.toByteArray();
    }
}