package com.algomeet.userservice.constants;

/** Non-localizable constants used across controllers/services. */
public final class ApiConstants {

    private ApiConstants() {}

    // Common map keys
    public static final String KEY_CODE    = "code";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_FIELDS  = "fields";
    public static final String KEY_USER    = "user";
    public static final String KEY_SID     = "sid";
    public static final String KEY_ERROR   = "error";

    // Exists() response keys
    public static final String KEY_EMAIL_TAKEN    = "emailTaken";
    public static final String KEY_USERNAME_TAKEN = "usernameTaken";
    public static final String KEY_PHONE_TAKEN    = "phoneTaken";

    // Request parameter names
    public static final String PARAM_LOGIN         = "login";
    public static final String PARAM_EMAIL         = "email";
    public static final String PARAM_PASSWORD_HASH = "passwordHash";
    public static final String PARAM_DEVICE_TYPE   = "deviceType";
    public static final String PARAM_DEVICE_TOKEN  = "deviceToken";


    // Misc
    public static final int    MAX_BATCH = 500;
    public static final String DUPLICATE_FALLBACK_DETAILS = "duplicate";
}
