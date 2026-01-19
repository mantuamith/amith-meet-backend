package com.algomeet.opaqueservice.jni;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.algomeet.opaqueservice.jni.dto.OpaqueCredReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueCreds;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaquePreRecExpKey;

import com.algomeet.opaqueservice.jni.dto.OpaqueRecExpKey;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;


public class OpaqueTest {
	
	@Test
    public void test() {
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        Opaque o = new Opaque();
        
        // Server
        OpaqueRecExpKey ret = o.register("password", ids);

        // Retrieve 
        // Client
        OpaqueCredReq creq = o.createCredReq("password");     
        
        // server
        OpaqueCredResp cresp = o.createCredResp(creq.pub, ret.rec, ids, "context");
        
        // Client
        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids); 
        
        // Server
        assert o.userAuth(cresp.sec, creds.authU);
    }

	@Test
	public void test_noPks_noIds() {
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        Opaque o = new Opaque();
        byte[] skS = fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        // Client
        OpaqueRecExpKey ret = o.register("password", skS, ids);
        assert ret != null;  
        
        // Retrieve 
        // Client
        OpaqueCredReq creq = o.createCredReq("password");
        assert creq != null;  
        
        // Server
        OpaqueCredResp cresp = o.createCredResp(creq.pub, ret.rec, ids, "context");
        assert cresp != null;  
        
        // Client
        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids);
        
        assert creds.export_key != null;       
         // server
        assert o.userAuth(cresp.sec, creds.authU);
    }

	@Test
	public void test_privreg() {
        Opaque o = new Opaque();
        // Client
        OpaqueRegReq regReq = o.createRegReq("password");

        // Server
        OpaqueRegResp regResp = o.createRegResp(regReq.M);
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        
        // Client
        OpaquePreRecExpKey prerec = o.finalizeReg(regReq.sec, regResp.pub, ids);

        // Server
        byte[] rec = o.storeRec(regResp.sec, prerec.rec);
        assert rec != null;  

        
        // Retrieve 
        // Client
        OpaqueCredReq creq = o.createCredReq("password");
        assert creq != null;  

        // Server
        OpaqueCredResp cresp = o.createCredResp(creq.pub, rec, ids, "context");
        assert cresp != null;  

        // Client
        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids);
        assert creds.export_key != null;   
        
        // Server
        assert o.userAuth(cresp.sec, creds.authU);
    }

	@Test
	public void test_priv1kreg() {
        Opaque o = new Opaque();
         // Client
        OpaqueRegReq regReq = o.createRegReq("password");
        byte[] skS = fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        
        // Server
        OpaqueRegResp regResp = o.createRegResp(regReq.M, skS);

        OpaqueIds ids = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
                                      "algomeet.com".getBytes(Charset.forName("UTF-8")));
        
        // Client
        OpaquePreRecExpKey preRec = o.finalizeReg(regReq.sec, regResp.pub, ids);

        // Server
        byte[] rec = o.storeRec(regResp.sec, preRec.rec);
        assert rec != null;  
                       
        // Retrieve 
        Opaque o2 = new Opaque();
        // Client
		OpaqueCredReq creq2 = o2.createCredReq("password");
		// Server
		OpaqueCredResp cresp2 = o2.createCredResp(creq2.pub, rec, ids, "context");
		// Client
        OpaqueCreds creds = o2.recoverCreds(cresp2.pub, creq2.sec, "context", ids);

        Assertions.assertEquals(Base64.getEncoder().encodeToString(creds.export_key), Base64.getEncoder().encodeToString(preRec.export_key));
        // Server
        assert (o2.userAuth(cresp2.sec, creds.authU));
    }

    // stackoverflowd from https://stackoverflow.com/a/140861
    private static byte[] fromHex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    // strackoverflowed from: https://stackoverflow.com/a/9855338
    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    public static String toHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
