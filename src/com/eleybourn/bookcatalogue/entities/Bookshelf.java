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

    /** the virtual 'All Books' */
    public static final int ALL_BOOKS = 0;
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;

    public long id;
    @NonNull
    public final String name;

    /**
     * Constructor
     *
     */
    public Bookshelf(final @NonNull String name) {
        this.name = name.trim();
    }

    /**
     * Constructor
     */
    public Bookshelf(final long id, final @NonNull String name) {
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
    public long fixupId(final @NonNull CatalogueDBAdapter db) {
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
     * - one or both of them is 'new' (e.g. id == 0) but their names are equal
     *   TOMF: but what about 'all books' ? that's id==0 as well.
     * - ids are equal
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(final @Nullable Object o) {
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
