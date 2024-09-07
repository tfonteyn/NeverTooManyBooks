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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;

public class SearchFtsViewModel
        extends ViewModel {

    /** The maximum number of suggestions we'll show during a live search. */
    private static final int MAX_SUGGESTIONS = 20;
    private final MutableLiveData<SearchCriteria> onSearchCriteriaUpdate =
            new MutableLiveData<>();
    private final MutableLiveData<List<Long>> onBooklistUpdate = new MutableLiveData<>();
    /** Database Access. */
    private FtsDao dao;
    @Nullable
    private SearchCriteria criteria;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    public void init(@Nullable final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getFtsDao();

            if (args != null) {
                criteria = args.getParcelable(SearchCriteria.BKEY);
            }
            if (criteria == null) {
                criteria = new SearchCriteria();
            }
        }
        onSearchCriteriaUpdate.setValue(criteria);
    }

    @NonNull
    MutableLiveData<List<Long>> onBooklistUpdate() {
        return onBooklistUpdate;
    }

    @NonNull
    MutableLiveData<SearchCriteria> onSearchCriteriaUpdate() {
        return onSearchCriteriaUpdate;
    }

    /**
     * Get the current set of criteria.
     *
     * @return criteria, can be empty, but never {@code null}
     */
    @NonNull
    public SearchCriteria getCriteria() {
        return Objects.requireNonNull(criteria);
    }

    /**
     * Execute the search using the current criteria.
     * <p>
     * Results will come back using {@link #onBooklistUpdate()}.
     */
    public void search() {
        Objects.requireNonNull(criteria);
        final List<Long> list = dao.search(criteria.getFtsAuthor(),
                                           criteria.getFtsBookTitle(),
                                           criteria.getFtsSeriesTitle(),
                                           criteria.getFtsPublisher(),
                                           criteria.getFtsKeywords(),
                                           MAX_SUGGESTIONS);
        criteria.setBookIdList(list);
        onBooklistUpdate.postValue(criteria.getBookIdList());
    }
}
