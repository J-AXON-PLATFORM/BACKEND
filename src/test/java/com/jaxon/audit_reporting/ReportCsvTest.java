package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCsvTest {

    @Test
    void quotesEveryCellUsesCrlfAndProtectsTextAgainstSpreadsheetInjection() {
        List<String> dangerous = List.of(
                "=1+1", "+cmd", "-2", "@sum", "  =formula", "\tvalue", "\rvalue", "\nvalue",
                "normal", "comma,value", "quote\"value", "áé");
        List<List<ReportCsv.Cell>> rows = dangerous.stream()
                .map(value -> List.of(new ReportCsv.Cell(value, true), new ReportCsv.Cell(value, false)))
                .toList();

        String csv = new String(
                ReportCsv.encode(List.of("protected", "plain"), rows), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\"protected\",\"plain\"\r\n");
        assertThat(csv).contains("\"'=1+1\",\"=1+1\"\r\n");
        assertThat(csv).contains("\"'  =formula\",\"  =formula\"\r\n");
        assertThat(csv).contains("\"'\tvalue\"", "\"'\rvalue\"", "\"'\nvalue\"");
        assertThat(csv).contains("\"comma,value\"", "\"quote\"\"value\"", "\"áé\"");
        assertThat(csv.charAt(0)).isEqualTo('"');
    }

    @Test
    void writesNullAsAnEmptyQuotedCell() {
        String csv = new String(
                ReportCsv.encode(
                        List.of("nullable"),
                        List.of(List.of(new ReportCsv.Cell(null, true)))),
                StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("\"nullable\"\r\n\"\"\r\n");
    }

    @Test
    void acceptsExactlyTenMebibytesAndRejectsOneByteMore() {
        int framingBytes = "\"value\"\r\n".length() + "\"\"\r\n".length();
        String exact = "a".repeat(ReportCsv.MAX_BYTES - framingBytes);

        assertThat(ReportCsv.encode(
                        List.of("value"),
                        List.of(List.of(new ReportCsv.Cell(exact, true)))))
                .hasSize(ReportCsv.MAX_BYTES);
        assertThatThrownBy(() -> ReportCsv.encode(
                        List.of("value"),
                        List.of(List.of(new ReportCsv.Cell(exact + "a", true)))))
                .isInstanceOf(ReportExportLimitException.class);
    }
}
