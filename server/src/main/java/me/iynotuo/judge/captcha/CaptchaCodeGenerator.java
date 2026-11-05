package me.iynotuo.judge.captcha;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates the short answer rendered into an image. Ambiguous characters (0/O, 1/I/L) are omitted
 * so a human can read the code reliably.
 */
@Component
public class CaptchaCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
