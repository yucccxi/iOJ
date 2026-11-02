package me.iynotuo.judge.common.enums;

/**
 * Why an email verification code was issued.
 *
 * <p>MyBatis stores the enum by {@code name()}, so renaming a constant is a data migration.</p>
 */
public enum EmailCodePurpose {

    /**
     * Registration of a brand-new account.
     */
    REGISTER,

    /**
     * Reserved for a password reset flow; not issued by any endpoint yet.
     */
    RESET_PASSWORD
}
