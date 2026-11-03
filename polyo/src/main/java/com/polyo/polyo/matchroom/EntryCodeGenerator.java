package com.polyo.polyo.matchroom;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class EntryCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[8];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
