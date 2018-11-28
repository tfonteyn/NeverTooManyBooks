package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.Date;

public class ExportSettings {
    /*
     * options as to *what* should be exported
     */
    public static final int NOTHING = 0;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int BOOK_DATA = 1 << 4;
    //    int DATABASE = 1 << 5;
    //    int EXPORT_6 = 1 << 6;
    //    int EXPORT_7 = 1 << 7;

    /* Options value to indicate ALL things should be exported */
    public static final int EXPORT_ALL = PREFERENCES | BOOK_LIST_STYLES | COVERS | BOOK_DATA;

    /*
     * Options to indicate new books or books with more recent update_date fields should be exported
     */

    /**
     * 0: all books
     * 1: books added/updated since {@link #dateFrom}. If the latter is null, then since last backup.
     */
    public static final int EXPORT_SINCE = 1 << 8; // 1: 'since';  '0': ALL

    /**
     * all defined flags
     */
    public static final int EXPORT_MASK = EXPORT_ALL | EXPORT_SINCE;

    /**
     * bitmask for the options
     */
    public int options = NOTHING;
    /**
     * EXPORT_SINCE.
     */
    @Nullable
    public Date dateFrom = null;

    /** file to exportBooks to */
    @NonNull
    public final File file;

    /**
     * Constructor
     *
     * @param file to exportBooks to
     */
    public ExportSettings(final @NonNull File file) {
        this.file = file;
    }
}
