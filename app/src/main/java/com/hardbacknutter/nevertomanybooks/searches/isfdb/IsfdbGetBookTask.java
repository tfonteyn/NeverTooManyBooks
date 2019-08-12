/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.List;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

public class IsfdbGetBookTask
        extends AsyncTask<Void, Void, Bundle> {

    /** Where to send our results to. */
    @NonNull
    private final WeakReference<IsfdbResultsListener> mTaskListener;

    private final long mIsfdbId;
    @Nullable
    private final List<Editions.Edition> mEditions;

    /**
     * Constructor. Initiate a single book lookup by edition.
     *
     * @param editions     List of ISFDB native ID's
     * @param taskListener where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(@NonNull final List<Editions.Edition> editions,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = 0;
        mEditions = editions;

        mTaskListener = new WeakReference<>(taskListener);
    }

    /**
     * Constructor. Initiate a single book lookup by id.
     *
     * @param isfdbId      Single ISFDB native ID's
     * @param taskListener where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(final long isfdbId,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = isfdbId;
        mEditions = null;

        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Bundle doInBackground(final Void... params) {
        Thread.currentThread().setName("IsfdbGetBookTask");
        try {
            if (mEditions != null) {
                return new IsfdbBook().fetch(mEditions, false);

            } else if (mIsfdbId != 0) {
                return new IsfdbBook().fetch(mIsfdbId, false);

            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debugWithStackTrace(this, "doInBackground",
                                               "how did we get here?");
                }
            }

        } catch (@NonNull final SocketTimeoutException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.warn(this, "doInBackground", e.getLocalizedMessage());
            }
        }

        return null;
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Bundle result) {
        // always send result, even if empty
        if (mTaskListener.get() != null) {
            mTaskListener.get().onGotIsfdbBook(result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }
}
