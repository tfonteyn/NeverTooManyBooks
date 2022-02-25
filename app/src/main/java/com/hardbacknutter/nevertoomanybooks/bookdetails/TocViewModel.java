/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class TocViewModel
        extends ViewModel {

    /** The list of TOC entries we're displaying. */
    @NonNull
    private final List<AuthorWork> mList = new ArrayList<>();

    @Nullable
    private String mBookTitle;

    @Nullable
    private String authors;

    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mList.isEmpty()) {
            final ArrayList<TocEntry> tocList = args.getParcelableArrayList(Book.BKEY_TOC_LIST);
            Objects.requireNonNull(tocList, Book.BKEY_TOC_LIST);
            mList.addAll(tocList);

            // optional, display purpose only
            mBookTitle = args.getString(DBKey.KEY_TITLE);
            // optional, display purpose only
            final List<Author> authorList = args.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            if (authorList != null) {
                authors = Author.getCondensedNames(context, authorList);
                if (BuildConfig.DEBUG /* always */) {
                    final long bookId = args.getLong(DBKey.FK_BOOK, 0);
                    authors = "[" + bookId + "] " + authors;
                }
            }
        }
    }

    public void reload(@NonNull final List<TocEntry> tocList) {
        mList.clear();
        mList.addAll(tocList);
    }

    @NonNull
    public List<AuthorWork> getList() {
        return mList;
    }

    @Nullable
    public String getAuthors() {
        return authors;
    }

    @Nullable
    public String getBookTitle() {
        return mBookTitle;
    }
}
