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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

public class IsfdbGetEditionsTask
        extends TaskBase<Void, ArrayList<Edition>> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetEditionsTask";

    /** The isbn we're looking up. */
    @NonNull
    private final String mIsbn;

    /**
     * Constructor.
     *
     * @param isbn         to search for
     * @param taskListener to send results to
     */
    @UiThread
    public IsfdbGetEditionsTask(@NonNull final String isbn,
                                @NonNull final TaskListener<ArrayList<Edition>> taskListener) {

        super(R.id.TASK_ID_SEARCH_EDITIONS, taskListener);
        mIsbn = isbn;
    }

    @Override
    @Nullable
    @WorkerThread
    protected ArrayList<Edition> doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG + mIsbn);
        final Context context = App.getTaskContext();

        try {
            return new IsfdbEditionsHandler().fetch(context, mIsbn);
        } catch (@NonNull final SocketTimeoutException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "doInBackground", e);
            }
            return null;
        }
    }
}
