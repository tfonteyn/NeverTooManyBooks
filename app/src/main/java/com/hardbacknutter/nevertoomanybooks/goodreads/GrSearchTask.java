/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.SearchBooksApiHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;

public class GrSearchTask
        extends VMTask<List<GoodreadsWork>> {

    private static final String TAG = "GrSearchTask";

    private final MutableLiveData<Boolean> mBookNoLongerExists = new MutableLiveData<>();

    /** Database Access. */
    private DAO mDb;
    /** Data from the 'incoming' book. */
    private long mBookId;
    private String mIsbnText;
    private String mAuthorText;
    private String mTitleText;
    private String mSearchText;

    /** Observable. */
    @NonNull
    MutableLiveData<Boolean> onBookNoLongerExists() {
        return mBookNoLongerExists;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args,
                     @Nullable final Bundle savedInstanceState) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
            if (mBookId > 0) {
                try (Cursor cursor = mDb.fetchBookColumnsForGoodreadsSearch(mBookId)) {
                    if (cursor.moveToFirst()) {
                        final DataHolder bookData = new CursorRow(cursor);
                        mAuthorText = bookData
                                .getString(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
                        mTitleText = bookData.getString(DBDefinitions.KEY_TITLE);
                        mIsbnText = bookData.getString(DBDefinitions.KEY_ISBN);
                    } else {
                        mBookNoLongerExists.setValue(true);
                    }
                }
                mSearchText = mAuthorText + ' ' + mTitleText + ' ' + mIsbnText + ' ';
            }
        }
        final Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;
        mSearchText = currentArgs
                .getString(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS,
                           mSearchText);
    }

    public long getBookId() {
        return mBookId;
    }

    @Nullable
    String getIsbnText() {
        return mIsbnText;
    }

    @Nullable
    String getAuthorText() {
        return mAuthorText;
    }

    @Nullable
    String getTitleText() {
        return mTitleText;
    }

    @Nullable
    String getSearchText() {
        return mSearchText;
    }

    void setSearchText(@Nullable final String searchText) {
        mSearchText = searchText;
    }

    public void search(@NonNull final String searchText) {
        mSearchText = searchText;
        if (!mSearchText.isEmpty()) {
            execute(R.id.TASK_ID_GR_GET_WORKS);
        }
    }

    @Override
    @NonNull
    protected List<GoodreadsWork> doWork()
            throws Http404Exception, CredentialsException, IOException {
        Thread.currentThread().setName(TAG);
        final Context context = App.getTaskContext();

        final GoodreadsAuth grAuth = new GoodreadsAuth(context);
        final SearchBooksApiHandler searcher = new SearchBooksApiHandler(context, grAuth);
        return searcher.search(mSearchText);
    }
}
