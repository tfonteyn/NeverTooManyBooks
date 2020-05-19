/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * Represents a Bookshelf.
 */
public class Bookshelf
        implements Parcelable, ItemWithFixableId, Entity {

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
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT = 1;
    /**
     * the virtual 'All Books' representing our complete library.
     * Note we use -1, as {@code 0} is generally used for a 'new' item.
     * i.e. when the user creates a new shelf, it has id==0 before it's saved.
     */
    public static final int ALL_BOOKS = -1;
    /** The user preferred shelf as stored in preferences. */
    public static final int PREFERRED = -2;

    /**
     * Preference name - the bookshelf to load next time we startup.
     * Storing the name and not the id. If you export/import... the id will be different.
     */
    private static final String PREF_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    /** Bookshelf id. */
    private long mId;
    /** Bookshelf name. */
    @NonNull
    private String mName;
    /**
     * the style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle}}
     */
    @NonNull
    private String mStyleUuid;

    /**
     * Saved adapter row id of top row.
     * See {@link BooksOnBookshelf}#displayList}
     */
    private long mTopRowId;

    /**
     * Saved adapter position of top row.
     * See {@link BooksOnBookshelf}#displayList}
     */
    private int mTopItemPosition = RecyclerView.NO_POSITION;

    /**
     * Saved view offset of top row.
     * See {@link BooksOnBookshelf}#displayList}
     */
    private int mTopViewOffset;

    /**
     * Constructor without ID.
     *
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final BooklistStyle style) {
        mName = name.trim();

        mStyleUuid = style.getUuid();
    }

    /**
     * Full Constructor for {@link PredefinedBookshelf} instances.
     *
     * @param id    the Bookshelf id; one of {@link PredefinedBookshelf}
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    private Bookshelf(@PredefinedBookshelf final long id,
                      @NonNull final String name,
                      @NonNull final BooklistStyle style) {
        mId = id;
        mName = name.trim();

        mStyleUuid = style.getUuid();
    }

    /**
     * Full constructor.
     *
     * @param id      the Bookshelf id
     * @param rowData with data
     */
    public Bookshelf(final long id,
                     @NonNull final RowDataHolder rowData) {
        mId = id;
        mName = rowData.getString(DBDefinitions.KEY_BOOKSHELF_NAME);
        mStyleUuid = rowData.getString(DBDefinitions.KEY_UUID);

        mTopItemPosition = rowData.getInt(DBDefinitions.KEY_BOOKSHELF_BL_TOP_POS);
        mTopViewOffset = rowData.getInt(DBDefinitions.KEY_BOOKSHELF_BL_TOP_OFFSET);
        mTopRowId = rowData.getLong(DBDefinitions.KEY_BOOKSHELF_BL_TOP_ROW_ID);
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

        mTopItemPosition = in.readInt();
        mTopViewOffset = in.readInt();
        mTopRowId = in.readLong();
    }

    /**
     * Get the specified bookshelf.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param id         of bookshelf to get
     * @param fallbackId to use if the bookshelf does not exist
     *                   should be one of {@link PredefinedBookshelf}
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         final long id,
                                         @PredefinedBookshelf final long fallbackId) {

        Bookshelf bookshelf = getBookshelf(context, db, id);
        if (bookshelf != null) {
            return bookshelf;
        }

        return Objects.requireNonNull(getBookshelf(context, db, fallbackId));
    }

    /**
     * Get the specified bookshelf. If not found, returns the {@code null}.
     *
     * @param context Current context
     * @param db      Database Access
     * @param id      of bookshelf to get
     *
     * @return the bookshelf, or {@code null} if not found
     */
    @Nullable
    private static Bookshelf getBookshelf(@NonNull final Context context,
                                          @NonNull final DAO db,
                                          final long id) {
        if (id == ALL_BOOKS) {
            return new Bookshelf(ALL_BOOKS, context.getString(R.string.bookshelf_all_books),
                                 BooklistStyle.getDefault(context, db));

        } else if (id == DEFAULT) {
            return new Bookshelf(DEFAULT, context.getString(R.string.bookshelf_my_books),
                                 BooklistStyle.getDefault(context, db));

        } else if (id == PREFERRED) {
            String name = PreferenceManager.getDefaultSharedPreferences(context)
                                           .getString(PREF_BOOKSHELF_CURRENT, null);
            if (name != null && !name.isEmpty()) {
                return db.getBookshelfByName(name);
            }
            return null;

        } else {
            return db.getBookshelf(id);
        }
    }

    /**
     * Set this bookshelf as the current/preferred.
     *
     * @param context Current context
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

    @Override
    public String getLabel(@NonNull final Context context) {
        return mName;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Set the style for this bookshelf. The style will also be set as the global default.
     *
     * @param context Current context
     * @param db      Database Access
     * @param style   to set
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final DAO db,
                         @NonNull final BooklistStyle style) {

        style.setDefault(context);

        mStyleUuid = style.getUuid();
        final long styleId = getStyle(context, db).getId();
        db.updateOrInsertBookshelf(context, this, styleId);
    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public BooklistStyle getStyle(@NonNull final Context context,
                                  @NonNull final DAO db) {

        BooklistStyle style = BooklistStyle.getStyle(context, db, mStyleUuid);
        if (style == null) {
            style = BooklistStyle.getDefault(context, db);
        }
        // the previous uuid might have been overruled so we always refresh it
        mStyleUuid = style.getUuid();
        return style;
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     *
     * @return value for {@link LinearLayoutManager#scrollToPosition(int)}
     * or {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    public int getTopItemPosition() {
        return mTopItemPosition;
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     *
     * @return value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    public int getTopViewOffset() {
        return mTopViewOffset;
    }

    /**
     * The rowId is of temporary use and will likely be removed soon.
     *
     * @return the row id which was displayed on the top position.
     */
    public long getTopRowId() {
        return mTopRowId;
    }

    /**
     * Store the current position of the booklist displaying this bookshelf.
     * <p>
     * The rowId is of temporary use and will likely be removed soon.
     *
     * @param context       Current context
     * @param db            Database Access
     * @param position      Value of {@link LinearLayoutManager#findFirstVisibleItemPosition}
     * @param topViewOffset Value of {@link RecyclerView#getChildAt(int)} #getTop()
     * @param rowId         Value of {@link BooklistAdapter#getItemId(int)} for position
     */
    public void setTopListPosition(@NonNull final Context context,
                                   @NonNull final DAO db,
                                   final int position,
                                   final int topViewOffset,
                                   final long rowId) {
        mTopItemPosition = position;
        mTopViewOffset = topViewOffset;
        mTopRowId = rowId;

        // not strictly needed to refresh the style id, but we might as well take
        // the opportunity to validate it.
        final long styleId = getStyle(context, db).getId();
        db.updateOrInsertBookshelf(context, this, styleId);
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void validateStyle(@NonNull final Context context,
                              @NonNull final DAO db) {
        final String uuid = mStyleUuid;
        final BooklistStyle style = getStyle(context, db);
        if (!uuid.equals(style.getUuid())) {
            db.updateBookshelf(this, style.getId());
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
        // don't copy the 'top' values.
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeString(mStyleUuid);

        dest.writeInt(mTopItemPosition);
        dest.writeInt(mTopViewOffset);
        dest.writeLong(mTopRowId);
    }

    /**
     * Write the extra data to the JSON object.
     * Positions are not saved.
     *
     * @param data which {@link #fromJson(JSONObject)} will read
     *
     * @throws JSONException on failure
     */
    public void toJson(@NonNull final JSONObject data)
            throws JSONException {
        if (!mStyleUuid.isEmpty()) {
            data.put(DBDefinitions.KEY_FK_STYLE, mStyleUuid);
        }
    }

    /**
     * Read the extra data from the JSON object.
     *
     * @param data as written by {@link #toJson(JSONObject)}
     */
    public void fromJson(@NonNull final JSONObject data) {
        // it's quite possible that the UUID is not a style we (currently)
        // know. But that does not matter as we'll check it upon first access.
        if (data.has(DBDefinitions.KEY_FK_STYLE)) {
            mStyleUuid = data.optString(DBDefinitions.KEY_FK_STYLE);
        } else if (data.has("style")) {
            mStyleUuid = data.optString("style");
        }
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale locale) {
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

    /**
     * Check if this is a regular shelf, or if this one represents our complete library.
     *
     * @return {@code true} for ALL books.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAllBooks() {
        return mId == ALL_BOOKS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    /**
     * Equality.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND their names are equal</li>
     * <li>Style and positions are ignored</li>
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
        final Bookshelf that = (Bookshelf) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mName, that.mName);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bookshelf{"
               + "mId=" + mId
               + ", mName=`" + mName + '`'
               + ", mTopItemPosition=" + mTopItemPosition
               + ", mTopViewOffset=" + mTopViewOffset
               + ", mTopRowId=" + mTopRowId
               + ", mStyleUuid=" + mStyleUuid
               + '}';
    }

    @IntDef({DEFAULT, ALL_BOOKS, PREFERRED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface PredefinedBookshelf {

    }
}
