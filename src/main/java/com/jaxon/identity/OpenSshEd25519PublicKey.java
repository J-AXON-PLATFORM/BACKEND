package com.jaxon.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

final class OpenSshEd25519PublicKey {

    private static final String ALGORITHM = "ssh-ed25519";

    private OpenSshEd25519PublicKey() {}

    static Parsed parse(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\s+", 3);
        if (parts.length < 2 || !ALGORITHM.equals(parts[0])) {
            throw new InvalidKeyException();
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidKeyException();
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        byte[] algorithm = readField(buffer);
        byte[] key = readField(buffer);
        if (buffer.hasRemaining()
                || !Arrays.equals(algorithm, ALGORITHM.getBytes(StandardCharsets.US_ASCII))
                || key.length != 32) {
            throw new InvalidKeyException();
        }
        String normalized = ALGORITHM + " " + Base64.getEncoder().encodeToString(blob);
        String fingerprint = "SHA256:"
                + Base64.getEncoder().withoutPadding().encodeToString(sha256(blob));
        return new Parsed(normalized, fingerprint);
    }

    private static byte[] readField(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new InvalidKeyException();
        }
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new InvalidKeyException();
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return value;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Parsed(String normalized, String fingerprint) {}

    static final class InvalidKeyException extends RuntimeException {}
}
