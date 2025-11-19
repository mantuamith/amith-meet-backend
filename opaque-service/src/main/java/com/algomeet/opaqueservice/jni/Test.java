package com.algomeet.opaqueservice.jni;

import java.nio.charset.*;
import java.util.Arrays;
import java.util.Base64;

import com.algomeet.opaqueservice.jni.dto.OpaqueCredReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueCreds;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaquePreRecExpKey;
import com.algomeet.opaqueservice.jni.dto.OpaqueRecExpKey;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;

public class Test {
	public static void main(String args[]) {
        test1();
        System.out.println("---> test1");
        test_noPks_noIds();
        System.out.println("---> test_noPks_noIds");
        test_privreg();
        System.out.println("---> test_privreg");
        test_priv1kreg();
		System.out.println("everything ok");
	}

    private static void test1() {
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        Opaque o = new Opaque();

        OpaqueRecExpKey ret = o.register("password12", ids);
		System.out.println("rec=" + Base64.getEncoder().encodeToString(ret.rec) + ", export_key=" + Base64.getEncoder().encodeToString(ret.export_key));

        OpaqueCredReq creq = o.createCredReq("password1287876565556erwersfsdfsdsdsdasdsad76767");
		System.out.println("sec=" + Base64.getEncoder().encodeToString(creq.sec) + ", pub=" + Base64.getEncoder().encodeToString(creq.pub));
        
        OpaqueCredResp cresp = o.createCredResp(creq.pub, ret.rec, ids, "context");
		System.out.println("sec=" + Base64.getEncoder().encodeToString(cresp.sec) + ", pub=" + Base64.getEncoder().encodeToString(cresp.pub));
        //byte[] oprf = Arrays.copyOfRange(cresp.sec, 256, cresp.sec.length);   
        System.out.println("oprf=====" + Base64.getEncoder().encodeToString(cresp.sec));
        //prf=====BNxlZR1+XXC5qDk5Cd/4NgGuKU0OuF7ty+GQNeIooPHsqk3Sdp3p45IkFC/xp3ZuVIBhrWneu52qww7azulnrg==
        
        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids); 
        
        //System.out.println("SK=====" + Base64.getEncoder().encodeToString(creds.sk));
        //SK=====AMaAJgEAAAAwqXJrAQAAAMiO3gUBgF7JKD1AOAEAAAAAxoAmAQAAAAAAAAAAAAAAgNFTBgEAAABQqXJrAQAAAA==
        //SK=====AMoAOQEAAAAwKXxrAQAAAMgO1QUBAG5WKD1AJAEAAAAAygA5AQAAAAAAAAAAAAAAgFFKBgEAAABQKXxrAQAAAA==
        //SK=====ABYBVAEAAAAwKYttAQAAAMgOxgMBgB0NKD1AIQEAAAAAFgFUAQAAAAAAAAAAAAAAgFE7BAEAAABQKYttAQAAAA==
        
        assert o.userAuth(cresp.sec, creds.authU);
    }

    private static void test_noPks_noIds() {
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        Opaque o = new Opaque();
        byte[] skS = fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        System.out.println("skS=" + Base64.getEncoder().encodeToString(skS));
        OpaqueRecExpKey ret =o.register("password", skS, ids);
		System.out.println("rec=" + Base64.getEncoder().encodeToString(ret.rec) + ", ek=" + Base64.getEncoder().encodeToString(ret.export_key));

        OpaqueCredReq creq = o.createCredReq("password");
		System.out.println("sec=" + Base64.getEncoder().encodeToString(creq.sec) + ", pub=" + Base64.getEncoder().encodeToString(creq.pub));

        OpaqueCredResp cresp = o.createCredResp(creq.pub, ret.rec, ids, "context");
		System.out.println("sec=" + Base64.getEncoder().encodeToString(cresp.sec) + ", pub=" + Base64.getEncoder().encodeToString(cresp.pub));

        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids);
        
        System.out.println("export_key=====" + Base64.getEncoder().encodeToString(creds.export_key));
        System.out.println("sk=====" + Base64.getEncoder().encodeToString(creds.sk));

        assert o.userAuth(cresp.sec, creds.authU);
    }

    private static void test_privreg() {
        Opaque o = new Opaque();
        OpaqueRegReq regReq = o.createRegReq("password");

        OpaqueRegResp regResp = o.createRegResp(regReq.M);
        OpaqueIds ids = new OpaqueIds("idU".getBytes(Charset.forName("UTF-8")),
                                      "idS".getBytes(Charset.forName("UTF-8")));
        OpaquePreRecExpKey prerec = o.finalizeReg(regReq.sec, regResp.pub, ids);

        byte[] rec = o.storeRec(regResp.sec, prerec.rec);
		System.out.println("rec: " + toHex(rec) + "\n");

        OpaqueCredReq creq = o.createCredReq("password");
		System.out.println("sec=" + creq.sec + ", pub=" + creq.pub);

        OpaqueCredResp cresp = o.createCredResp(creq.pub, rec, ids, "context");
		System.out.println("sec=" + cresp.sec + ", pub=" + cresp.pub);

        OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids);

        assert o.userAuth(cresp.sec, creds.authU);
    }

    private static void test_priv1kreg() {
        Opaque o = new Opaque();
        OpaqueRegReq regReq = o.createRegReq("password");
        byte[] skS = fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        OpaqueRegResp regResp = o.createRegResp(regReq.M, skS);

        OpaqueIds ids = new OpaqueIds("2fc35cae-e0b7-40a5-b2aa-e86206730e99".getBytes(Charset.forName("UTF-8")),
                                      "algomeet.com".getBytes(Charset.forName("UTF-8")));
        
        OpaquePreRecExpKey prerec = o.finalizeReg(regReq.sec, regResp.pub, ids);

        byte[] rec = o.storeRec(regResp.sec, prerec.rec);
		System.out.println("rec: " + toHex(rec) + "\n");
        
		OpaqueCredReq creq = o.createCredReq("password");
		System.out.println("sec=" + creq.sec + ", pub=" + creq.pub);

        OpaqueCredResp cresp = o.createCredResp(creq.pub, rec, ids, "context");
		System.out.println("sec=" + cresp.sec + ", pub=" + cresp.pub);
	
		System.out.println("pub=====" + Base64.getEncoder().encodeToString(cresp.pub));
        System.out.println("creq=====" + Base64.getEncoder().encodeToString(creq.sec));
        System.out.println("rec=====" + Base64.getEncoder().encodeToString(rec));
        System.out.println("sec=====" + Base64.getEncoder().encodeToString(cresp.sec));
        
        String rec2 = "XCBKw4IAC0NtIfOyZPPIjFhHiq8TC9iz0dJb/spC+AAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eH2rT2hM9ZIwOSAVvdsTj0Ood1/O0eswlrAQ5MaE7CaNk3OlrXhlE3PQSWH+mB3Bu9vXrS5bCccjuM0ouWJtVW+rChmm+Io4599RFnnxuFzrTTpkF9R9A0PMMwgaPZx/IUs5ha7aV2DhgoVYKOtl0Na4R55mBEgF7C3lKi2xRA4lYyiTVHLgwXbozAkP8mGZ3nFonM17ldJvx0M649kmeOI6HaiocuW/DYoDEPNFYhr57uwfyzOsQEcnwweaDZfa8GQ==";
        //OpaqueCreds creds = o.recoverCreds(cresp.pub, creq.sec, "context", ids);
        Opaque o2 = new Opaque();
		OpaqueCredReq creq2 = o2.createCredReq("password");
		OpaqueCredResp cresp2 = o2.createCredResp(creq2.pub, Base64.getDecoder().decode(rec2), ids, "context");
        OpaqueCreds creds = o2.recoverCreds(cresp2.pub, creq2.sec, "context", ids);
        
        //QMxdo6DxkSFTxGhAXEQapbD9nRn2vgrw4Ilp7tMQf+QKp0YKUra9NDUogGNPH2AnQl4KVGQA2zd5/wNJqi8Spw==
        //QMxdo6DxkSFTxGhAXEQapbD9nRn2vgrw4Ilp7tMQf+QKp0YKUra9NDUogGNPH2AnQl4KVGQA2zd5/wNJqi8Spw==
        
        // export_key=====6r1EmCwMbrZMHd1sC/dGucqyNMo6km9nMIiDgVM842ErD92cXEl3aaSFBLid7ukVjlrprTd/F5sP1TJgsvcmlA==
        // sk=====SroW7d/zU3wD1eRgCF4zXscrUDzY4SGyKLO0aJIOyzxL7SAYxDJmzW5P2fv0kJFl/5hBODD9qrdU/1i4YpECPA==
        System.out.println("export_key=====" + Base64.getEncoder().encodeToString(creds.export_key));
        System.out.println("sk=====" + Base64.getEncoder().encodeToString(creds.sk));
        
        System.out.println(o2.userAuth(cresp2.sec, creds.authU));
    }

    // stackoverflowd from https://stackoverflow.com/a/140861
    public static byte[] fromHex(String s) {
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
