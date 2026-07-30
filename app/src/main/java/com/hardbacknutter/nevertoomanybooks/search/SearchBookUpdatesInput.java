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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class SearchBookUpdatesInput {

    private static final String TAG = "SearchBookUpdatesInput";
    /** Optional argument to set a Toolbar title. */
    private static final String BKEY_SCREEN_TITLE = TAG + ":t";
    /** Optional argument to set a Toolbar subtitle. */
    private static final String BKEY_SCREEN_SUBTITLE = TAG + ":st";

    @NonNull
    private final List<Long> bookIdList;
    @Nullable
    private final String screenTitle;
    @Nullable
    private final String screenSubtitle;

    /**
     * Constructor.
     *
     * @param bookIdList     list of ids to process
     * @param screenTitle    optional title for the screen
     * @param screenSubtitle optional subtitle for the screen
     */
    public SearchBookUpdatesInput(@NonNull final List<Long> bookIdList,
                                  @Nullable final String screenTitle,
                                  @Nullable final String screenSubtitle) {
        this.bookIdList = bookIdList;
        this.screenTitle = screenTitle;
        this.screenSubtitle = screenSubtitle;
    }

    @NonNull
    static SearchBookUpdatesInput fromBundle(@NonNull final Bundle args) {
        final List<Long> bookIdList = Objects.requireNonNull(
                ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST));
        final String screenTitle = args.getString(SearchBookUpdatesInput.BKEY_SCREEN_TITLE);
        final String screenSubtitle = args.getString(SearchBookUpdatesInput.BKEY_SCREEN_SUBTITLE);

        return new SearchBookUpdatesInput(bookIdList, screenTitle, screenSubtitle);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putParcelable(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList));
        if (screenTitle != null) {
            args.putString(BKEY_SCREEN_TITLE, screenTitle);
        }
        if (screenSubtitle != null) {
            args.putString(BKEY_SCREEN_SUBTITLE, screenSubtitle);
        }

        return args;
    }

    @NonNull
    List<Long> getBookIdList() {
        return bookIdList;
    }

    @Nullable
    String getScreenTitle() {
        return screenTitle;
    }

    @Nullable
    String getScreenSubtitle() {
        return screenSubtitle;
    }
}
