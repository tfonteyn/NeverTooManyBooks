package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.cursors.ColumnMapper;
import com.hardbacknutter.nevertomanybooks.utils.StringList;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Bookshelf.
 */
public class Bookshelf
        implements Parcelable, ItemWithFixableId, Entity {

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
    /** the virtual 'All Books'. */
    private static final int ALL_BOOKS = -1;
    /** String encoding use. */
    private static final char FIELD_SEPARATOR = '*';
    private long mId;
    @NonNull
    private String mName;
    /**
     * the style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle}}
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
        mName = mapper.getString(DBDefinitions.KEY_BOOKSHELF);
        mStyleUuid = mapper.getString(DBDefinitions.KEY_UUID);
//        mCachedStyle = null;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Bookshelf(@NonNull final Parcel in) {
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
    @NonNull
    public static Bookshelf fromString(@NonNull final String element) {
        List<String> list = new StringList<String>()
                .decode(element, false, FIELD_SEPARATOR);

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
     * Get the named bookshelf with fallback to Default/AllBooks as needed.
     *
     * @param name   of bookshelf to get
     * @param useAll set to {@code true} to return the AllBooks shelf, instead the default
     *               if the desired shelf was not found.
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         @Nullable final String name,
                                         final boolean useAll) {
        if (name != null && !name.isEmpty()) {
            Bookshelf bookshelf = db.getBookshelfByName(name);
            if (bookshelf != null) {
                return bookshelf;
            } else if (useAll) {
                // Caller wants "AllBooks" (instead of the default Bookshelf)
                return new Bookshelf(ALL_BOOKS, context.getString(R.string.bookshelf_all_books),
                        BooklistStyles.getDefaultStyle(context, db));
            }
        }

        return new Bookshelf(DEFAULT_ID, context.getString(R.string.bookshelf_my_books),
                BooklistStyles.getDefaultStyle(context, db));
    }

    /**
     * Get the preferred bookshelf with fallback to Default/AllBooks as needed.
     *
     * @param useAll set to {@code true} to return the AllBooks shelf, instead the default
     *               if the desired shelf was not found.
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         final boolean useAll) {
        String name = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_BOOKSHELF_CURRENT, null);
        return getBookshelf(context, db, name, useAll);
    }

    /**
     * Set this bookshelf as the current/preferred.
     */
    public void setAsPreferred(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(PREF_BOOKSHELF_CURRENT, mName).apply();
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
    public void setStyle(@NonNull final Context context,
                         @NonNull final DAO db,
                         @NonNull final BooklistStyle style) {
        style.setDefault(context);

        mStyleUuid = style.getUuid();
        long styleId = getStyle(context, db).getId();
        db.updateOrInsertBookshelf(this, styleId);
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
    public BooklistStyle getStyle(@NonNull final Context context,
                                  @NonNull final DAO db) {

        if (mCachedStyle == null || !mStyleUuid.equals(mCachedStyle.getUuid())) {
            // refresh
            mCachedStyle = BooklistStyles.getStyle(context, db, mStyleUuid);
            // the previous uuid might have been overruled.
            mStyleUuid = mCachedStyle.getUuid();
        }

        return mCachedStyle;
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param db the database
     */
    public void validateStyle(@NonNull final Context context,
                              @NonNull final DAO db) {
        String uuid = mStyleUuid;
        if (!uuid.equals(getStyle(context, db).getUuid())) {
            long styleId = getStyle(context, db).getId();
            db.updateBookshelf(this, styleId);
        }
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
        return mName + ' ' + FIELD_SEPARATOR + ' ' + mStyleUuid;
    }

    @Override
    public long fixId(@NonNull final DAO db) {
        mId = db.getBookshelfId(this);
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


    public boolean isDefault() {
        return mId == DEFAULT_ID;
    }

    /**
     * Equality.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND all other fields are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical id.
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
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        // one or both are 'new' or their ID's are the same.
        return Objects.equals(mName, that.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }
}
