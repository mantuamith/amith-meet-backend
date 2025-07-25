package com.algomeet.meetservice.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomIdGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final int PART1_LEN = 4;
    private static final int PART2_LEN = 3;
    private static final int PART3_LEN = 4;

    public static String generateId() {
        StringBuilder idBuilder = new StringBuilder(PART1_LEN + PART2_LEN + PART3_LEN + 2); // +2 for hyphens

        appendRandomLetters(idBuilder, PART1_LEN);
        idBuilder.append('-');
        appendRandomLetters(idBuilder, PART2_LEN);
        idBuilder.append('-');
        appendRandomLetters(idBuilder, PART3_LEN);

        return idBuilder.toString();
    }

    private static void appendRandomLetters(StringBuilder builder, int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            char c = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            builder.append(c);
        }
    }
}
