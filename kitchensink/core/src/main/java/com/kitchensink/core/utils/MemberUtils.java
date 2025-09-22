package com.kitchensink.core.utils;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

@Slf4j
public class MemberUtils {

    private MemberUtils() {
        //private constructor
    }

    public static boolean hasText(final String s) {
        return s != null && !s.trim().isEmpty();
    }

    public static String generateTempPassword() {
        final var alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*?";
        return new SecureRandom().ints(16, 0, alphabet.length())
                .collect(StringBuilder::new,
                        (sb, idx) -> sb.append(alphabet.charAt(idx)),
                        StringBuilder::append)
                .toString();
    }

    // MemberUtils.java
    public static LocalDate tryParseLocalDate(String s) {
        if (!hasText(s)) return null;
        try {
            return LocalDate.parse(s); // or with a DateTimeFormatter.ISO_LOCAL_DATE
        } catch (DateTimeParseException e) {
            // return null silently (or log at DEBUG if you prefer)
            return null;
        }
    }

    public static String formatDate(final LocalDate localDate) {
        return localDate == null ? "" : localDate.toString();
    }

    public static String emptyIfNull(final String s) {
        return s == null ? "" : s;
    }

    public static String toProperCase(final String name) {
        return Arrays.stream(name.split("\\s+"))
                .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .collect(joining(" "));
    }
}
