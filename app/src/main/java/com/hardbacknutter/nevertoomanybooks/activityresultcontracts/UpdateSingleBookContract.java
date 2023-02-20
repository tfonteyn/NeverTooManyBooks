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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookUpdatesFragment;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookUpdatesViewModel;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;

/**
 * Update a single Book.
 */
public class UpdateSingleBookContract
        extends ActivityResultContract<Book, Optional<UpdateBooksOutput>> {

    private static final String TAG = "Update1BookContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Book book) {

        final List<Long> bookIdList = new ArrayList<>();
        bookIdList.add(book.getId());

        final Intent intent = FragmentHostActivity
                .createIntent(context, SearchBookUpdatesFragment.class)
                .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                .putExtra(SearchBookUpdatesFragment.BKEY_SCREEN_TITLE, book.getTitle());

        final Author author = book.getPrimaryAuthor();
        if (author != null) {
            intent.putExtra(SearchBookUpdatesFragment.BKEY_SCREEN_SUBTITLE,
                            author.getLabel(context));
        }
        return intent;
    }

    @Override
    @NonNull
    public Optional<UpdateBooksOutput> parseResult(final int resultCode,
                                                   @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                          .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final long repositionToBookId =
                intent.getLongExtra(DBKey.FK_BOOK, 0);
        final long bookModified =
                intent.getLongExtra(SearchBookUpdatesViewModel.BKEY_BOOK_MODIFIED, 0);
        final long lastBookIdProcessed =
                intent.getLongExtra(SearchBookUpdatesViewModel.BKEY_LAST_BOOK_ID_PROCESSED, 0);
        final boolean listModified =
                intent.getBooleanExtra(SearchBookUpdatesViewModel.BKEY_LIST_MODIFIED, false);

        return Optional.of(new UpdateBooksOutput(repositionToBookId,
                                                 bookModified,
                                                 lastBookIdProcessed,
                                                 listModified));
    }
}
