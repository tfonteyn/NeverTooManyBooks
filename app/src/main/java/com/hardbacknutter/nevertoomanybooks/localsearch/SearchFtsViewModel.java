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
package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsSearchResult;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

@SuppressWarnings("WeakerAccess")
public class SearchFtsViewModel
        extends ViewModel {

    private final MutableLiveData<LocalSearchCriteria> onInitSearchCriteria =
            new MutableLiveData<>();
    private final MutableLiveData<Void> onSearchStart = new MutableLiveData<>();
    private final MutableLiveData<Void> onSearchFinished = new MutableLiveData<>();

    private final List<FtsSearchResult> searchResults = new ArrayList<>();

    private TimerDelegate timerDelegate;

    /** Database Access. */
    private FtsDao dao;
    @Nullable
    private LocalSearchCriteria criteria;
    private Bookshelf bookshelf;

    @Override
    protected void onCleared() {
        // overkill, paranoia ... the fragment.onDestroy should already have done the shutdown
        shutdownTimer();
    }

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getFtsDao();
            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            criteria = args.getParcelable(LocalSearchCriteria.BKEY);
            if (criteria == null) {
                criteria = new LocalSearchCriteria();
            }

            // The callback comes from the timer thread, hence use "post"
            timerDelegate = new TimerDelegate(() -> onSearchStart.postValue(null));
        }
        onInitSearchCriteria.setValue(criteria);
    }

    @NonNull
    MutableLiveData<LocalSearchCriteria> onInitSearchCriteria() {
        return onInitSearchCriteria;
    }

    @NonNull
    MutableLiveData<Void> onSearchStart() {
        return onSearchStart;
    }

    @NonNull
    MutableLiveData<Void> onSearchFinished() {
        return onSearchFinished;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    /**
     * Get the current set of criteria.
     *
     * @return criteria, can be empty, but never {@code null}
     */
    @NonNull
    LocalSearchCriteria getCriteria() {
        Objects.requireNonNull(criteria);

        final List<Long> ids = searchResults.stream().map(result -> result.id)
                                            .collect(Collectors.toList());
        criteria.setBookIdList(ids);
        return criteria;
    }

    @NonNull
    List<FtsSearchResult> getSearchResults() {
        return searchResults;
    }

    void userIsActive(final boolean dirty) {
        timerDelegate.userIsActive(dirty);
    }

    void shutdownTimer() {
        timerDelegate.shutdown();
    }

    /**
     * Execute the search using the current criteria.
     * <p>
     * Results will come back using {@link #onSearchFinished()}.
     */
    void search() {
        Objects.requireNonNull(criteria);
        searchResults.clear();
        if (!criteria.isEmpty()) {
            searchResults.addAll(dao.search(criteria.getFtsAuthor(),
                                            criteria.getFtsBookTitle(),
                                            criteria.getFtsSeriesTitle(),
                                            criteria.getFtsPublisher(),
                                            criteria.getFtsKeywords()));
        }
        onSearchFinished.setValue(null);
    }
}
