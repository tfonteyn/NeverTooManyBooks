/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Update a single Book. The input is the Book itself.
 */
public class SearchBookUpdatesSingleBookContract
        extends ActivityResultContract<Book, Optional<EditBookOutput>> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Book book) {

        final List<Long> bookIdList = new ArrayList<>();
        bookIdList.add(book.getId());

        String subTitle = null;
        final Author author = book.getPrimaryAuthor();
        if (author != null) {
            subTitle = author.getLabel(context);
        }
        final SearchBookUpdatesInput args =
                new SearchBookUpdatesInput(bookIdList, book.getTitle(), subTitle);

        return FragmentHostActivityLauncher
                .createIntent(context, SearchBookUpdatesFragment.class)
                .putExtras(args.toBundle());
    }

    @Override
    @NonNull
    public Optional<EditBookOutput> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final Bundle result = Objects.requireNonNull(intent.getExtras());
        return Optional.of(EditBookOutput.fromBundle(result));
    }
}
