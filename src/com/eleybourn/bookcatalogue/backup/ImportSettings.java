package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import java.io.File;

public class ImportSettings {
    /*
     * options as to *what* should be exported
     */
    public static final int NOTHING = 0;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int BOOK_DATA = 1 << 4;
    //public static final int DATABASE = 1 << 5; // pointless to implement. Just here for mirroring export flags
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;

    public static final int IMPORT_ALL = BOOK_DATA | COVERS | BOOK_LIST_STYLES | PREFERENCES;

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 8;

    /**
     * all defined flags
     */
    public static final int IMPORT_MASK = IMPORT_ALL | IMPORT_ONLY_NEW_OR_UPDATED;
    /**
     * Bitmask
     * */
    public int what;

    /**
     * File to import from
     */
    public File file;

    public ImportSettings() {
    }

    public ImportSettings(final @NonNull File file) {
        this.file = file;
    }

    public void copyFrom(final ImportSettings settings) {

    }
}
