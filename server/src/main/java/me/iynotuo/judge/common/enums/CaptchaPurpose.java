package me.iynotuo.judge.common.enums;

/**
 * The action a CAPTCHA challenge is issued for. The client chooses the purpose when requesting a
 * challenge, but the server stores it and verifies it against the endpoint's hard-coded expected
 * value, so a challenge cannot be reused for another flow.
 */
public enum CaptchaPurpose {

    LOGIN,

    /**
     * Sending a verification mail, currently used by both registration and password reset.
     */
    EMAIL_SENDING
}
