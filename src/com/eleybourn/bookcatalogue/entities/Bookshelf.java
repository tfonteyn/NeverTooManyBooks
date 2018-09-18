package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;

public class Bookshelf implements Serializable, Utils.ItemWithIdFixup {
    private static final long serialVersionUID = 1L;

    /**
     * Support for creation via Parcelable.
     * This is primarily useful for passing ArrayList<Bookshelf> in Bundles to activities.
     */
    public static final Parcelable.Creator<Bookshelf> CREATOR = new Parcelable.Creator<Bookshelf>() {
        public Bookshelf createFromParcel(Parcel in) {
            return new Bookshelf(in);
        }

        public Bookshelf[] newArray(int size) {
            return new Bookshelf[size];
        }
    };

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

    /**
     * Constructor using a Parcel.
     */
    private Bookshelf(@NonNull final Parcel in) {
        name = in.readString().trim();
        id = in.readLong();
    }

    @Override
    @NonNull
    public String toString() {
        return name;
    }

    /**
     * Replace local details from another Bookshelf
     *
     * @param source Bookshelf to copy
     */
    public void copyFrom(@NonNull final Bookshelf source) {
        name = source.name;
        id = source.id;
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
