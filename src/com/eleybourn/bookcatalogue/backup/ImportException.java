package com.eleybourn.bookcatalogue.backup;

class ImportException extends RuntimeException {
    private static final long serialVersionUID = 1660687786319003483L;

    ImportException(String s) {
        super(s);
    }
}
