/*
 * @Copyright 2018-2024 HardBackNutter
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

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.SearchFtsFragment;
import com.hardbacknutter.util.logger.LoggerFactory;

public class SearchFtsContract
        extends ActivityResultContract<SearchCriteria, Optional<SearchCriteria>> {

    private static final String TAG = "SearchFtsContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final SearchCriteria criteria) {
        final Intent intent = FragmentHostActivity
                .createIntent(context, SearchFtsFragment.class);
        if (criteria != null && !criteria.isEmpty()) {
            intent.putExtra(SearchCriteria.BKEY, criteria);
        }
        return intent;
    }

    @Override
    @NonNull
    public Optional<SearchCriteria> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                         .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }
        // The criteria are always present, but can be empty.
        final SearchCriteria searchCriteria = intent.getParcelableExtra(SearchCriteria.BKEY);
        if (searchCriteria != null) {
            return Optional.of(searchCriteria);
        } else {
            // Paranoia, we should never get here.
            return Optional.empty();
        }
    }
}
