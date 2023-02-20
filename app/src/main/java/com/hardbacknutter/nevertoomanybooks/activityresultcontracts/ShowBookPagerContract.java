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

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookPagerFragment;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookPagerViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class ShowBookPagerContract
        extends ActivityResultContract<ShowBookPagerContract.Input, Optional<EditBookOutput>> {

    private static final String TAG = "ShowBookContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return FragmentHostActivity
                .createIntent(context, R.layout.activity_book_details, ShowBookPagerFragment.class)
                .putExtra(DBKey.FK_BOOK, input.bookId)
                .putExtra(ShowBookPagerViewModel.BKEY_NAV_TABLE_NAME, input.navTableName)
                .putExtra(ShowBookPagerViewModel.BKEY_LIST_TABLE_ROW_ID, input.listTableRowId)
                .putExtra(Style.BKEY_UUID, input.styleUuid);
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

    public static class Input {

        @IntRange(from = 1)
        final long bookId;
        @NonNull
        final String styleUuid;
        @Nullable
        final String navTableName;
        /** Ignore if navTableName is null. */
        final long listTableRowId;

        /**
         * @param bookId         the book id
         * @param styleUuid      to use
         *                       Some style elements are applicable for the details screen
         * @param navTableName   The name of the current list-navigation table,
         *                       which will be used by the pager to allow
         *                       the user to swipe to the next/previous book
         * @param listTableRowId The row id in the list table of the given book.
         *                       Keep in mind a book can occur multiple times,
         *                       so we need to pass the specific one.
         */
        public Input(@IntRange(from = 1) final long bookId,
                     @NonNull final String styleUuid,
                     @Nullable final String navTableName,
                     final long listTableRowId) {
            this.bookId = bookId;
            this.styleUuid = styleUuid;
            this.navTableName = navTableName;
            this.listTableRowId = listTableRowId;
        }
    }
}
