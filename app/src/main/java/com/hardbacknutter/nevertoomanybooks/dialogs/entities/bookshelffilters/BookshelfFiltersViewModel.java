/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelffilters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
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
    private BookshelfDao dao;

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    void init(@NonNull final Context context,
              @NonNull final BookshelfFiltersInput args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getBookshelfDao();

            bookshelf = args.getBookshelf();

            // We do a refresh, to make sure the filters are fully up-to-date.
            // The database is not modified at this point.
            // If the user edits any filter, any issues would have been resolved.
            // If the user abandons this edit, the regular DBCleaner will kick in sooner or later.
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            dao.refresh(context, bookshelf, locale);
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
        final Map<String, String> map = FilterFactory.getLabels(context);
        return new Pair<>(map.keySet().toArray(Z_ARRAY_STRING),
                          map.values().toArray(Z_ARRAY_STRING));
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @NonNull
    List<PFilter<?>> getFilterList() {
        return filterList;
    }

    /**
     * Get the modification flag used for the result when quiting the filter editor.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Set the modification flag used for the result when quiting the filter editor.
     *
     * @param modified flag
     */
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
                dao.update(context, bookshelf, locale);
            } catch (@NonNull final DaoWriteException e) {
                // log, but ignore - should never happen unless disk full
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
        return true;
    }
}
