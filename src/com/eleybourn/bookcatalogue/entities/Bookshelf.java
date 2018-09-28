package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;

public class Bookshelf implements Serializable, Utils.ItemWithIdFixup {
    private static final long serialVersionUID = 1L;

    /** how to concat bookshelf names */
    public static final Character SEPARATOR = ',';

    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;

    public long id;
    public String name;

    /**
     * Constructor
     *
     * @param id     ID of Bookshelf in DB (0 if not in DB)
     */
    public Bookshelf(long id, @NonNull final String name) {
        this.id = id;
        this.name = name.trim();
    }

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

    @Override
    public long getId() {
        return id;
    }

    /**
     * Each Bookshelf is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

}
