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
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.util.logger.LoggerFactory;

public class ShowBookPagerContract
        extends ActivityResultContract<ShowBookPagerContract.Input, Optional<EditBookOutput>> {

    private static final String TAG = "ShowBookContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return FragmentHostActivity
                .createIntent(context, R.layout.activity_book_details, ShowBookPagerFragment.class)
                // book-details page
                .putExtra(DBKey.FK_BOOKSHELF, input.bookshelf)
                // Pager
                .putExtra(DBKey.FK_BOOK, input.bookId)
                .putExtra(ShowBookPagerViewModel.BKEY_NAV_TABLE_NAME, input.navTableName)
                .putExtra(ShowBookPagerViewModel.BKEY_LIST_TABLE_ROW_ID, input.listTableRowId);
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
        @Nullable
        final String navTableName;
        /** Ignore if navTableName is null. */
        final long listTableRowId;
        @NonNull
        private Bookshelf bookshelf;

        /**
         * Constructor.
         *
         * @param bookId    Initial book id to show.
         *                  Used by the pager.
         * @param bookshelf current Bookshelf displayed by the BoB
         *                  Used by the book-details.
         */
        public Input(@IntRange(from = 1) final long bookId,
                     @NonNull final Bookshelf bookshelf) {
            this(bookId, bookshelf, null, 0);
        }

        /**
         * Constructor.
         *
         * @param bookId         Initial book id to show.
         *                       Used by the pager.
         * @param bookshelf      current Bookshelf displayed by the BoB
         *                       Used by the book-details.
         * @param navTableName   (Optional) The name of the current list-navigation table,
         *                       which will be used by the pager to allow
         *                       the user to swipe to the next/previous book.
         *                       Used by the pager.
         * @param listTableRowId The row id in the list table of the given book.
         *                       Keep in mind a book can occur multiple times,
         *                       so we need to pass the specific one.
         *                       Ignored if navTableName is {@code null}.
         *                       Used by the pager.
         */
        public Input(@IntRange(from = 1) final long bookId,
                     @NonNull final Bookshelf bookshelf,
                     @Nullable final String navTableName,
                     @IntRange(from = 0) final long listTableRowId) {
            this.bookId = bookId;
            this.bookshelf = bookshelf;
            this.navTableName = navTableName;
            this.listTableRowId = listTableRowId;
        }
    }
}
