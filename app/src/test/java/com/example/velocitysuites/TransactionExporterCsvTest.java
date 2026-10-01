package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** CSV cell escaping (RFC 4180) and the export's page maths - the parts that need no Android runtime. */
public class TransactionExporterCsvTest {

    @Test
    public void plainValuesAreUntouched() {
        assertEquals("Deluxe", TransactionExporter.escapeCsv("Deluxe"));
        assertEquals("1800.00", TransactionExporter.escapeCsv("1800.00"));
        assertEquals("", TransactionExporter.escapeCsv(""));
    }

    @Test
    public void nullBecomesAnEmptyCell() {
        assertEquals("", TransactionExporter.escapeCsv(null));
    }

    @Test
    public void commasQuotesAndNewlinesAreQuoted_soTheyCantShiftColumns() {
        assertEquals("\"Deluxe, Garden View\"", TransactionExporter.escapeCsv("Deluxe, Garden View"));
        assertEquals("\"He said \"\"hi\"\"\"", TransactionExporter.escapeCsv("He said \"hi\""));
        assertEquals("\"line1\nline2\"", TransactionExporter.escapeCsv("line1\nline2"));
        assertEquals("\"a\rb\"", TransactionExporter.escapeCsv("a\rb"));
    }

    @Test
    public void aCellThatStartsLikeAFormulaIsDefused() {
        // "=HYPERLINK(...)" typed into a room name must not execute when the export is opened in a spreadsheet.
        assertEquals("'=1+1", TransactionExporter.escapeCsv("=1+1"));
        assertEquals("'@SUM(A1)", TransactionExporter.escapeCsv("@SUM(A1)"));
        assertEquals("'+cmd", TransactionExporter.escapeCsv("+cmd"));
    }

    @Test
    public void realNegativeNumbersAreLeftAlone() {
        assertEquals("-5.00", TransactionExporter.escapeCsv("-5.00"));
    }

    @Test
    public void fileNamesAreSortableAndSafe() {
        String name = TransactionExporter.fileName("csv", 1_790_000_000_000L);
        assertTrue(name, name.matches("Velocity_Suites_Transactions_\\d{8}_\\d{4}\\.csv"));
    }

    @Test
    public void pdfPaging_alwaysHasAtLeastOnePage_andGrowsWithRecords() {
        int perPage = TransactionExporter.rowsPerPage();
        assertTrue(perPage > 5);
        assertEquals(1, TransactionExporter.pageCount(0));
        assertEquals(1, TransactionExporter.pageCount(perPage));
        assertEquals(2, TransactionExporter.pageCount(perPage + 1));
        assertEquals(3, TransactionExporter.pageCount(perPage * 2 + 1));
    }
}
