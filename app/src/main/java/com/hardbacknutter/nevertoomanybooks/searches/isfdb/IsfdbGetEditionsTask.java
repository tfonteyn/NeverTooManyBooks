/*
 * @Copyright 2019 HardBackNutter
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

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class IsfdbGetEditionsTask
        extends AsyncTask<Void, Void, ArrayList<IsfdbEditionsHandler.Edition>> {

    @NonNull
    private final String mIsbn;
    @NonNull
    private final WeakReference<IsfdbResultsListener> mTaskListener;

    /**
     * Constructor.
     *
     * @param isbn         to search for
     * @param taskListener to send results to
     */
    @UiThread
    public IsfdbGetEditionsTask(@NonNull final String isbn,
                                @NonNull final IsfdbResultsListener taskListener) {
        mIsbn = isbn;
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    @WorkerThread
    protected ArrayList<IsfdbEditionsHandler.Edition> doInBackground(final Void... params) {
        Thread.currentThread().setName("IsfdbGetEditionsTask " + mIsbn);
        try {
            return new IsfdbEditionsHandler().fetch(mIsbn);
        } catch (@NonNull final SocketTimeoutException e) {
            Logger.warn(this, "doInBackground", e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final ArrayList<IsfdbEditionsHandler.Edition> result) {
        // always send result, even if empty
        if (mTaskListener.get() != null) {
            mTaskListener.get().onGotIsfdbEditions(result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }
}
