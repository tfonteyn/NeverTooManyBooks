package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Bookshelf
        implements Parcelable, Utils.ItemWithIdFixup {

    /** how to concat bookshelf names. */
    public static final Character SEPARATOR = ',';

    /** the virtual 'All Books'. */
    public static final int ALL_BOOKS = -1;

    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;
    /** {@link Parcelable}. */
    public static final Creator<Bookshelf> CREATOR =
            new Creator<Bookshelf>() {
                @Override
                public Bookshelf createFromParcel(@NonNull final Parcel source) {
                    return new Bookshelf(source);
                }

                @Override
                public Bookshelf[] newArray(final int size) {
                    return new Bookshelf[size];
                }
            };

    public long id;
    @NonNull
    public String name;

    /**
     * Constructor.
     */
    public Bookshelf(@NonNull final String name) {
        this.name = name.trim();
    }

    /**
     * Constructor.
     */
    public Bookshelf(final long id,
                     @NonNull final String name) {
        this.id = id;
        this.name = name.trim();
    }

    protected Bookshelf(@NonNull final Parcel in) {
        id = in.readLong();
        name = in.readString();
    }

    /**
     * Special Formatter.
     *
     * @return the list of bookshelves formatted as "shelf1, shelf2, shelf3, ...
     */
    @NonNull
    public static String toDisplayString(@NonNull final List<Bookshelf> list) {
        List<String> allNames = new ArrayList<>();
        for (Bookshelf bookshelf : list) {
            allNames.add(bookshelf.name);
        }
        return Csv.toDisplayString(allNames);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Support for encoding to a text file.
     *
     * @return the object encoded as a String.
     *
     * "name"
     */
    @Override
    @NonNull
    public String toString() {
        return name;
    }

    @Override
    public long fixupId(@NonNull final DBA db) {
        this.id = db.getBookshelfIdByName(this.name);
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
     * Equality.
     *
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     * AND all their other fields are equal
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Bookshelf that = (Bookshelf) obj;
        if (this.id != 0 && that.id != 0 && this.id != that.id) {
            return false;
        }
        return Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
