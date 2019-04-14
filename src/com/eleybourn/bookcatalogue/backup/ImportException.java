package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;

public class ImportException
        extends RuntimeException {

    private static final long serialVersionUID = 1660687786319003483L;

    public ImportException(@NonNull final String s) {
        super(s);
    }
}
