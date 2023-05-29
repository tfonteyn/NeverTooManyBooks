/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.os.Parcelable;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookByExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookByIsbnFragment;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookByIsbnViewModel;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookByTextFragment;

public class AddBookBySearchContract
        extends ActivityResultContract<AddBookBySearchContract.By, Optional<EditBookOutput>> {

    private static final String TAG = "AddBookBySearchContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final By by) {
        switch (by) {
            case Isbn:
                return FragmentHostActivity
                        .createIntent(context, SearchBookByIsbnFragment.class);

            case Scan:
                return FragmentHostActivity
                        .createIntent(context, SearchBookByIsbnFragment.class)
                        .putExtra(SearchBookByIsbnViewModel.BKEY_SCAN_MODE,
                                  (Parcelable) ScanMode.Single);

            case ScanBatch:
                return FragmentHostActivity
                        .createIntent(context, SearchBookByIsbnFragment.class)
                        .putExtra(SearchBookByIsbnViewModel.BKEY_SCAN_MODE,
                                  (Parcelable) ScanMode.Batch);

            case ExternalId:
                return FragmentHostActivity
                        .createIntent(context, SearchBookByExternalIdFragment.class);

            case Text:
                return FragmentHostActivity
                        .createIntent(context, SearchBookByTextFragment.class);
        }

        throw new IllegalArgumentException(by.name());
    }

    @Override
    @NonNull
    public Optional<EditBookOutput> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                          .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        return Optional.of(EditBookOutput.parseResult(intent));
    }

    public enum By {
        Isbn,
        Scan,
        ScanBatch,
        Text,
        ExternalId
    }
}
