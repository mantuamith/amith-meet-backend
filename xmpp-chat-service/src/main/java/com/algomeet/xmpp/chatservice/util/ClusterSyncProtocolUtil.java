package com.algomeet.xmpp.chatservice.util;

public class ClusterSyncProtocolUtil {

    // Non-printable ASCII Unit Separator used as field delimiter
    // for compact cluster sync messages.
    public static final char SEP = 31;

    // Current protocol version identifier.
    public static final String v1 = "v1";

    // Expected number of fields for protocol version v1 payloads.
    public static final int V1_FIELD_COUNT = 10;

}