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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.LoggerFactory;

public class EditBookContract
        extends ActivityResultContract<EditBookContract.Input, Optional<EditBookOutput>> {

    private static final String TAG = "EditBookContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        final Intent intent = FragmentHostActivity
                .createIntent(context, R.layout.activity_edit_book, EditBookFragment.class)
                .putExtra(Style.BKEY_UUID, input.styleUuid);

        if (input.book != null) {
            return intent.putExtra(Book.BKEY_BOOK_DATA, input.book);
        } else {
            return intent.putExtra(DBKey.FK_BOOK, input.bookId);
        }
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
        final long bookId;
        @Nullable
        final Book book;

        @NonNull
        final String styleUuid;

        /**
         * Add/Edit a <strong>new</strong> book, typically data as retrieved after an
         * internet search, or a copy of an existing book.
         * <p>
         * This is meant for book(data) <strong>without</strong> an {@code id}.
         *
         * @param book  data
         * @param style to use
         */
        public Input(@NonNull final Book book,
                     @NonNull final Style style) {
            this.bookId = 0;
            this.book = book;
            this.styleUuid = style.getUuid();
        }

        /**
         * Edit an <strong>existing</strong> book.
         *
         * @param bookId of the book; can be {@code 0} for a new empty book.
         * @param style  to use
         */
        public Input(final long bookId,
                     @NonNull final Style style) {
            this.bookId = bookId;
            this.book = null;
            this.styleUuid = style.getUuid();
        }
    }
}
