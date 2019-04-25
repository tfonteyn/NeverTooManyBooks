package com.eleybourn.bookcatalogue.entities;

import androidx.annotation.NonNull;

/**
 * A Fragment or an Activity can be the 'keeper' ('manager') of a Book.
 * Other parts of the current Fragment/Activity can get the Book by asking the BookManager
 * or can directly interact with the other methods.
 */
public interface BookManager {

    @NonNull
    BookManager getBookManager();

    @NonNull
    Book getBook();

    void setBook(@NonNull Book book);

    /**
     * @return {@code true} if our data was changed.
     */
    default boolean isDirty() {
        return false;
    }

    /**
     * @param isDirty set to {@code true} if our data was changed.
     */
    default void setDirty(boolean isDirty) {
    }
}
