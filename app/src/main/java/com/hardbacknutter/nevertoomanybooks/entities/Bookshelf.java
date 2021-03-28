/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents a Bookshelf.
 *
 * <strong>Warning:</strong> the {@link ListStyle} association is LAZY.
 * i.o.w. the stored style UUID will/must always be validated before being used.
 * See {@link #getStyle(Context)}.
 */
public class Bookshelf
        implements Entity, Mergeable {

    /** Log tag. */
    public static final String TAG = "Bookshelf";

    /** {@link Parcelable}. */
    public static final Creator<Bookshelf> CREATOR = new Creator<Bookshelf>() {
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
    /** Row ID. */
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
                     @NonNull final ListStyle style) {
        mName = name.trim();
        mStyleUuid = style.getUuid();
    }

    /**
     * For testing only.
     *
     * @param name      for the Bookshelf
     * @param styleUuid the UUID of the style to apply to this shelf
     */
    @VisibleForTesting
    public Bookshelf(@NonNull final String name,
                     @NonNull final String styleUuid) {
        mName = name.trim();
        mStyleUuid = styleUuid;
    }

    /**
     * Full constructor.
     *
     * @param id      the Bookshelf id
     * @param rowData with data
     */
    public Bookshelf(final long id,
                     @NonNull final DataHolder rowData) {
        mId = id;
        mName = rowData.getString(DBKeys.KEY_BOOKSHELF_NAME);
        mStyleUuid = rowData.getString(DBKeys.KEY_STYLE_UUID);

        mTopItemPosition = rowData.getInt(DBKeys.KEY_BOOKSHELF_BL_TOP_POS);
        mTopViewOffset = rowData.getInt(DBKeys.KEY_BOOKSHELF_BL_TOP_OFFSET);
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
    }

    /**
     * Get the specified bookshelf.
     *
     * @param context    Current context
     * @param id         of bookshelf to get
     * @param fallbackId to use if the bookshelf does not exist
     *                   should be one of {@link PredefinedBookshelf}
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         final long id,
                                         @PredefinedBookshelf final long fallbackId) {

        final Bookshelf bookshelf = getBookshelf(context, id);
        if (bookshelf != null) {
            return bookshelf;
        }

        return Objects.requireNonNull(getBookshelf(context, fallbackId));
    }

    /**
     * Get the specified bookshelf.
     *
     * @param context Current context
     * @param id      of bookshelf to get
     *
     * @return the bookshelf, or {@code null} if not found
     */
    @Nullable
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         final long id) {
        if (id == ALL_BOOKS) {
            final Bookshelf bookshelf = new Bookshelf(context.getString(
                    R.string.bookshelf_all_books), StyleUtils.getDefault(context));
            bookshelf.setId(ALL_BOOKS);
            return bookshelf;

        } else if (id == DEFAULT) {
            final Bookshelf bookshelf = new Bookshelf(context.getString(
                    R.string.bookshelf_my_books), StyleUtils.getDefault(context));
            bookshelf.setId(DEFAULT);
            return bookshelf;

        } else if (id == PREFERRED) {
            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
            final String name = global.getString(PREF_BOOKSHELF_CURRENT, null);
            if (name != null && !name.isEmpty()) {
                return ServiceLocator.getInstance().getBookshelfDao().findByName(name);
            }
            return null;

        } else {
            return ServiceLocator.getInstance().getBookshelfDao().getById(id);
        }
    }

    /**
     * Passed a list of Objects, remove duplicates. We keep the first occurrence.
     *
     * @param list List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    public static boolean pruneList(@NonNull final Collection<Bookshelf> list) {
        if (list.isEmpty()) {
            return false;
        }

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
        final EntityMerger<Bookshelf> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Bookshelf current = entityMerger.next();
            bookshelfDao.fixId(current);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    /**
     * Set this bookshelf as the current/preferred.
     *
     * @param global Global preferences
     */
    public void setAsPreferred(@NonNull final SharedPreferences global) {
        global.edit().putString(PREF_BOOKSHELF_CURRENT, mName).apply();
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return mName;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull final String name) {
        mName = name;
    }

    /**
     * Set the style for this bookshelf.
     *
     * @param context Current context
     * @param style   to set; must already exist (id != 0)
     *
     * @throws SanityCheck.MissingValueException if the style is 'new' (id==0)
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final ListStyle style) {
        SanityCheck.requireNonZero(style.getId(), "style.getId()");

        mStyleUuid = style.getUuid();
        ServiceLocator.getInstance().getBookshelfDao().update(context, this);

    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param context Current context
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public ListStyle getStyle(@NonNull final Context context) {

        // Always validate first
        final ListStyle style = StyleUtils.getStyleOrDefault(context, mStyleUuid);
        // the previous uuid might have been overruled so we always refresh it
        mStyleUuid = style.getUuid();
        return style;
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     * Normally in the range 0..x, but can theoretically be {@link RecyclerView#NO_POSITION} !
     *
     * @return value for {@link LinearLayoutManager#scrollToPosition(int)}
     * or {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    @IntRange(from = RecyclerView.NO_POSITION)
    public int getFirstVisibleItemPosition() {
        return mTopItemPosition;
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     * Due to CoordinatorLayout behaviour, the returned value
     * <strong>can be negative, this is NORMAL</strong>
     *
     * @return value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    public int getTopViewOffset() {
        return mTopViewOffset;
    }

    /**
     * Store the current position of the booklist displaying this bookshelf.
     *
     * @param context       Current context
     * @param position      Value of {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * @param topViewOffset Value of {@link RecyclerView#getChildAt(int)} #getTop()
     */
    public void setFirstVisibleItemPosition(@NonNull final Context context,
                                            final int position,
                                            final int topViewOffset) {
        mTopItemPosition = position;
        mTopViewOffset = topViewOffset;

        ServiceLocator.getInstance().getBookshelfDao().update(context, this);
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     */
    public void validateStyle(@NonNull final Context context) {
        final String uuid = mStyleUuid;
        final ListStyle style = getStyle(context);
        if (!uuid.equals(style.getUuid())) {
            ServiceLocator.getInstance().getBookshelfDao().update(context, this);
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
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @return the unvalidated style uuid
     */
    @NonNull
    public String getStyleUuid() {
        return mStyleUuid;
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @param styleUuid the unvalidated style uuid
     */
    public void setStyleUuid(@NonNull final String styleUuid) {
        mStyleUuid = styleUuid;
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


    /**
     * Diacritic neutral version of {@link  #hashCode()} without id.
     *
     * @return hashcode
     */
    @Override
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(mName));
    }


    /**
     * Equality: <strong>id, name</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    /**
     * Equality.
     * <ol>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     *     AND their names are equal</li>
     * <li>Style and positions are ignored</li>
     * </ol>
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
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
               + ", mStyleUuid=" + mStyleUuid
               + '}';
    }

    @IntDef({DEFAULT, ALL_BOOKS, PREFERRED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface PredefinedBookshelf {

    }
}
