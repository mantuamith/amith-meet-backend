package com.algomeet.signalservice.constant;

import java.util.UUID;

public class Constants {	
	
	public static final String TOKEN_PREFIX = "Bearer";
	public static final String AUTHORIZATION = "Authorization";
	public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
	
	public static final UUID NIL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	
	// The absolute smallest, structurally valid UUID v7
	public static final UUID SMALLEST_UUID_V7 = UUID.fromString("00000000-0000-7000-8000-000000000000");
	
	public static final UUID LARGEST_UUID_V7 = UUID.fromString("ffffffff-ffff-7fff-bfff-ffffffffffff");
}
