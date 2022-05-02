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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;

public final class FilterFactory {

    public static final int[] SUPPORTED_LABELS = {R.string.lbl_read,
                                                  R.string.lbl_signed,
                                                  R.string.lbl_bookshelves,
                                                  R.string.lbl_color,
                                                  R.string.lbl_format,
                                                  R.string.lbl_language};

    public static final String[] SUPPORTED_NAMES = {DBKey.BOOL_READ,
                                                    DBKey.BOOL_SIGNED,
                                                    DBKey.FK_BOOKSHELF,
                                                    DBKey.KEY_COLOR,
                                                    DBKey.KEY_FORMAT,
                                                    DBKey.KEY_LANGUAGE};

    private FilterFactory() {
    }

    @Nullable
    public static PFilter<?> create(@NonNull final String name) {

        switch (name) {
            case DBKey.BOOL_READ:
                return new PBooleanFilter(
                        name, R.string.lbl_read, R.array.pe_bob_filter_read,
                        TBL_BOOKS, DOM_BOOK_READ);

            case DBKey.BOOL_SIGNED:
                return new PBooleanFilter(
                        name, R.string.lbl_signed, R.array.pe_bob_filter_signed,
                        TBL_BOOKS, DOM_BOOK_SIGNED);


            case DBKey.FK_BOOKSHELF:
                return new PEntityListFilter(
                        name, R.string.lbl_bookshelves,
                        TBL_BOOKSHELF, DOM_PK_ID,
                        id -> ServiceLocator.getInstance().getBookshelfDao().getById(id));


            case DBKey.KEY_COLOR:
                return new PStringEqualityFilter(
                        name, R.string.lbl_color,
                        TBL_BOOKS, DOM_BOOK_COLOR);

            case DBKey.KEY_FORMAT:
                return new PStringEqualityFilter(
                        name, R.string.lbl_format,
                        TBL_BOOKS, DOM_BOOK_FORMAT);

            case DBKey.KEY_LANGUAGE:
                return new PStringEqualityFilter(
                        name, R.string.lbl_language,
                        TBL_BOOKS, DOM_BOOK_LANGUAGE);

            default:
                return null;
        }
    }
}
