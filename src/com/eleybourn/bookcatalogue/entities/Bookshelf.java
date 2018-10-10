package com.eleybourn.bookcatalogue.entities;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.Objects;

public class Bookshelf implements Serializable, Utils.ItemWithIdFixup {
    private static final long serialVersionUID = 1L;

    /** how to concat bookshelf names */
    public static final Character SEPARATOR = ',';

    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;

    public long id;
    public final String name;

    /**
     * Constructor
     */
    public Bookshelf(final long id, @NonNull final String name) {
        this.id = id;
        this.name = name.trim();
    }

    /**
     * Support for Serializable/encoding to a text file
     *
     * @return the object encoded as a String. If the format changes, update serialVersionUID
     *
     * "name"
     */
    @Override
    @NonNull
    public String toString() {
        return name;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.id = db.getBookshelfId(this);
        return this.id;
    }

    /**
     * Each Bookshelf is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Two bookshelves are equal if:
     * - it's the same Object duh..
     * - one or both of them is 'new' (error.g. id == 0) but their names are equal
     * - ids are equal
     *
     * Compare is CASE SENSITIVE !
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Bookshelf bookshelf = (Bookshelf) o;
        if (id == 0 || bookshelf.id == 0) {
            return Objects.equals(name, bookshelf.name);
        }
        return (id == bookshelf.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
