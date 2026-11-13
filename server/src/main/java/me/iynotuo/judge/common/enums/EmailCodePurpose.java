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
     * Password reset flow; issued by {@code POST /auth/password-reset-codes} and verified by
     * {@code POST /auth/password-resets}.
     */
    RESET_PASSWORD
}
