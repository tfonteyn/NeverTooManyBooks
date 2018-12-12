package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Date;
import java.util.Objects;

public class ExportSettings {
    /*
     * options as to *what* should be exported
     */
    public static final int NOTHING = 0;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int BOOK_CSV = 1 << 4;
    public static final int XML_TABLES = 1 << 6;
    //public static final int EXPORT_7 = 1 << 7;
    //public static final int DATABASE = 1 << 8;

    /* Options value to indicate ALL things should be exported */
    public static final int EXPORT_ALL = PREFERENCES | BOOK_LIST_STYLES | COVERS
            | BOOK_CSV
            | XML_TABLES;

    /*
     * Options to indicate new books or books with more recent update_date fields should be exported
     *
     * 0: all books
     * 1: books added/updated since {@link #dateFrom}. If the latter is null, then since last backup.
     */
    public static final int EXPORT_SINCE = 1 << 16; // 1: 'since';  '0': ALL

    /**
     * all defined flags
     */
    public static final int EXPORT_MASK = EXPORT_ALL | EXPORT_SINCE;

    /** file to export to */
    @Nullable
    public File file;
    /**
     * bitmask for the options
     */
    public int what = NOTHING;

    /**
     * EXPORT_SINCE.
     */
    @Nullable
    public Date dateFrom = null;

    public ExportSettings() {
    }

    /**
     * Constructor
     *
     * @param file to export to
     */
    public ExportSettings(final @NonNull File file) {
        this.file = file;
    }

    public void validate() {
        // if we want 'since', we *must* have a valid dateFrom
        if ((what & ExportSettings.EXPORT_SINCE) != 0) {
            Objects.requireNonNull(dateFrom,"Export Failed - 'dateFrom' is null");
        } else {
            // sanity check: we don't want 'since', so make sure fromDate is not set.
            dateFrom = null;
        }
    }
}
