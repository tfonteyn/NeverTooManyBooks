package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

public class ImportException extends RuntimeException {
    private static final long serialVersionUID = 1660687786319003483L;

    public ImportException(final @NonNull String s) {
        super(s);
    }
}
