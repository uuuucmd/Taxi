package com.flytaxi.internalCommon.util;

import java.security.SecureRandom;

/**
 * Generates numeric one-time verification codes for authentication flows.
 */
public final class VerificationCodeUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 9;

    private VerificationCodeUtils() {
    }

    public static int generate(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("Verification code size must be between 1 and 9");
        }
        int minimum = (int) Math.pow(10, size - 1);
        int maximum = (int) Math.pow(10, size) - 1;
        return minimum + RANDOM.nextInt(maximum - minimum + 1);
    }
}
