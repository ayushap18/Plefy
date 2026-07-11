package com.plefy.app.backend.parser;

import com.plefy.app.model.RawRow;

import java.util.List;

/**
 * One worksheet of a workbook: its name plus its raw rows. Used by
 * {@link SheetReader#readAll(java.io.InputStream)} so multi-sheet files import every tab instead of
 * silently dropping all but the first.
 */
public final class NamedSheet {

    private final String name;
    private final List<RawRow> rows;

    public NamedSheet(String name, List<RawRow> rows) {
        this.name = name;
        this.rows = rows;
    }

    /** The sheet/tab name, or {@code null} for single-sheet formats such as CSV. */
    public String name() {
        return name;
    }

    /** The sheet's rows in source order. */
    public List<RawRow> rows() {
        return rows;
    }
}
