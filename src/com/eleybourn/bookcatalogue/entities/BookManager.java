package com.eleybourn.bookcatalogue.entities;

import androidx.annotation.NonNull;

/**
 * A Fragment or an Activity can be the 'keeper' ('manager') of a Book.
 * Other parts of the current Fragment/Activity can get the Book by asking the BookManager
 * or can directly interact with the other methods.
 */
public interface BookManager {

    @NonNull
    Book getBook();

    void setBook(@NonNull Book book);

    default boolean isDirty() {
        return false;
    }

    default void setDirty(boolean isDirty) {
    }
}
