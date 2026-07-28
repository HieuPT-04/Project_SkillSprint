package com.skillsprint.service.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Produces a deterministic, GitHub-style mirrored identicon without an object
 * upload. A real avatar always replaces this key through the existing S3 flow.
 */
public final class DefaultIdenticon {

    public static final String OBJECT_KEY_PREFIX = "default-identicon:";
    private static final String[] PALETTE = {
            "#2563eb", "#7c3aed", "#db2777", "#ea580c",
            "#16a34a", "#0891b2", "#4f46e5", "#be123c"
    };

    private DefaultIdenticon() {
    }

    public static String objectKeyFor(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("A user ID is required for a default identicon");
        }
        return OBJECT_KEY_PREFIX + userId;
    }

    public static boolean isDefaultObjectKey(String objectKey) {
        return objectKey != null && objectKey.startsWith(OBJECT_KEY_PREFIX)
                && objectKey.length() > OBJECT_KEY_PREFIX.length();
    }

    public static String dataUrl(String objectKey) {
        if (!isDefaultObjectKey(objectKey)) {
            return null;
        }
        String seed = objectKey.substring(OBJECT_KEY_PREFIX.length());
        byte[] hash = sha256(seed);
        String foreground = PALETTE[Byte.toUnsignedInt(hash[0]) % PALETTE.length];
        String accent = PALETTE[Byte.toUnsignedInt(hash[1]) % PALETTE.length];
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\" role=\"img\" aria-label=\"Default avatar\">");
        svg.append("<rect width=\"64\" height=\"64\" rx=\"12\" fill=\"#f8fafc\"/>");

        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 3; column++) {
                int bit = row * 3 + column;
                if (!isSet(hash, bit)) {
                    continue;
                }
                String color = bit % 4 == 0 ? accent : foreground;
                appendTile(svg, column, row, color);
                if (column < 2) {
                    appendTile(svg, 4 - column, row, color);
                }
            }
        }
        svg.append("</svg>");
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendTile(StringBuilder svg, int column, int row, String color) {
        svg.append("<rect x=\"").append(8 + column * 10)
                .append("\" y=\"").append(8 + row * 10)
                .append("\" width=\"8\" height=\"8\" rx=\"2\" fill=\"")
                .append(color).append("\"/>");
    }

    private static boolean isSet(byte[] hash, int bit) {
        return (Byte.toUnsignedInt(hash[bit / 8]) & (1 << (bit % 8))) != 0;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
