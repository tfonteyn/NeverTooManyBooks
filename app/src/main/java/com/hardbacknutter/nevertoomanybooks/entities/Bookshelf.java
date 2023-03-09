/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/**
 * Represents a Bookshelf.
 * <p>
 * <strong>Warning:</strong> the {@link Style} association is LAZY.
 * i.o.w. the stored style UUID will/must always be validated before being used.
 * See {@link #getStyle(Context)}.
 */
public class Bookshelf
        implements Parcelable, Entity, Mergeable {

    /** Log tag. */
    public static final String TAG = "Bookshelf";

    /** {@link Parcelable}. */
    public static final Creator<Bookshelf> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Bookshelf createFromParcel(@NonNull final Parcel source) {
            return new Bookshelf(source);
        }

        @Override
        @NonNull
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
    private static final String PK_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    @SuppressWarnings("FieldNotUsedInToString")
    private final List<PFilter<?>> filters = new ArrayList<>();

    /** Row ID. */
    private long id;
    /** Bookshelf name. */
    @NonNull
    private String name;
    /**
     * The style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle}}
     */
    @NonNull
    private String styleUuid;

    /** The booklist adapter position of top row. */
    private int adapterPosition = RecyclerView.NO_POSITION;

    /** The booklist adapter position view offset of top row. */
    private int adapterPositionViewOffset;

    /**
     * Constructor without ID.
     *
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final Style style) {
        this.name = name.trim();
        styleUuid = style.getUuid();
    }

    /**
     * Constructor without ID.
     *
     * @param name      for the Bookshelf
     * @param styleUuid the UUID of the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final String styleUuid) {
        this.name = name.trim();
        this.styleUuid = styleUuid;
    }

    /**
     * Full constructor.
     *
     * @param id      the Bookshelf id
     * @param rowData with data
     */
    public Bookshelf(final long id,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.BOOKSHELF_NAME);
        styleUuid = rowData.getString(DBKey.STYLE_UUID);

        adapterPosition = rowData.getInt(DBKey.BOOKSHELF_BL_TOP_POS);
        adapterPositionViewOffset = rowData.getInt(DBKey.BOOKSHELF_BL_TOP_OFFSET);

        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(this.id));
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Bookshelf(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection ConstantConditions
        name = in.readString();
        //noinspection ConstantConditions
        styleUuid = in.readString();

        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(id));

        adapterPosition = in.readInt();
        adapterPositionViewOffset = in.readInt();
    }

    /**
     * Get the specified bookshelf.
     *
     * @param context Current context
     * @param id      of bookshelf to get
     *
     * @return the bookshelf
     */
    @NonNull
    public static Optional<Bookshelf> getBookshelf(@NonNull final Context context,
                                                   final long id) {
        if (id == 0) {
            return Optional.empty();
        } else if (id == ALL_BOOKS) {
            return getAllBooksBookshelf(context);
        } else if (id == DEFAULT) {
            return getDefaultBookshelf(context);
        } else if (id == PREFERRED) {
            return getPreferredBookshelf(context);
        } else {
            return ServiceLocator.getInstance().getBookshelfDao().getById(id);
        }
    }

    /**
     * Get the first of the specified bookshelf found.
     *
     * @param context Current context
     * @param ids     list of bookshelves to get in order of preference
     *
     * @return the bookshelf
     */
    @NonNull
    public static Optional<Bookshelf> getBookshelf(@NonNull final Context context,
                                                   final long... ids) {
        for (final long id : ids) {
            final Optional<Bookshelf> bookshelf = getBookshelf(context, id);
            if (bookshelf.isPresent()) {
                return bookshelf;
            }
        }
        return Optional.empty();
    }

    @NonNull
    private static Optional<Bookshelf> getAllBooksBookshelf(@NonNull final Context context) {
        final Bookshelf bookshelf = new Bookshelf(
                context.getString(R.string.bookshelf_all_books),
                ServiceLocator.getInstance().getStyles().getDefault(context));
        bookshelf.setId(ALL_BOOKS);
        return Optional.of(bookshelf);
    }

    @NonNull
    private static Optional<Bookshelf> getDefaultBookshelf(@NonNull final Context context) {
        final Bookshelf bookshelf = new Bookshelf(
                context.getString(R.string.bookshelf_my_books),
                ServiceLocator.getInstance().getStyles().getDefault(context));
        bookshelf.setId(DEFAULT);
        return Optional.of(bookshelf);
    }

    @NonNull
    private static Optional<Bookshelf> getPreferredBookshelf(@NonNull final Context context) {
        final String name = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PK_BOOKSHELF_CURRENT, null);
        if (name != null && !name.isEmpty()) {
            return ServiceLocator.getInstance().getBookshelfDao().findByName(name);
        }
        return Optional.empty();
    }

    /**
     * Set this bookshelf as the current/preferred.
     *
     * @param context Current context
     */
    public void setAsPreferred(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PK_BOOKSHELF_CURRENT, name).apply();
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @Nullable final Style style) {
        return name;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Set the style for this bookshelf.
     *
     * @param context Current context
     * @param style   to set; must already exist (id != 0)
     *
     * @throws SanityCheck.SanityException if the style is 'new' (id==0)
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final Style style) {
        if (style.getId() == 0) {
            throw new IllegalArgumentException("style.getId() == 0");
        }

        styleUuid = style.getUuid();

        try {
            ServiceLocator.getInstance().getBookshelfDao().update(context, this);
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param context Current context
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public Style getStyle(@NonNull final Context context) {

        // Always validate first
        final Style style = ServiceLocator.getInstance().getStyles()
                                          .getStyleOrDefault(context, styleUuid);
        // the previous uuid might have been overruled so we always refresh it
        styleUuid = style.getUuid();
        return style;
    }

    /**
     * Get the list of filters.
     *
     * @return a new list
     */
    @NonNull
    public List<PFilter<?>> getFilters() {
        return new ArrayList<>(filters);
    }

    public void setFilters(@Nullable final List<PFilter<?>> list) {
        filters.clear();
        if (list != null) {
            filters.addAll(list);
        }
    }

    /**
     * Prune the filters so we only keep the active ones.
     *
     * @param context Current context
     *
     * @return list of active filters
     */
    @NonNull
    public List<PFilter<?>> pruneFilters(@NonNull final Context context) {
        final List<PFilter<?>> list = filters.stream()
                                             .filter(f -> f.isActive(context))
                                             .collect(Collectors.toList());
        filters.clear();
        filters.addAll(list);
        return getFilters();
    }

    /**
     * Get the stored view offset to use for re-displaying this bookshelf's booklist.
     * Due to CoordinatorLayout behaviour, the returned value
     * <strong>can be negative, this is NORMAL</strong>.
     *
     * @return offset value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     *
     * @see #saveListPosition(Context, int, int)
     */
    public int getFirstVisibleItemViewOffset() {
        return adapterPositionViewOffset;
    }


    /**
     * Get the stored booklist adapter position to use for re-displaying
     * this bookshelf's booklist.
     * Normally in the range 0..x, but can theoretically be {@link RecyclerView#NO_POSITION} !
     *
     * @return The booklist adapter position
     *
     * @see #saveListPosition(Context, int, int)
     */
    @IntRange(from = RecyclerView.NO_POSITION)
    public int getBooklistAdapterPosition() {
        return adapterPosition;
    }

    /**
     * Save the current booklist adapter position on the current bookshelf.
     *
     * @param context    Current context
     * @param position   The booklist adapter position of the first visible view.
     * @param viewOffset Value of {@link RecyclerView#getChildAt(int)} #getTop()
     *
     * @see #getBooklistAdapterPosition()
     * @see #getFirstVisibleItemViewOffset()
     */
    public void saveListPosition(@NonNull final Context context,
                                 final int position,
                                 final int viewOffset) {
        adapterPosition = position;
        adapterPositionViewOffset = viewOffset;

        try {
            ServiceLocator.getInstance().getBookshelfDao().update(context, this);
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     */
    public void validateStyle(@NonNull final Context context) {
        final String uuid = styleUuid;
        final Style style = getStyle(context);
        if (!uuid.equals(style.getUuid())) {
            try {
                ServiceLocator.getInstance().getBookshelfDao().update(context, this);
            } catch (@NonNull final DaoWriteException e) {
                // log, but ignore - should never happen unless disk full
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
    }

    /**
     * Replace local details from another Bookshelf.
     *
     * @param source Bookshelf to copy from
     */
    public void copyFrom(@NonNull final Bookshelf source) {
        name = source.name;
        styleUuid = source.styleUuid;
        filters.clear();
        filters.addAll(source.filters);
        // don't copy the 'top' values.
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeString(styleUuid);

        dest.writeInt(adapterPosition);
        dest.writeInt(adapterPositionViewOffset);
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @return the unvalidated style uuid
     */
    @NonNull
    public String getStyleUuid() {
        return styleUuid;
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @param styleUuid the unvalidated style uuid
     */
    public void setStyleUuid(@NonNull final String styleUuid) {
        this.styleUuid = styleUuid;
    }

    /**
     * Check if this is a regular shelf, or if this one represents our complete library.
     *
     * @return {@code true} for ALL books.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAllBooks() {
        return id == ALL_BOOKS;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(name);
    }

    /**
     * Equality: <strong>id, name</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
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
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bookshelf{"
               + "id=" + id
               + ", name=`" + name + '`'
               + ", adapterPosition=" + adapterPosition
               + ", adapterPositionViewOffset=" + adapterPositionViewOffset
               + ", styleUuid=" + styleUuid
               + '}';
    }
}
