package com.hardbacknutter.nevertomanybooks.database;

import androidx.annotation.NonNull;

/**
 * Debug exception to make it clear when we've got SQL issues...
 */
public class ColumnNotPresentException
        extends RuntimeException {

    private static final long serialVersionUID = -5065796313450875326L;

    public ColumnNotPresentException(@NonNull final String columnName) {
        super("Column " + columnName + " not present in cursor");
    }
}
