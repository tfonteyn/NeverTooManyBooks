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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.util.logger.LoggerFactory;

public class BookshelfFiltersViewModel
        extends ViewModel {

    private static final String TAG = "BookshelfFiltersVM";

    private static final String[] Z_ARRAY_STRING = new String[0];

    /** The Bookshelf we're editing. */
    private Bookshelf bookshelf;

    /** The list we're editing. */
    private List<PFilter<?>> filterList;

    private boolean modified;
    private Pair<String[], String[]> filterChoiceItems;

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (bookshelf == null) {
            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            filterList = bookshelf.getFilters();

            filterChoiceItems = createFilterChoiceItems(context);
        }
    }

    /**
     * Create the labels/dbKey lists for the supported filters.
     *
     * @param context Current context
     *
     * @return a pair with the 'first' the (sorted) labels, and the 'second' their {@link DBKey}s
     */
    @NonNull
    private Pair<String[], String[]> createFilterChoiceItems(@NonNull final Context context) {
        // key: the label, value: the DBKey
        // The map will be automatically sorted alphabetically
        // on the labels according to to the Locale
        final SortedMap<String, String> map = new TreeMap<>();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        FilterFactory.SUPPORTED
                .entrySet()
                .stream()
                .filter(entry -> serviceLocator.isFieldEnabled(entry.getKey()))
                .forEach(entry -> map.put(context.getString(entry.getValue()), entry.getKey()));

        return new Pair<>(map.keySet().toArray(Z_ARRAY_STRING),
                          map.values().toArray(Z_ARRAY_STRING)
        );
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @NonNull
    List<PFilter<?>> getFilterList() {
        return filterList;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(final boolean modified) {
        this.modified = modified;
    }

    /**
     * Get the labels/dbKey lists for the supported filters.
     *
     * @return a pair with the 'first' the (sorted) labels, and the 'second' their {@link DBKey}s
     */
    @NonNull
    Pair<String[], String[]> getFilterChoiceItems() {
        return filterChoiceItems;
    }

    boolean saveChanges(@NonNull final Context context) {
        if (modified) {
            bookshelf.setFilters(filterList);
            try {
                final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
                ServiceLocator.getInstance().getBookshelfDao().update(context, bookshelf, locale);
            } catch (@NonNull final DaoWriteException e) {
                // log, but ignore - should never happen unless disk full
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
        return true;
    }
}
