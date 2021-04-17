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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents a Publisher.
 */
public class Publisher
        implements Entity, ItemWithTitle, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Publisher> CREATOR = new Creator<Publisher>() {
        @Override
        public Publisher createFromParcel(@NonNull final Parcel source) {
            return new Publisher(source);
        }

        @Override
        public Publisher[] newArray(final int size) {
            return new Publisher[size];
        }
    };

    /** Row ID. */
    private long mId;
    /** Publisher name. */
    @NonNull
    private String mName;

    /**
     * Constructor.
     *
     * @param name of publisher.
     */
    public Publisher(@NonNull final String name) {
        mName = name.trim();
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Publisher in the database.
     * @param rowData with data
     */
    public Publisher(final long id,
                     @NonNull final DataHolder rowData) {
        mId = id;
        mName = rowData.getString(DBKey.KEY_PUBLISHER_NAME);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Publisher(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
    }

    /**
     * Constructor.
     *
     * @param name of publisher.
     *
     * @return Publisher
     */
    public static Publisher from(@NonNull final String name) {
        return new Publisher(name);
    }

    /**
     * Passed a list of Objects, remove duplicates. We keep the first occurrence.
     *
     * @param list         List to clean up
     * @param context      Current context
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return {@code true} if the list was modified.
     */
    public static boolean pruneList(@NonNull final Collection<Publisher> list,
                                    @NonNull final Context context,
                                    final boolean lookupLocale,
                                    @NonNull final Locale bookLocale) {
        if (list.isEmpty()) {
            return false;
        }

        final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();

        final EntityMerger<Publisher> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Publisher current = entityMerger.next();
            publisherDao.fixId(context, current, lookupLocale, bookLocale);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
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
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the user visible name.
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
        return reorderTitleForDisplaying(context, getLocale(context, userLocale));
    }

    /**
     * Get the unformatted name.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Set the unformatted name; as entered manually by the user.
     *
     * @param name to use
     */
    public void setName(@NonNull final String name) {
        mName = name;
    }

    @NonNull
    @Override
    public String getTitle() {
        return mName;
    }

    /**
     * Replace local details from another publisher.
     *
     * @param source publisher to copy from
     */
    public void copyFrom(@NonNull final Publisher source) {
        mName = source.mName;
    }

    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale bookLocale) {
        return bookLocale;
    }

    @Override
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(mName));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    /**
     * Equality.
     * <ol>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     *     AND the names are equal</li>
     * <li>if both are 'new' check if family/given-names are equal</li>
     * </ol>
     *
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
        final Publisher that = (Publisher) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mName, that.mName);
    }

    @Override
    @NonNull
    public String toString() {
        return "Publisher{"
               + "mId=" + mId
               + ", mName=`" + mName + '`'
               + '}';
    }
}
