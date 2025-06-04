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

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookPagerFragment;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookPagerViewModel;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.util.logger.LoggerFactory;

public class ShowBookPagerContract
        extends ActivityResultContract<ShowBookPagerContract.Input, Optional<EditBookOutput>> {

    private static final String TAG = "ShowBookContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        final Intent intent = FragmentHostActivityLauncher
                .createIntent(context, ShowBookPagerFragment.class, R.layout.activity_book_details)
                // book-details page
                .putExtra(DBKey.FK_BOOKSHELF, input.bookshelf)
                // Pager
                .putExtra(DBKey.FK_BOOK, input.bookId)
                .putExtra(ShowBookPagerViewModel.BKEY_NAV_POSITION, input.position);

        if (input.navTableName != null) {
            // Pager
            intent.putExtra(ShowBookPagerViewModel.BKEY_NAV_TABLE_NAME, input.navTableName);
        }
        if (input.bookIdList != null && !input.bookIdList.isEmpty()) {
            // Pager
            intent.putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(input.bookIdList));
        }
        return intent;
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
        final Bookshelf bookshelf;
        final int position;

        @Nullable
        final String navTableName;
        @Nullable
        final List<Long> bookIdList;

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
            this.bookId = bookId;
            this.bookshelf = bookshelf;
            this.position = 0;
            this.navTableName = null;
            this.bookIdList = null;
        }

        /**
         * Constructor.
         *
         * @param bookId     Initial book id to show.
         *                   Used by the pager.
         * @param bookshelf  current Bookshelf displayed by the BoB
         *                   Used by the book-details.
         * @param position   The position of the given book.
         *                   Keep in mind a book can occur multiple times,
         *                   so we need to pass the specific position.
         *                   Ignored if navTableName is {@code null}.
         *                   Used by the pager.
         * @param bookIdList The list of book ids to display.
         *                   Used by the pager.
         */
        public Input(@IntRange(from = 1) final long bookId,
                     @NonNull final Bookshelf bookshelf,
                     @IntRange(from = 0) final int position,
                     @NonNull final List<Long> bookIdList) {
            this.bookId = bookId;
            this.bookshelf = bookshelf;
            this.position = position;
            this.bookIdList = bookIdList;
            this.navTableName = null;
        }

        /**
         * Constructor.
         *
         * @param bookId       Initial book id to show.
         *                     Used by the pager.
         * @param bookshelf    current Bookshelf displayed by the BoB
         *                     Used by the book-details.
         * @param position     The position of the given book.
         *                     Keep in mind a book can occur multiple times,
         *                     so we need to pass the specific position.
         *                     Used by the pager.
         * @param navTableName The name of the current list-navigation table.
         *                     Used by the pager.
         */
        public Input(@IntRange(from = 1) final long bookId,
                     @NonNull final Bookshelf bookshelf,
                     @IntRange(from = 0) final int position,
                     @NonNull final String navTableName) {
            this.bookId = bookId;
            this.bookshelf = bookshelf;
            this.position = position;
            this.navTableName = navTableName;
            this.bookIdList = null;
        }
    }
}
