package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

class ImportException extends RuntimeException {
    private static final long serialVersionUID = 1660687786319003483L;

    ImportException(@NonNull final String s) {
        super(s);
    }
}
