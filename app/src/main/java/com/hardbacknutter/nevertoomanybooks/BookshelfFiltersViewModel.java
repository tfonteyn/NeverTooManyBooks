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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class BookshelfFiltersViewModel
        extends ViewModel {

    private static final String TAG = "BookshelfFiltersVM";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private static final String[] Z_ARRAY_STRING = new String[0];

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The Bookshelf we're editing. */
    private Bookshelf bookshelf;

    /** The list we're editing. */
    private List<PFilter<?>> filterList;

    private boolean modified;

    void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                BKEY_REQUEST_KEY);
            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            filterList = bookshelf.getFilters();
        }
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @NonNull
    List<PFilter<?>> getFilterList() {
        return filterList;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(final boolean modified) {
        this.modified = modified;
    }

    @NonNull
    Pair<String[], String[]> getFilterChoiceItems(@NonNull final Context context) {
        // key: the label, sorted locale-alphabetically; value: the DBKey
        final SortedMap<String, String> map = new TreeMap<>();

        final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                              .getGlobalFieldVisibility();
        FilterFactory.SUPPORTED
                .entrySet()
                .stream()
                .filter(entry -> fieldVisibility.isShowField(entry.getKey()))
                .forEach(entry -> map.put(context.getString(entry.getValue()), entry.getKey()));

        return new Pair<>(map.keySet().toArray(Z_ARRAY_STRING),
                          map.values().toArray(Z_ARRAY_STRING)
        );
    }

    boolean saveChanges(@NonNull final Context context) {
        if (modified) {
            bookshelf.setFilters(filterList);
            try {
                ServiceLocator.getInstance().getBookshelfDao().update(context, bookshelf);
            } catch (@NonNull final DaoWriteException e) {
                // log, but ignore - should never happen unless disk full
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
        return true;
    }
}
