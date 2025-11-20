package com.algomeet.userservice.constants;

/** i18n message keys resolved via MessageSource. */
public final class MsgKeys {

    private MsgKeys() {}

    // Users
    public static final String USER_NOT_FOUND       = "users.error.notFound";
    public static final String USER_DELETE_SUCCESS  = "users.delete.success";
    public static final String PRID_ALLOCATE_FAILED = "users.prid.allocate.failed";

    // Common
    public static final String PAYLOAD_TOO_LARGE = "common.error.payloadTooLarge";
    public static final String ACCESS_DENIED     = "common.error.accessDenied";
    public static final String INVALID_UUID_SKIPPED = "common.info.invalidUuidSkipped";
}
