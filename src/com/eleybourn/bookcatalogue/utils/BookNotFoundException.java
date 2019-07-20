package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.Nullable;

public class BookNotFoundException
        extends Exception {


    public BookNotFoundException() {
    }

    public BookNotFoundException(@Nullable final String isbn) {
        super(isbn);
    }
}
