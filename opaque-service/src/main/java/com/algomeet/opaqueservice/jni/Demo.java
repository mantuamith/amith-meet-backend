package com.algomeet.opaqueservice.jni;

import java.nio.charset.Charset;
import java.util.Base64;

import com.algomeet.opaqueservice.dto.LoginResponse;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretResponse;
import com.algomeet.opaqueservice.dto.UserSecretResponse;
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
        String bearerToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYWRkb3guYWxnb2ZyYW1lQGdtYWlsLmNvbSIsInVzZXJuYW1lIjoibWFkZG94Iiwicm9sZSI6IlJPTEVfU0EiLCJzaWQiOiJlMmEwNGFiYy0wNDc0LTQ0M2UtYWI2My00OTRhOWE0ODhlZWEiLCJ1c2VyX2tleSI6IjJmYzM1Y2FlLWUwYjctNDBhNS1iMmFhLWU4NjIwNjczMGU5OSIsInRlbmFudElkIjowLCJpYXQiOjE3NjM1MzEwNDIsImV4cCI6MTc2MzYyMTA0Mn0.23K5jN1tTXY7XM0dqfEC0UYGRH4B_4RrOF1_E873yOA";
//
//        // --- Step 1: Create OPAQUE clientRegistrationMessage ---       
//        Opaque o = new Opaque();
//        OpaqueRegReq regReq = o.createRegReq("password");
//       
//        System.out.println("regReq=====" + Base64.getEncoder().encodeToString(regReq.M));                
//        String clientRegMsgBase64 = Base64.getEncoder().encodeToString(regReq.M);
//        
//        // --- Call server /register ---
//        RegistrationResponse regResponse = client.register(clientRegMsgBase64, bearerToken);
//
//        System.out.println("Server PubKey = " + regResponse.getPublicKey());
//        System.out.println("Server ID = " + regResponse.getServerId());
//
//        // --- Step 2: Create OPAQUE clientRecord ---
//        OpaqueIds ids = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
//        		regResponse.getServerId().getBytes(Charset.forName("UTF-8")));
//
//        OpaquePreRecExpKey prerec = o.finalizeReg(regReq.sec, Base64.getDecoder().decode(regResponse.getPublicKey()), ids);   
//        System.out.println("Export Key = " + prerec.export_key);
//        String clientRecordBase64 = Base64.getEncoder().encodeToString(prerec.rec);
//          
//        // Save secret
//        UserSecretResponse saveResp = client.saveSecret(
//                CredentialType.PIN,
//                clientRecordBase64,
//                Base64.getEncoder().encodeToString("my-secret".getBytes()),
//                bearerToken
//        );
//
//        System.out.println("Saved Secret = " + saveResp.getSecretKey());        
       
        // Retrieve secret
        
     // Replace with your actual client public key in Base64
       // export_key=====7UhrG7YmTbTyFiMehI+hDnNd2PP6G9KprYe+TTcyMT7glSw4pGzIwF+fs9pfe8e20dMhMQVTbf53/g8G0561Ug== 
                
        Opaque opaqueRetriever = new Opaque();
        OpaqueCredReq credReq = opaqueRetriever.createCredReq("password");
        System.out.println("pub=====" + Base64.getEncoder().encodeToString(credReq.pub));
        
        String clientPubKeyBase64 = Base64.getEncoder().encodeToString(credReq.pub);

        LoginResponse loginResp = client.login(
                CredentialType.PIN,
                clientPubKeyBase64,
                bearerToken
        );
        
        OpaqueIds ids = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
		"in.algoframe.algomeet".getBytes(Charset.forName("UTF-8")));
        
        OpaqueCreds creds = opaqueRetriever.recoverCreds(Base64.getDecoder().decode(loginResp.getPublicKey()), credReq.sec, "context", ids);
        

        System.out.println("export_key=====" + Base64.getEncoder().encodeToString(creds.export_key));
        System.out.println("sk=====" + Base64.getEncoder().encodeToString(creds.sk));

        RetrieveUserSecretResponse retrieveResp = client.retrieveSecret(
                CredentialType.PIN,
                Base64.getEncoder().encodeToString(creds.authU),
                loginResp.getServerSecKey(),
                bearerToken
        );

        System.out.println("Retrieved Secret = " + retrieveResp.getSecretKey());
    }
}
