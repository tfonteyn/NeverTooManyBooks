/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Represents a Bookshelf.
 * <p>
 * <strong>Warning:</strong> the {@link Style} association is LAZY.
 * i.o.w. the stored style UUID will/must always be validated before being used.
 * See {@link #getStyle()}.
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

    /**
     * The 'first' bookshelf created at install time. We allow renaming it, but not deleting.
     */
    public static final int HARD_DEFAULT = 1;

    /**
     * The virtual 'All Books' representing our complete library.
     * Note we use -1, as {@code 0} is generally used for a 'new' item.
     * i.e. when the user creates a new shelf, it has id==0 before it's saved.
     */
    public static final int ALL_BOOKS = -1;

    /**
     * The user preferred shelf as stored in preferences.
     * WARNING: this can be either a normal bookshelf, ot the "All Books" virtual shelf.
     */
    public static final int USER_DEFAULT = -2;

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
    private TopRowListPosition topRowAdapterPosition = new TopRowListPosition(
            RecyclerView.NO_POSITION, 0);

    /**
     * Copy constructor.
     *
     * @param bookshelf to copy
     */
    public Bookshelf(@NonNull final Bookshelf bookshelf) {
        copyFrom(bookshelf);
    }

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

        topRowAdapterPosition = new TopRowListPosition(rowData.getInt(DBKey.BOOKSHELF_BL_TOP_POS),
                                                       rowData.getInt(
                                                               DBKey.BOOKSHELF_BL_TOP_OFFSET));

        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(this.id));
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Bookshelf(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        name = in.readString();
        //noinspection DataFlowIssue
        styleUuid = in.readString();

        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(id));

        topRowAdapterPosition = new TopRowListPosition(in.readInt(), in.readInt());
    }

    /**
     * Set this bookshelf as the current/preferred.
     *
     * @param context Current context
     */
    public void setAsPreferred(@NonNull final Context context) {
        ServiceLocator.getInstance().getBookshelfDao().setAsPreferred(context, this);
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
                           @NonNull final Style style) {
        return name;
    }

    /**
     * Get the unformatted name of this shelf.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Set the name of this shelf.
     *
     * @param name to set
     */
    public void setName(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Set the style for this bookshelf.
     *
     * @param context Current context
     * @param style   to set; must already exist (id != 0)
     *
     * @throws IllegalArgumentException if the style is 'new' (id==0)
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final Style style) {
        if (style.getId() == 0) {
            throw new IllegalArgumentException("style.getId() == 0");
        }

        styleUuid = style.getUuid();

        doUpdate(context);
    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public Style getStyle() {
        // Validate and use the default if needed.
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        final Style style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);

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

    /**
     * Set the list of filters.
     *
     * @param list to set
     */
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
     * @return a new list of active filters
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
     * Get the list position of the first visible view.
     *
     * @return position
     *
     * @see #saveTopRowPosition(Context, TopRowListPosition)
     */
    @NonNull
    public TopRowListPosition getTopRowPosition() {
        return topRowAdapterPosition;
    }

    /**
     * Save the current list position for this bookshelf.
     *
     * @param context  Current context
     * @param position The list position of the first visible view.
     *
     * @see #getTopRowPosition()
     */
    public void saveTopRowPosition(@NonNull final Context context,
                                   @NonNull final TopRowListPosition position) {
        topRowAdapterPosition = position;

        doUpdate(context);
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     */
    public void validateStyle(@NonNull final Context context) {
        final String uuid = styleUuid;
        // Resolving the style might change it (i.e. a different UUID)
        final Style style = getStyle();
        if (!uuid.equals(style.getUuid())) {
            doUpdate(context);
        }
    }

    /**
     * Update the database for this Bookshelf.
     *
     * @param context Current context
     */
    private void doUpdate(@NonNull final Context context) {
        try {
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            ServiceLocator.getInstance().getBookshelfDao().update(context, this, locale);
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, this);
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
        // NEVER copy the id and topRowAdapterPosition values.
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

        dest.writeInt(topRowAdapterPosition.getAdapterPosition());
        dest.writeInt(topRowAdapterPosition.getViewOffset());
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
               + ", adapterPosition=" + topRowAdapterPosition
               + ", styleUuid=" + styleUuid
               + '}';
    }
}
