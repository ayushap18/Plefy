package com.plefy.app.backend.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.RawRow;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Regression test for the XLSX streaming path: a native Excel <em>date</em> cell (a number with a
 * date number-format) must be surfaced as a formatted date string, not the raw serial. Without the
 * styles-table wiring in {@link PoiSpreadsheetReader} the date column would import as an integer.
 */
public class PoiXlsxDateTest {

    private static byte[] xlsxWithDateAndNumber() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Data");
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("When");
            header.createCell(1).setCellValue("Count");

            XSSFRow data = sheet.createRow(1);
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(2024, Calendar.JULY, 12); // 2024-07-12
            XSSFCell dateCell = data.createCell(0);
            dateCell.setCellValue(cal);
            dateCell.setCellStyle(dateStyle);
            data.createCell(1).setCellValue(42); // plain number, General format

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    public void dateFormattedNumericCell_isRenderedAsDate_notSerial() throws Exception {
        List<RawRow> rows;
        try (ByteArrayInputStream in = new ByteArrayInputStream(xlsxWithDateAndNumber())) {
            rows = new PoiSpreadsheetReader().read(in, null);
        }

        assertEquals(2, rows.size());
        String dateValue = rows.get(1).getCells().get(0);
        String numberValue = rows.get(1).getCells().get(1);

        // Excel serial for 2024-07-12 is 45485 — the raw serial must NOT leak through.
        assertNotEquals("45485", dateValue);
        assertTrue("expected a formatted date, got: " + dateValue, dateValue.contains("2024-07-12"));

        // A plain General-format number stays a bare number (still classifiable as numeric),
        // not run through any date format.
        assertEquals(42.0, Double.parseDouble(numberValue), 0.0);
    }
}
