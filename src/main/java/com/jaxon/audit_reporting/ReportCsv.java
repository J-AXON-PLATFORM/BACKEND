package com.jaxon.audit_reporting;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class ReportCsv {

    static final int MAX_BYTES = 10 * 1024 * 1024;

    private ReportCsv() {}

    static byte[] encode(List<String> header, List<List<Cell>> rows) {
        StringBuilder csv = new StringBuilder();
        append(csv, header.stream().map(value -> new Cell(value, false)).toList());
        for (List<Cell> row : rows) {
            append(csv, row);
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new ReportExportLimitException();
        }
        return bytes;
    }

    private static void append(StringBuilder csv, List<Cell> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) csv.append(',');
            String value = cells.get(index).value();
            if (value == null) value = "";
            if (cells.get(index).protect()) value = protect(value);
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private static String protect(String value) {
        if (value.isEmpty()) return value;
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            if (codePoint == '\t' || codePoint == '\r' || codePoint == '\n') return "'" + value;
            index += Character.charCount(codePoint);
        }
        if (index < value.length()) {
            int first = value.codePointAt(index);
            if (first == '=' || first == '+' || first == '-' || first == '@') return "'" + value;
        }
        return value;
    }

    record Cell(String value, boolean protect) {}
}
