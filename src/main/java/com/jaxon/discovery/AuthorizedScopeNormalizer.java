package com.jaxon.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

final class AuthorizedScopeNormalizer {

    private AuthorizedScopeNormalizer() {}

    static Name normalizeName(String input) {
        if (input == null) {
            throw new InvalidAuthorizedScopeException("name");
        }
        String display = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("(?U)\\s+", " ")
                .trim();
        String normalized = display.toLowerCase(Locale.ROOT)
                .toUpperCase(Locale.ROOT)
                .toLowerCase(Locale.ROOT);
        if (display.isEmpty()
                || display.codePointCount(0, display.length()) > 200
                || normalized.codePointCount(0, normalized.length()) > 200) {
            throw new InvalidAuthorizedScopeException("name");
        }
        return new Name(display, normalized);
    }

    static String normalizeCidr(String input) {
        if (input == null || !input.equals(input.trim())) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        int slash = input.indexOf('/');
        if (slash < 1 || slash != input.lastIndexOf('/') || slash == input.length() - 1) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        String addressPart = input.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(input.substring(slash + 1));
        } catch (NumberFormatException exception) {
            throw new InvalidAuthorizedScopeException("cidr");
        }

        byte[] address = addressPart.contains(":") ? ipv6(addressPart) : ipv4(addressPart);
        int maximumPrefix = address.length * Byte.SIZE;
        if (prefix < 0 || prefix > maximumPrefix) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        byte[] network = applyMask(address, prefix);
        if (!Arrays.equals(address, network)) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        return (address.length == 4 ? formatIpv4(network) : formatIpv6(network)) + "/" + prefix;
    }

    private static byte[] ipv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        byte[] result = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].matches("0|[1-9][0-9]{0,2}")) {
                throw new InvalidAuthorizedScopeException("cidr");
            }
            int octet = Integer.parseInt(parts[index]);
            if (octet > 255) {
                throw new InvalidAuthorizedScopeException("cidr");
            }
            result[index] = (byte) octet;
        }
        return result;
    }

    private static byte[] ipv6(String value) {
        if (!value.matches("[0-9a-fA-F:]+") || !value.contains(":")) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
        try {
            byte[] result = InetAddress.getByName(value).getAddress();
            if (result.length != 16) {
                throw new InvalidAuthorizedScopeException("cidr");
            }
            return result;
        } catch (UnknownHostException exception) {
            throw new InvalidAuthorizedScopeException("cidr");
        }
    }

    private static byte[] applyMask(byte[] address, int prefix) {
        byte[] network = address.clone();
        for (int bit = prefix; bit < network.length * Byte.SIZE; bit++) {
            network[bit / Byte.SIZE] &= (byte) ~(1 << (7 - bit % Byte.SIZE));
        }
        return network;
    }

    private static String formatIpv4(byte[] address) {
        return String.join(
                ".",
                Integer.toString(Byte.toUnsignedInt(address[0])),
                Integer.toString(Byte.toUnsignedInt(address[1])),
                Integer.toString(Byte.toUnsignedInt(address[2])),
                Integer.toString(Byte.toUnsignedInt(address[3])));
    }

    private static String formatIpv6(byte[] address) {
        int[] groups = new int[8];
        for (int index = 0; index < groups.length; index++) {
            groups[index] = Byte.toUnsignedInt(address[index * 2]) << 8
                    | Byte.toUnsignedInt(address[index * 2 + 1]);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int start = 0; start < groups.length; ) {
            if (groups[start] != 0) {
                start++;
                continue;
            }
            int end = start;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            if (end - start > bestLength && end - start >= 2) {
                bestStart = start;
                bestLength = end - start;
            }
            start = end;
        }

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < groups.length; index++) {
            if (index == bestStart) {
                result.append("::");
                index += bestLength - 1;
                continue;
            }
            if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
                result.append(':');
            }
            result.append(Integer.toHexString(groups[index]));
        }
        return result.toString();
    }

    record Name(String display, String normalized) {}
}

final class InvalidAuthorizedScopeException extends RuntimeException {
    private final String field;

    InvalidAuthorizedScopeException(String field) {
        this.field = field;
    }

    String field() {
        return field;
    }
}
