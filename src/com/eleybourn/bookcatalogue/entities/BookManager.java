package com.eleybourn.bookcatalogue.entities;

import android.support.annotation.NonNull;

public interface BookManager {

    BookManager getBookManager();

    @NonNull
    Book getBook();

    void setBook(@NonNull Book book);

    boolean isDirty();

    void setDirty(final boolean isDirty);


}
