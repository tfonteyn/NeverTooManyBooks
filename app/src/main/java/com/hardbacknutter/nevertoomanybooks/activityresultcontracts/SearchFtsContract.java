/*
 * @Copyright 2018-2025 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.localsearch.LocalSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.localsearch.SearchFtsFragment;
import com.hardbacknutter.nevertoomanybooks.localsearch.SearchFtsInput;

public class SearchFtsContract
        extends ActivityResultContract<SearchFtsInput, Optional<LocalSearchCriteria>> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final SearchFtsInput args) {
        return FragmentHostActivityLauncher
                .createIntent(context, SearchFtsFragment.class)
                .putExtras(args.toBundle());
    }

    @Override
    @NonNull
    public Optional<LocalSearchCriteria> parseResult(final int resultCode,
                                                     @Nullable final Intent intent) {

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }
        final LocalSearchCriteria criteria = LocalSearchCriteria.fromBundle(intent.getExtras());
        return criteria != null ? Optional.of(criteria) : Optional.empty();
    }
}
