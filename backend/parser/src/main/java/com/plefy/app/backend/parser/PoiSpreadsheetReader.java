package com.plefy.app.backend.parser;

import com.plefy.app.model.RawRow;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads binary spreadsheet formats using Apache POI.
 *
 * <p>This is the <em>only</em> class in the module that imports {@code org.apache.poi}; the
 * rest of the parser package depends solely on the JDK and {@code :core:model}. That keeps
 * POI concerns quarantined behind the {@link SheetReader} interface.
 *
 * <p>Format handling:
 * <ul>
 *   <li><b>.xlsx</b> is read with the POI <em>event (SAX) model</em>
 *       ({@link XSSFReader}) rather than the DOM user-model. This streams one row at a time
 *       and never materialises the whole sheet XML, so workbooks with hundreds of thousands
 *       of rows stay memory-safe.</li>
 *   <li><b>.xls</b> is read with {@link HSSFWorkbook}. The legacy BIFF format is capped at
 *       65&nbsp;536 rows, so an in-memory read is bounded and acceptable.</li>
 * </ul>
 *
 * <p>For both formats the <em>cached</em> value of a formula cell is returned (never a
 * re-evaluation), error cells map to {@code null}, and missing/blank leading cells are
 * surfaced as {@code null} in the correct column position.
 */
public final class PoiSpreadsheetReader implements SheetReader {

    static {
        // POI caps a single record's byte array at 100 MB as a zip-bomb / OOM guard. Real workbooks
        // (large shared-strings tables, embedded images) can exceed that legitimately, so raise the
        // ceiling. Runs once on class load, before any read.
        IOUtils.setByteArrayMaxOverride(300_000_000);
        // Also disable the inflate-ratio guard: a genuine sheet with a big, highly compressible
        // shared-strings table trips POI's zip-bomb heuristic and aborts. Heap is bounded by
        // android:largeHeap instead. Together these mean a valid file never fails on an artificial cap.
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.0);
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls");
    }

    @Override
    public List<RawRow> read(InputStream in, String sheetName) throws Exception {
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        // FileMagic requires a mark-supporting stream so it can peek without consuming.
        InputStream stream = in.markSupported() ? in : new BufferedInputStream(in);
        FileMagic magic = FileMagic.valueOf(stream);
        switch (magic) {
            case OOXML:
                return readXlsx(stream, sheetName);
            case OLE2:
                return readXls(stream, sheetName);
            default:
                throw new IllegalArgumentException(
                        "Unsupported binary spreadsheet content: " + magic);
        }
    }

    @Override
    public List<NamedSheet> readAll(InputStream in) throws Exception {
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        InputStream stream = in.markSupported() ? in : new BufferedInputStream(in);
        FileMagic magic = FileMagic.valueOf(stream);
        switch (magic) {
            case OOXML:
                return readAllXlsx(stream);
            case OLE2:
                return readAllXls(stream);
            default:
                throw new IllegalArgumentException(
                        "Unsupported binary spreadsheet content: " + magic);
        }
    }

    private static List<NamedSheet> readAllXlsx(InputStream stream) throws Exception {
        List<NamedSheet> out = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(stream)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    String name = sheets.getSheetName();
                    out.add(new NamedSheet(name, parseSheetXml(sheetStream, sst, styles)));
                }
            }
        }
        return out;
    }

    private static List<NamedSheet> readAllXls(InputStream stream) throws Exception {
        List<NamedSheet> out = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (HSSFWorkbook workbook = new HSSFWorkbook(stream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                out.add(new NamedSheet(sheet.getSheetName(), readSheetRows(sheet, formatter)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // XLSX (streaming, SAX event model)
    // ------------------------------------------------------------------

    private static List<RawRow> readXlsx(InputStream stream, String sheetName) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(stream)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            // Each stream from SheetIterator.next() is only valid until the following call to
            // next(), so a sheet must be parsed before advancing the iterator.
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    String name = sheets.getSheetName();
                    if (sheetName == null || sheetName.equals(name)) {
                        return parseSheetXml(sheetStream, sst, styles);
                    }
                }
            }

            if (sheetName != null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }
            // No sheets at all.
            return new ArrayList<>();
        }
    }

    private static List<RawRow> parseSheetXml(InputStream sheetStream, SharedStrings sst,
                                              StylesTable styles) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader xmlReader = factory.newSAXParser().getXMLReader();
        SheetXmlHandler handler = new SheetXmlHandler(sst, styles);
        xmlReader.setContentHandler(handler);
        xmlReader.parse(new InputSource(sheetStream));
        return handler.rows;
    }

    /**
     * SAX handler for the {@code sheetN.xml} part of an XLSX workbook.
     *
     * <p>Working at this level (rather than via {@code XSSFSheetXMLHandler}) gives direct
     * access to the cell {@code t} (type) attribute, which is what lets us map error cells to
     * {@code null} and reconstruct missing cells from their {@code r} (reference) attribute.
     */
    private static final class SheetXmlHandler extends DefaultHandler {

        final List<RawRow> rows = new ArrayList<>();
        private final SharedStrings sst;
        private final StylesTable styles;
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

        private final List<String> rowCells = new ArrayList<>();
        private final StringBuilder value = new StringBuilder();

        private int rowIndex = -1;
        private int expectedColumn = 0;
        private String cellType;   // value of the "t" attribute, e.g. "s", "b", "e", "str"
        private int styleIndex;    // value of the "s" attribute (index into cellXfs), or -1
        private boolean capturing; // whether we are inside a <v> or inline <t>

        SheetXmlHandler(SharedStrings sst, StylesTable styles) {
            this.sst = sst;
            this.styles = styles;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            String name = localName.isEmpty() ? qName : localName;
            switch (name) {
                case "row": {
                    rowCells.clear();
                    expectedColumn = 0;
                    String r = attrs.getValue("r");
                    rowIndex = (r != null) ? (parseInt(r, rowIndex + 2) - 1) : (rowIndex + 1);
                    break;
                }
                case "c": {
                    cellType = attrs.getValue("t");
                    styleIndex = parseInt(attrs.getValue("s"), -1);
                    String ref = attrs.getValue("r");
                    int col = (ref != null) ? colFromRef(ref) : expectedColumn;
                    // Fill any gap (missing cells) with nulls so column positions line up.
                    while (expectedColumn < col) {
                        rowCells.add(null);
                        expectedColumn++;
                    }
                    value.setLength(0);
                    break;
                }
                case "v":
                case "t": // inline string text
                    capturing = true;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (capturing) {
                value.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName.isEmpty() ? qName : localName;
            switch (name) {
                case "v":
                case "t":
                    capturing = false;
                    break;
                case "c": {
                    rowCells.add(resolveCellValue());
                    expectedColumn++;
                    break;
                }
                case "row":
                    rows.add(new RawRow(rowIndex, new ArrayList<>(rowCells)));
                    break;
                default:
                    break;
            }
        }

        /** Interprets the captured raw text according to the cell's declared type. */
        private String resolveCellValue() {
            String raw = value.toString();
            // A numeric cell is either untyped (t absent) or explicitly t="n". A date-formatted
            // number is stored as an Excel serial (e.g. 45485), so render it through the cell's
            // number format — matching the .xls path — rather than surfacing the bare serial
            // (which inference would read as an integer). Non-date numbers keep their verbatim value.
            if (cellType == null || "n".equals(cellType)) {
                if (raw.isEmpty()) {
                    return null;
                }
                return formatIfDate(raw);
            }
            switch (cellType) {
                case "s": { // shared string: raw is an index into the shared strings table
                    try {
                        int idx = Integer.parseInt(raw.trim());
                        return sst.getItemAt(idx).getString();
                    } catch (RuntimeException ex) {
                        return null;
                    }
                }
                case "inlineStr":
                case "str": // formula string result (cached)
                    return raw.isEmpty() ? "" : raw;
                case "b": // boolean stored as "1"/"0"
                    return "1".equals(raw) ? "TRUE" : "FALSE";
                case "e": // error cell -> null per spec
                    return null;
                default:
                    return raw.isEmpty() ? null : raw;
            }
        }

        /**
         * If the current cell's style is a date/time number format, renders the Excel serial in
         * [raw] as a formatted date string (as the HSSF path does); otherwise returns [raw]
         * unchanged. Any parsing/style issue degrades gracefully to the verbatim value.
         */
        private String formatIfDate(String raw) {
            if (styles == null || styleIndex < 0) {
                return raw;
            }
            try {
                XSSFCellStyle style = styles.getStyleAt(styleIndex);
                if (style == null) {
                    return raw;
                }
                short fmtIndex = style.getDataFormat();
                String fmtString = style.getDataFormatString();
                if (DateUtil.isADateFormat(fmtIndex, fmtString)) {
                    double serial = Double.parseDouble(raw);
                    return formatter.formatRawCellContents(serial, fmtIndex, fmtString);
                }
            } catch (RuntimeException ex) {
                // Bad style index or non-numeric content: fall back to the verbatim value.
            }
            return raw;
        }

        private static int parseInt(String s, int fallback) {
            if (s == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }

    // ------------------------------------------------------------------
    // XLS (legacy BIFF, in-memory HSSF)
    // ------------------------------------------------------------------

    private static List<RawRow> readXls(InputStream stream, String sheetName) throws Exception {
        List<RawRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (HSSFWorkbook workbook = new HSSFWorkbook(stream)) {
            Sheet sheet = (sheetName != null)
                    ? workbook.getSheet(sheetName)
                    : (workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null);
            if (sheet == null) {
                if (sheetName != null) {
                    throw new IllegalArgumentException("Sheet not found: " + sheetName);
                }
                return rows;
            }
            return readSheetRows(sheet, formatter);
        }
    }

    /**
     * Zero-based column index from a cell reference like {@code "B12"} — a cheap char scan
     * (A=0, B=1, Z=25, AA=26, ...). Replaces {@code new CellReference(ref).getCol()}, whose
     * per-cell regex parse dominated xlsx import time (tens of seconds for ~100k cells).
     * Package-private for unit testing.
     */
    static int colFromRef(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else {
                break; // reached the row-number digits
            }
        }
        return col - 1;
    }

    /** Extracts every row of a loaded HSSF/XSSF {@link Sheet} into raw strings. */
    private static List<RawRow> readSheetRows(Sheet sheet, DataFormatter formatter) {
        List<RawRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                rows.add(new RawRow(r, new ArrayList<>()));
                continue;
            }
            int lastCol = row.getLastCellNum(); // 1-based count, -1 if empty
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < lastCol; c++) {
                Cell cell = row.getCell(c);
                cells.add(cellToString(cell, formatter));
            }
            rows.add(new RawRow(r, cells));
        }
        return rows;
    }

    /**
     * Renders a single cell to its raw string form, reading cached formula results and mapping
     * error cells to {@code null}.
     */
    private static String cellToString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case BLANK:
                return null;
            case ERROR:
                return null;
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // formatCellValue honours date/number formatting for the cached value.
                return formatter.formatCellValue(cell);
            default:
                return formatter.formatCellValue(cell);
        }
    }
}
