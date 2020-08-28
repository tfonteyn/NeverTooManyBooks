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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * This task is bypassing {@link SearchEngine.AlternativeEditions}
 * as in this particular circumstance it's faster.
 */
public class IsfdbGetEditionsTask
        extends VMTask<List<Edition>> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetEditionsTask";

    /** The isbn we're looking up. */
    private String mIsbn;

    @UiThread
    public void search(@NonNull final ISBN isbn) {
        mIsbn = isbn.asText();
        execute(R.id.TASK_ID_SEARCH_EDITIONS);
    }

    @Nullable
    @Override
    protected List<Edition> doWork()
            throws IOException {
        Thread.currentThread().setName(TAG + mIsbn);
        final Context context = AppLocale.getInstance().apply(App.getTaskContext());

        final IsfdbSearchEngine searchEngine = new IsfdbSearchEngine(context);
        searchEngine.setCaller(this);

        return searchEngine.fetchEditionsByIsbn(mIsbn);
    }
}
