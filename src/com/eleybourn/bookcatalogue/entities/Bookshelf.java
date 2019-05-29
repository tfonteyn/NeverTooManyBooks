package com.eleybourn.bookcatalogue.entities;

import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_UUID;

/**
 * Represents a Bookshelf.
 */
public class Bookshelf
        implements Parcelable, Utils.ItemWithIdFixup, Entity {

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
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT_ID = 1;
    /** String encoding use. */
    private static final char FIELD_SEPARATOR = '*';
    /** the virtual 'All Books'. */
    private static final int ALL_BOOKS = -1;
    private long mId;
    @NonNull
    private String mName;

    /**
     * the style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle(DAO)}}
     */
    @NonNull
    private String mStyleUuid;

    /** the style gets cached. It only gets reloaded when the mStyleUuid != cached one. */
    private BooklistStyle mCachedStyle;

    /**
     * Constructor without ID.
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final String styleUuid) {
        mName = name.trim();
        mStyleUuid = styleUuid;
//        mCachedStyle = null;
    }

    /**
     * Constructor without ID.
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final BooklistStyle style) {
        mName = name.trim();

        mStyleUuid = style.getUuid();
        mCachedStyle = style;
    }

    /**
     * Full Constructor.
     */
    public Bookshelf(final long id,
                     @NonNull final String name,
                     @NonNull final BooklistStyle style) {
        mId = id;
        mName = name.trim();

        mStyleUuid = style.getUuid();
        mCachedStyle = style;
    }

    /**
     * Full constructor.
     *
     * @param mapper a cursor mapper.
     */
    public Bookshelf(final long id,
                     @NonNull final ColumnMapper mapper) {
        mId = id;
        mName = mapper.getString(DOM_BOOKSHELF);
        mStyleUuid = mapper.getString(DOM_UUID);
//        mCachedStyle = null;
    }

    /** {@link Parcelable}. */
    protected Bookshelf(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        //noinspection ConstantConditions
        mStyleUuid = in.readString();
//        mCachedStyle = null;
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
            // it's quite possible that the UUID is not a style we (currently) know.
            // but right now that does not matter as we'll check it when we actually access it.
            return new Bookshelf(name, uuid);

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
        return Csv.join(", ", list, Bookshelf::getName);
    }

    /**
     * Get the users preferred/current bookshelf with fallback to initial/all-books as needed.
     *
     * @return the preferred bookshelf.
     */
    public static Bookshelf getPreferred(@NonNull final Resources resources,
                                         @NonNull final DAO db) {
        String bookshelfName = App.getPrefs().getString(PREF_BOOKSHELF_CURRENT, null);
        if (bookshelfName != null && !bookshelfName.isEmpty()) {
            // try to get the preferred shelf
            Bookshelf bookshelf = db.getBookshelfByName(bookshelfName);
            if (bookshelf != null) {
                return bookshelf;
            }
            // shelf must have been deleted, switch to 'all book'
            return getAllBooksBookshelf(resources, db);

        } else {
            // no current shelf, start with initial shelf
            return getDefaultBookshelf(resources, db);
        }
    }

    /**
     * Get the builtin default bookshelf (with the current/default style).
     *
     * @param db the database
     *
     * @return shelf
     */
    public static Bookshelf getDefaultBookshelf(@NonNull final Resources resources,
                                                @NonNull final DAO db) {
        return new Bookshelf(DEFAULT_ID,
                             resources.getString(R.string.bookshelf_my_books),
                             BooklistStyles.getDefaultStyle(db));
    }

    /**
     * Get the virtual 'all books' bookshelf (with the current/default style).
     *
     * @param db the database
     *
     * @return shelf
     */
    public static Bookshelf getAllBooksBookshelf(@NonNull final Resources resources,
                                                 @NonNull final DAO db) {
        return new Bookshelf(ALL_BOOKS,
                             resources.getString(R.string.bookshelf_all_books),
                             BooklistStyles.getDefaultStyle(db));
    }

    /**
     * Set this bookshelf as the current/preferred.
     */
    public void setAsPreferred() {
        App.getPrefs().edit().putString(PREF_BOOKSHELF_CURRENT, mName).apply();
    }

    @Override
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

    @Override
    public String getLabel() {
        return mName;
    }

    /**
     * Set the style for this bookshelf. The style will also be set as the global default.
     *
     * @param db    the database used to update the bookshelf
     * @param style to set
     */
    public void setStyle(@NonNull final DAO db,
                         @NonNull final BooklistStyle style) {
        style.setDefault();

        mStyleUuid = style.getUuid();
        db.updateOrInsertBookshelf(this);
        mCachedStyle = style;
    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param db the database (needed to check existence and/or to get defaults)
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public BooklistStyle getStyle(@NonNull final DAO db) {

        if (mCachedStyle == null || !mStyleUuid.equals(mCachedStyle.getUuid())) {
            // refresh
            mCachedStyle = BooklistStyles.getStyle(db, mStyleUuid);
            // the previous uuid might have been overruled.
            mStyleUuid = mCachedStyle.getUuid();
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
        mStyleUuid = source.mStyleUuid;
        mCachedStyle = null;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeString(mStyleUuid);
    }

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
                + ", mName=`" + mName + '`'
                + ", mStyleUuid=" + mStyleUuid
                + ", mCachedStyle=" + (mCachedStyle == null ? "null" : mCachedStyle.getUuid())
                + '}';
    }

    /**
     * Support for encoding to a text file.
     *
     * @return the object encoded as a String.
     * <p>
     * "name * styleUUID"
     */
    @NonNull
    public String stringEncoded() {
        //TOMF: the uuid is not validated. Does not matter for the CSV export, but will subsequently fail on re-import.
        return mName + ' ' + FIELD_SEPARATOR + ' ' + mStyleUuid;
    }

    @Override
    public long fixupId(@NonNull final DAO db) {
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
