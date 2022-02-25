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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminViewModel;

public class SearchSitesSingleListContract
        extends ActivityResultContract<ArrayList<Site>, ArrayList<Site>> {

    /** Log tag. */
    private static final String TAG = "SearchSitesSingleList";
    /** The key (list type) to retrieve the result. */
    private String mListKey;

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final ArrayList<Site> list) {

        // All sites in a list are always of the same type; just grab it from the first entry
        mListKey = list.get(0).getType().getBundleKey();

        return FragmentHostActivity
                .createIntent(context, R.layout.activity_admin_search, SearchAdminFragment.class)
                .putParcelableArrayListExtra(SearchAdminViewModel.BKEY_LIST, list);
    }

    @Nullable
    @Override
    public ArrayList<Site> parseResult(final int resultCode,
                                       @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent
                                         + "|mListKey=" + mListKey);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        return intent.getParcelableArrayListExtra(mListKey);
    }
}
