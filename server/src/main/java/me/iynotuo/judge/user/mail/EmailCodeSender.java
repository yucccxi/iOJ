package me.iynotuo.judge.user.mail;

import me.iynotuo.judge.common.enums.EmailCodePurpose;

/**
 * Delivers a verification code to a mailbox.
 *
 * <p>Kept as an interface so tests can capture codes without an SMTP server, and so a different
 * transport (an HTTP mail API, for example) only needs a new implementation.</p>
 */
public interface EmailCodeSender {

    /**
     * Sends the code.
     *
     * @param email      recipient address, already normalized (trimmed and lower-cased)
     * @param code       the 6-digit code
     * @param ttlMinutes how long the code stays valid, used in the mail body
     * @param purpose    why the code was issued; decides the subject and the wording, so a
     *                   registration code can never be mistaken for a password reset code
     * @throws RuntimeException when the mail could not be handed to the mail server
     */
    void send(String email, String code, int ttlMinutes, EmailCodePurpose purpose);
}
