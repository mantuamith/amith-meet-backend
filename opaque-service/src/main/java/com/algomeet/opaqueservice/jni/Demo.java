package com.algomeet.opaqueservice.jni;

import java.nio.charset.Charset;
import java.util.Base64;

import com.algomeet.opaqueservice.dto.UserCredentialResponse;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretResponse;
import com.algomeet.opaqueservice.dto.UserMasterSecretResponse;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueCreds;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaquePreRecExpKey;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegReq;

public class Demo {
    public static void main(String[] args) throws Exception {

        // Initialize
        OpaqueClient client = new OpaqueClient("http://localhost:8092/opaque");
        String bearerToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYWRkb3guYWxnb2ZyYW1lQGdtYWlsLmNvbSIsInVzZXJuYW1lIjoibWFkZG94Iiwicm9sZSI6IlJPTEVfU0EiLCJzaWQiOiJhMzk4YzA2Zi04MGU4LTQyOGMtYWNmNC1hZTUxMDY3YmQxMWQiLCJ1c2VyX2tleSI6IjJmYzM1Y2FlLWUwYjctNDBhNS1iMmFhLWU4NjIwNjczMGU5OSIsInRlbmFudElkIjowLCJpYXQiOjE3NjQ1OTY1NzYsImV4cCI6MTc2NDU5NzQ3Nn0.iUtWRg77KustLsLBHdw1fai9kznez_O8rw5MgacnvjA";
//
//        // --- Step 1: Create OPAQUE clientRegistrationMessage ---       
//        Opaque o = new Opaque();
//        OpaqueRegReq regReq = o.createRegReq("password");
//       
//        System.out.println("regReq=====" + Base64.getEncoder().encodeToString(regReq.M));                
//        String clientRegMsgBase64 = Base64.getEncoder().encodeToString(regReq.M);
//        
//        // --- Call server /register ---
//        RegistrationResponse regResponse = client.register(CredentialType.PIN, clientRegMsgBase64, bearerToken);
//
//        System.out.println("Server PubKey = " + regResponse.getPublicKey());
//        System.out.println("Server ID = " + regResponse.getServerId());
//
//        // --- Step 2: Create OPAQUE clientRecord ---
//        OpaqueIds ids = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
//        		regResponse.getServerId().getBytes(Charset.forName("UTF-8")));
//
//        OpaquePreRecExpKey preRec = o.finalizeReg(regReq.sec, Base64.getDecoder().decode(regResponse.getPublicKey()), ids);   
//        System.out.println("Export Key = " + preRec.export_key);
//                
//        String clientRecordBase64 = Base64.getEncoder().encodeToString(prerec.rec);
//        System.out.println("Record = " +  Base64.getEncoder().encodeToString(preRec.rec));
//          
//        // Save secret
//        UserMasterSecretResponse saveResp = client.saveSecret(
//                CredentialType.PIN,
//                clientRecordBase64,
//                Base64.getEncoder().encodeToString("my-secret".getBytes()),
//                "AES",
//                "V1",
//                "XXYYZZ",
//                bearerToken
//        );
//
//        System.out.println("Saved Secret = " + saveResp.getMasterSecretKey());        
//       
        
        // Retrieve secret                
        Opaque opaqueRetriever = new Opaque();
        OpaqueCredReq credReq = opaqueRetriever.createCredReq("password");
        System.out.println("pub=====" + Base64.getEncoder().encodeToString(credReq.pub));
        
        String clientPubKeyBase64 = Base64.getEncoder().encodeToString(credReq.pub);   
        
        UserCredentialResponse credResp = client.credentialResponse(
                CredentialType.PIN,
                clientPubKeyBase64,
                bearerToken
        );
        
        OpaqueIds opIds = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
        		credResp.getServerId().getBytes(Charset.forName("UTF-8")));
        
        OpaqueCreds creds = opaqueRetriever.recoverCreds(Base64.getDecoder().decode(credResp.getPublicKey()), credReq.sec, "context", opIds);
        System.out.println("export_key=====" + Base64.getEncoder().encodeToString(creds.export_key));
        
        RetrieveUserMasterSecretResponse retrieveResp = client.retrieveMasterSecret(
                CredentialType.PIN,
                Base64.getEncoder().encodeToString(creds.authU),
                bearerToken
        );

        System.out.println("Retrieved Secret = " + new String(Base64.getDecoder().decode(retrieveResp.getMasterSecretKey())));
        System.out.println("Algorithm = " + retrieveResp.getAlgorithm());
        System.out.println("Version = " + retrieveResp.getVersion());
        System.out.println("Salt = " + retrieveResp.getSalt());

        System.out.println("export_key=====" + Base64.getEncoder().encodeToString(creds.export_key));
        System.out.println("sk=====" + Base64.getEncoder().encodeToString(creds.sk));
    }
}
