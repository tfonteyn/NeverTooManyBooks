package com.eleybourn.bookcatalogue.baseactivity;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.entities.Book;

public interface HasBook {
    @NonNull
    Book getBook();

    void setBookId(final long bookId);
}
