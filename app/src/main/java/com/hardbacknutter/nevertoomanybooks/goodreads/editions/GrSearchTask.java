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
package com.hardbacknutter.nevertoomanybooks.goodreads.editions;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Used by {@link GoodreadsSearchActivity} only. Limited testing.
 */
public class GrSearchTask
        extends VMTask<List<GoodreadsWork>> {

    /** Log tag. */
    private static final String TAG = "GrSearchTask";

    private final MutableLiveData<Boolean> mBookNoLongerExists = new MutableLiveData<>();

    /** Database Access. */
    private DAO mDb;
    /** Data from the 'incoming' book. */
    private long mBookId;
    private String mIsbnText;
    private String mAuthorText;
    private String mTitleText;
    @Nullable
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
    public void init(@NonNull final Bundle args) {
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

    @NonNull
    @Override
    @WorkerThread
    protected List<GoodreadsWork> doWork(@NonNull final Context context)
            throws Http404Exception, CredentialsException, IOException {
        Thread.currentThread().setName(TAG);
        Objects.requireNonNull(mSearchText, "mSearchText");

        final GoodreadsAuth grAuth = new GoodreadsAuth(context);
        final SearchWorksApiHandler searchApi = new SearchWorksApiHandler(context, grAuth);
        return searchApi.search(mSearchText);
    }
}
