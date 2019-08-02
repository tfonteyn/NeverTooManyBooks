package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.Nullable;

public class BookNotFoundException
        extends Exception {


    public BookNotFoundException() {
    }

    public BookNotFoundException(@Nullable final String isbn) {
        super(isbn);
    }
}
