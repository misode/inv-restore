package io.github.misode.invrestore;

import java.util.Random;

public class RandomBase62 {
    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    public static String generate(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return result.toString();
    }
}
