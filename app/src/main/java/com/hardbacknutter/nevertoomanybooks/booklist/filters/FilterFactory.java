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

import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

public final class FilterFactory {

    public static final Map<String, Integer> SUPPORTED =
            Map.of(DBKey.READ__BOOL, R.string.lbl_read,
                   DBKey.SIGNED__BOOL, R.string.lbl_signed,
                   DBKey.ISBN, R.string.lbl_isbn,
                   DBKey.BITMASK_TOC, R.string.lbl_anthology,
                   DBKey.LOANEE_NAME, R.string.lbl_lend_out,

                   DBKey.FK_BOOKSHELF, R.string.lbl_bookshelves,

                   DBKey.COLOR, R.string.lbl_color,
                   DBKey.BOOK_FORMAT, R.string.lbl_format,
                   DBKey.LANGUAGE, R.string.lbl_language,

                   DBKey.BITMASK_EDITION, R.string.lbl_edition
                  );

    private FilterFactory() {
    }

    @NonNull
    public static Optional<PFilter<?>> create(@NonNull final String name) {

        switch (name) {
            case DBKey.READ__BOOL:
                return Optional.of(new PBooleanFilter(
                        name, R.string.lbl_read, R.array.pe_bob_filter_read,
                        TBL_BOOKS, DOM_BOOK_READ));

            case DBKey.SIGNED__BOOL:
                return Optional.of(new PBooleanFilter(
                        name, R.string.lbl_signed, R.array.pe_bob_filter_signed,
                        TBL_BOOKS, DOM_BOOK_SIGNED));

            case DBKey.ISBN:
                return Optional.of(new PBooleanFilter(
                        name, R.string.lbl_isbn, R.array.pe_bob_filter_isbn,
                        TBL_BOOKS, DOM_BOOK_ISBN));

            case DBKey.BITMASK_TOC:
                return Optional.of(new PBooleanFilter(
                        name, R.string.lbl_anthology, R.array.pe_bob_filter_anthology,
                        TBL_BOOKS, DOM_BOOK_TOC_BITMASK));

            case DBKey.LOANEE_NAME:
                return Optional.of(new PBooleanFilter(
                        name, R.string.lbl_lend_out, R.array.pe_bob_filter_lending,
                        TBL_BOOK_LOANEE, DOM_LOANEE));


            case DBKey.COLOR:
                return Optional.of(new PStringEqualityFilter(
                        name, R.string.lbl_color,
                        TBL_BOOKS, DOM_BOOK_COLOR,
                        () -> ServiceLocator.getInstance().getColorDao().getList()));

            case DBKey.BOOK_FORMAT:
                return Optional.of(new PStringEqualityFilter(
                        name, R.string.lbl_format,
                        TBL_BOOKS, DOM_BOOK_FORMAT,
                        () -> ServiceLocator.getInstance().getFormatDao().getList()));

            case DBKey.LANGUAGE:
                return Optional.of(new PStringEqualityFilter(
                        name, R.string.lbl_language,
                        TBL_BOOKS, DOM_BOOK_LANGUAGE,
                        () -> ServiceLocator.getInstance().getLanguageDao().getList()));


            case DBKey.FK_BOOKSHELF:
                return Optional.of(new PEntityListFilter<>(
                        name, R.string.lbl_bookshelves,
                        TBL_BOOK_BOOKSHELF, DOM_FK_BOOKSHELF,
                        () -> ServiceLocator.getInstance().getBookshelfDao().getAll(),
                        id -> ServiceLocator.getInstance().getBookshelfDao().getById(id)));


            case DBKey.BITMASK_EDITION:
                return Optional.of(new PBitmaskFilter(
                        name, R.string.lbl_edition,
                        TBL_BOOKS, DOM_BOOK_EDITION_BITMASK,
                        Book.Edition::getEditions));

            default:
                return Optional.empty();
        }
    }
}
