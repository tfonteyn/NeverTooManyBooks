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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Hard coded not to fetch any images.
 */
public class IsfdbGetBookTask
        extends VMTask<Bundle> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetBookTask";

    /** Native site book id to get. */
    private long mIsfdbId;
    /** Native site book edition(s) to get. */
    @Nullable
    private List<Edition> mEditions;
    /** whether the TOC should get parsed for Series information. */
    private boolean mAddSeriesFromToc;

    /**
     * Initiate a single book lookup by edition.
     *
     * @param editions         List of ISFDB native ID's
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     */
    @UiThread
    public void search(@NonNull final List<Edition> editions,
                       final boolean addSeriesFromToc) {
        mAddSeriesFromToc = addSeriesFromToc;
        mIsfdbId = 0;
        mEditions = editions;

        execute(R.id.TASK_ID_ISFDB_GET_BOOK);
    }

    /**
     * Initiate a single book lookup by ID.
     *
     * @param isfdbId          Single ISFDB native ID's
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     */
    @UiThread
    public void search(final long isfdbId,
                       final boolean addSeriesFromToc) {
        mAddSeriesFromToc = addSeriesFromToc;
        mIsfdbId = isfdbId;
        mEditions = null;

        execute(R.id.TASK_ID_ISFDB_GET_BOOK);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Bundle doWork()
            throws SocketTimeoutException, InterruptedException {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());
        final SearchEngine searchEngine = new IsfdbSearchEngine();
        searchEngine.setCaller(this);

        Thread.sleep(3_000);

        final boolean[] fetchThumbnails = {false, false};
        if (mEditions != null) {
            return new IsfdbBookHandler(searchEngine).fetch(
                    context, mEditions, mAddSeriesFromToc,
                    fetchThumbnails, new Bundle());

        } else if (mIsfdbId != 0) {
            return new IsfdbBookHandler(searchEngine).fetchByNativeId(
                    context, String.valueOf(mIsfdbId), mAddSeriesFromToc,
                    fetchThumbnails, new Bundle());

        } else {
            throw new IllegalStateException("how did we get here?");
        }
    }
}
