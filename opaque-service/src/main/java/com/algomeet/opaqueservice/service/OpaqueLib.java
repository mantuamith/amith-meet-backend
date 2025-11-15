package com.algomeet.opaqueservice.service;

//---- OpaqueLib wrapper (pseudocode) ----
//Implement this wrapper with your chosen OPAQUE implementation.
//It should provide: server registration processing, server login KE2 generation,
//and server final verification (process KE3).
public class OpaqueLib {
 // Called on server during registration. Takes client's registration message and returns server message
 public static byte[] serverProcessRegistration(byte[] serverSk, byte[] clientRegMsg){
     // library-specific call
     throw new UnsupportedOperationException("Implement with your OPAQUE lib");
 }

 // Build the registrationRecord to store (opaque bytes)
 public static byte[] makeRegistrationRecord(byte[] serverSk, byte[] clientRegMsg){
     // typically returned by serverProcessRegistration or a second step
     throw new UnsupportedOperationException("Implement with your OPAQUE lib");
 }

 // Called during login: produce KE2 from stored registration record + client KE1
 public static byte[] serverLoginStep2(byte[] serverSk, byte[] registrationRecord, byte[] clientKe1){
     throw new UnsupportedOperationException("Implement with your OPAQUE lib");
 }

 // Called when server receives client's KE3: verify and derive session key; returns boolean
 public static boolean serverFinalize(byte[] serverSk, byte[] registrationRecord, byte[] clientKe3){
     throw new UnsupportedOperationException("Implement with your OPAQUE lib");
 }
}