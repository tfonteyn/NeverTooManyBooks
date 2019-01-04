package com.eleybourn.bookcatalogue.backup;

import java.io.File;

import androidx.annotation.NonNull;

public class ImportSettings {

    /*
     * options as to *what* should be exported
     */
    public static final int BOOK_CSV = 1;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int XML_TABLES = 1 << 4;
    //public static final int IMPORT_5 = 1 << 5;
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;
    //public static final int DATABASE = 1 << 8; // pointless to implement. Just here for mirroring export flags

    /* Options value to indicate ALL things should be exported */
    public static final int ALL = BOOK_CSV | COVERS | BOOK_LIST_STYLES | PREFERENCES;
    public static final int NOTHING = 0;

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 16;

    /**
     * all defined flags
     */
    public static final int MASK = ALL | IMPORT_ONLY_NEW_OR_UPDATED;
    /**
     * Bitmask
     */
    public int what;

    /**
     * File to import from
     */
    public File file;

    public ImportSettings() {
    }

    public ImportSettings(@NonNull final File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "ImportSettings{" +
            "what=0%" + Integer.toBinaryString(what) +
            ", file=" + file +
            '}';
    }

    public void validate() {

    }
}
