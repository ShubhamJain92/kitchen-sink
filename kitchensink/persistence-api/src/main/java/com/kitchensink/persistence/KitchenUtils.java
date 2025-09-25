package com.kitchensink.persistence;

public class KitchenUtils {

    private KitchenUtils() {
        //private constructor
    }

    public static String collapseWhitespace(final String s) {
        if (s == null) return null;
        final var t = s.trim();
        return t.replaceAll("\\s+", " ");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
