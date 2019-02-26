package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.List;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_STYLE_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;

/**
 * Represents a Bookshelf.
 */
public class Bookshelf
        implements Parcelable, Utils.ItemWithIdFixup {

    /**
     * how to concat bookshelf names. This should be using '|' as {@link StringList}
     * but backwards compatibility rules here.
     */
    public static final Character MULTI_SHELF_SEPARATOR = ',';
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
    /**
     * Preference name - the bookshelf to load next time we startup.
     * Storing the name and not the id. If you export/import... the id will be different.
     */
    public static final String PREF_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    /** String encoding use. */
    private static final char FIELD_SEPARATOR = '*';
    /** the virtual 'All Books'. */
    private static final int ALL_BOOKS = -1;
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    private static final int DEFAULT_ID = 1;
    private long mId;
    @NonNull
    private String mName;

    /**
     * the style id. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle(DBA)}}
     */
    private long mStyleId;

    /** the style gets cached. It only gets reloaded when the mStyleId != cached one. */
    private BooklistStyle mCachedStyle;


    /**
     * Constructor without ID.
     */
    public Bookshelf(@NonNull final String name,
                     final long styleId) {
        mName = name.trim();
        mStyleId = styleId;
    }

    /**
     * Full Constructor.
     */
    public Bookshelf(final long id,
                     @NonNull final String name,
                     final long styleId) {
        mId = id;
        mName = name.trim();
        mStyleId = styleId;
    }

    /**
     * Full constructor.
     *
     * @param mapper a cursor mapper.
     */
    public Bookshelf(@NonNull final ColumnMapper mapper) {
        mId = mapper.getLong(DOM_PK_ID);
        mName = mapper.getString(DOM_BOOKSHELF);
        mStyleId = mapper.getLong(DOM_FK_STYLE_ID);
    }

    /** {@link Parcelable}. */
    protected Bookshelf(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        mStyleId = in.readLong();
    }

    /**
     * Constructor that will attempt to parse a single string into a Bookshelf.
     *
     * @param element the string to decode
     *                <p>
     *                format: "name"
     *                format: "name * styleUUID"
     */
    public static Bookshelf fromString(@NonNull final String element) {
        List<String> list = new StringList<String>()
                .decode(FIELD_SEPARATOR, element, false);

        String name = list.get(0);
        // check if we have a style
        if (list.size() > 1) {
            String uuid = list.get(1).trim();
            if (uuid.startsWith("-")) {
                // it's a builtin style, use it.
                try {
                    long builtinId = Long.parseLong(uuid);
                    return new Bookshelf(name, builtinId);
                } catch (NumberFormatException ignore) {
                }
            } else {
                // it's a user defined style. TOMF: ENHANCE... implement later....
                //problem: importing an archive where the bookshelf data comes BEFORE the styles
                // see if we can find the uuid in the db
                // if found, we have the id:
                //..... return new Bookshelf(name, styleId);
                // if not found?
            }
        }
        // the right thing todo would be: get a database, then get the 'real' default style.
        // as this is a lot of overkill for importing, we're just using the builtin default.
        return new Bookshelf(name, BooklistStyles.DEFAULT_STYLE);
    }

    /**
     * Special Formatter.
     *
     * @return the list of bookshelves formatted as "shelf1, shelf2, shelf3, ...
     */
    @NonNull
    public static String toDisplayString(@NonNull final List<Bookshelf> list) {
        return Csv.toDisplayString(list, new Csv.Formatter<Bookshelf>() {
            @Override
            public String format(@NonNull final Bookshelf element) {
                return element.getName();
            }
        });
    }

    /**
     * Get the users preferred/current bookshelf with fallback to initial/all-books as needed.
     *
     * @return the preferred bookshelf.
     */
    public static Bookshelf getPreferred(@NonNull final DBA db) {
        String bookshelfName = Prefs.getPrefs().getString(PREF_BOOKSHELF_CURRENT, null);
        if (bookshelfName != null && !bookshelfName.isEmpty()) {
            // try to get the preferred shelf
            Bookshelf bookshelf = db.getBookshelfByName(bookshelfName);
            if (bookshelf != null) {
                return bookshelf;
            }
            // shelf must have been deleted, switch to 'all book'
            return getAllBooksBookshelf(db);

        } else {
            // no current shelf, start with initial shelf
            return getDefaultBookshelf(db);
        }
    }

    /**
     * Get the builtin default bookshelf (with the current/default style).
     *
     * @param db the database
     *
     * @return shelf
     */
    public static Bookshelf getDefaultBookshelf(@NonNull final DBA db) {
        return new Bookshelf(DEFAULT_ID,
                             db.getContext().getString(R.string.initial_bookshelf),
                             BooklistStyles.getDefaultStyle(db).getId());
    }

    /**
     * Get the virtual 'all books' bookshelf (with the current/default style).
     *
     * @param db the database
     *
     * @return shelf
     */
    public static Bookshelf getAllBooksBookshelf(@NonNull final DBA db) {
        return new Bookshelf(ALL_BOOKS,
                             db.getContext().getString(R.string.all_books),
                             BooklistStyles.getDefaultStyle(db).getId());
    }

    /**
     * Set this bookshelf as the current/preferred.
     */
    public void setAsPreferred() {
        Prefs.getPrefs().edit().putString(PREF_BOOKSHELF_CURRENT, mName).apply();
    }

    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull final String name) {
        mName = name;
    }


    /**
     * Set the style for this bookshelf. The style will also be set as the global default.
     *
     * @param db    the database used to update the bookshelf
     * @param style to set
     */
    public void setStyle(@NonNull final DBA db,
                         @NonNull final BooklistStyle style) {
        style.setDefault();

        mStyleId = style.getId();
        db.updateOrInsertBookshelf(this);
    }

    /**
     * Returns a valid style for this bookshelf.
     * If the currently set style is not valid, returns the global default or the builtin default.
     *
     * @param db the database (needed to check existence and/or to get defaults)
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public BooklistStyle getStyle(@NonNull final DBA db) {

        if (mStyleId == 0) {
            // the value 0 == undefined. User styles: 1+; system style -1..
            return BooklistStyles.getDefaultStyle(db);
        } else if (mCachedStyle == null || mStyleId != mCachedStyle.getId()) {
            // refresh the cached.
            mCachedStyle = BooklistStyles.getStyle(db, mStyleId);
        }

        return mCachedStyle;
    }

    /**
     * Replace local details from another Bookshelf.
     *
     * @param source Bookshelf to copy from
     */
    public void copyFrom(@NonNull final Bookshelf source) {
        mName = source.mName;
        mStyleId = source.mStyleId;
    }

    /** {@link Parcelable}. */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeLong(mStyleId);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }


    @Override
    @NonNull
    public String toString() {
        return "Bookshelf{"
                + "mId=" + mId
                + ", mName='" + mName + '\''
                + ", mStyleId=" + mStyleId
                + '}';
    }

    /**
     * Support for encoding to a text file.
     *TOMF: fix/finish uuid stuff: give builtin styles a uuid
     * @return the object encoded as a String.
     * <p>
     * "name * styleUUID"
     */
    @NonNull
    public String stringEncoded() {
        if (mStyleId < 0) {
            // builtin style, use the id.
            return mName + ' ' + FIELD_SEPARATOR + ' ' + mStyleId;
        }

        if (mCachedStyle != null) {
            return mName + ' ' + FIELD_SEPARATOR + ' ' + mCachedStyle.getUuid();
        } else {
            // without a database, ... no style.
            return mName;
        }
    }


    @Override
    public long fixupId(@NonNull final DBA db) {
        mId = db.getBookshelfIdByName(mName);
        return mId;
    }

    /**
     * Each Bookshelf is defined exactly by a unique ID.
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Equality.
     * <p>
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     * AND all their other fields are equal
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Bookshelf that = (Bookshelf) obj;
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mName, that.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    public boolean isDefault() {
        return mId == DEFAULT_ID;
    }
}
