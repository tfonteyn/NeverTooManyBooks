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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;

public class SearchFtsViewModel
        extends ViewModel {

    /** The maximum number of suggestions we'll show during a live search. */
    private static final int MAX_SUGGESTIONS = 20;
    private final MutableLiveData<SearchCriteria> mSearchCriteriaMutableLiveData =
            new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Long>> mBooklist = new MutableLiveData<>();
    /** Database Access. */
    private FtsDao mDao;
    @Nullable
    private SearchCriteria mCriteria;

    public void init(@Nullable final Bundle args) {
        if (mDao == null) {
            mDao = ServiceLocator.getInstance().getFtsDao();

            if (args != null) {
                mCriteria = args.getParcelable(SearchCriteria.BKEY);
            }
            if (mCriteria == null) {
                mCriteria = new SearchCriteria();
            }
        }
        mSearchCriteriaMutableLiveData.setValue(mCriteria);
    }

    @NonNull
    public MutableLiveData<ArrayList<Long>> onBooklistUpdate() {
        return mBooklist;
    }

    @NonNull
    public MutableLiveData<SearchCriteria> onSearchCriteriaUpdate() {
        return mSearchCriteriaMutableLiveData;
    }

    @NonNull
    public SearchCriteria getCriteria() {
        return Objects.requireNonNull(mCriteria);
    }

    public void search() {
        //noinspection ConstantConditions
        mCriteria.search(mDao, MAX_SUGGESTIONS);
        mBooklist.postValue(mCriteria.getBookIdList());
    }
}
