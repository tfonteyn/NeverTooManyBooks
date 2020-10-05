/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BooleanFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NotEmptyFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Encapsulate Filters and all related data/logic.
 */
public class Filters {

    /** Booklist Filter - ListPreference. */
    static final String PK_FILTER_ISBN = "style.booklist.filter.isbn";
    /** Booklist Filter - ListPreference. */
    static final String PK_FILTER_READ = "style.booklist.filter.read";
    /** Booklist Filter - ListPreference. */
    static final String PK_FILTER_SIGNED = "style.booklist.filter.signed";
    /** Booklist Filter - ListPreference. */
    static final String PK_FILTER_LEND = "style.booklist.filter.loaned";
    /** Booklist Filter - ListPreference. */
    static final String PK_FILTER_ANTHOLOGY = "style.booklist.filter.anthology";
    /** Booklist Filter - MultiSelectListPreference. */
    static final String PK_FILTER_EDITIONS = "style.booklist.filter.editions";

    /**
     * All filters in an <strong>ordered</strong> map.
     */
    private final Map<String, Filter<?>> mFilters = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param stylePrefs    the SharedPreferences for the style
     * @param isUserDefined flag
     */
    public Filters(@NonNull final SharedPreferences stylePrefs,
                   final boolean isUserDefined) {

        mFilters.put(PK_FILTER_READ,
                     new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_read,
                                       PK_FILTER_READ,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_READ));

        mFilters.put(PK_FILTER_SIGNED,
                     new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_signed,
                                       PK_FILTER_SIGNED,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_SIGNED));

        mFilters.put(PK_FILTER_ANTHOLOGY,
                     new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_anthology,
                                       PK_FILTER_ANTHOLOGY,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_TOC_BITMASK));

        mFilters.put(PK_FILTER_LEND,
                     new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_loaned,
                                       PK_FILTER_LEND,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_LOANEE));

        mFilters.put(PK_FILTER_EDITIONS,
                     new BitmaskFilter(stylePrefs, isUserDefined, R.string.lbl_edition,
                                       PK_FILTER_EDITIONS, 0, Book.Edition.BITMASK_ALL,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_EDITION_BITMASK));

        mFilters.put(PK_FILTER_ISBN,
                     new NotEmptyFilter(stylePrefs, isUserDefined, R.string.lbl_isbn,
                                        PK_FILTER_ISBN,
                                        DBDefinitions.TBL_BOOKS,
                                        DBDefinitions.KEY_ISBN));
    }

    /**
     * Get the list of <strong>active and non-active</strong> Filters.
     *
     * @return list
     */
    @NonNull
    public Collection<Filter<?>> getAll() {
        return mFilters.values();
    }

    /**
     * Get the list of <strong>active</strong> Filters.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    public Collection<Filter<?>> getActiveFilters(@NonNull final Context context) {
        return mFilters.values()
                       .stream()
                       .filter(f -> f.isActive(context))
                       .collect(Collectors.toList());
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param key   filter to set
     * @param value to use
     */
    @SuppressWarnings("SameParameterValue")
    void setFilter(@Key @NonNull final String key,
                   final boolean value) {
        //noinspection ConstantConditions
        ((BooleanFilter) mFilters.get(key)).set(value);
    }


    @NonNull
    private List<String> getLabels(@NonNull final Context context,
                                   final boolean all) {

        return mFilters.values().stream()
                       .filter(f -> f.isActive(context) || all)
                       .map(f -> f.getLabel(context))
                       .sorted()
                       .collect(Collectors.toList());
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the list of in-use filter names in a human readable format.
     *
     * @param context Current context
     * @param all     {@code true} to get all, {@code false} for only the active filters
     *
     * @return summary text
     */
    public String getSummaryText(@NonNull final Context context,
                                 final boolean all) {

        final List<String> labels = getLabels(context, all);
        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return TextUtils.join(", ", labels);
        }
    }

    /**
     * Add all filters (both active and non-active) to the given map.
     *
     * @param map to add to
     */
    void addToMap(@NonNull final Map<String, PPref> map) {
        for (Filter<?> filter : mFilters.values()) {
            map.put(filter.getKey(), (PPref) filter);
        }
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        // the collection is ordered, so we don't need the keys.
        for (Filter<?> filter : mFilters.values()) {
            filter.set(in);
        }
    }

    /**
     * Write the <strong>value</strong> to the Parcel.
     *
     * @param dest parcel to write to
     */
    public void writeToParcel(@NonNull final Parcel dest) {
        // the collection is ordered, so we don't write the keys.
        for (Filter<?> filter : mFilters.values()) {
            filter.writeToParcel(dest);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Filters{"
               + "mFilters=" + mFilters
               + '}';
    }

    @StringDef({PK_FILTER_ISBN,
                PK_FILTER_READ,
                PK_FILTER_SIGNED,
                PK_FILTER_LEND,
                PK_FILTER_ANTHOLOGY,
                PK_FILTER_EDITIONS})
    @Retention(RetentionPolicy.SOURCE)
    @interface Key {

    }
}
