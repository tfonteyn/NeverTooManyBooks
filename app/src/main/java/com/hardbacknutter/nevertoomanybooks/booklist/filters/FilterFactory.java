/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.BookData;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_EDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

public final class FilterFactory {

    public static final Map<String, Integer> SUPPORTED = Map.of(
            DBKey.READ__BOOL, R.string.lbl_read,
            DBKey.SIGNED__BOOL, R.string.lbl_signed,

            DBKey.BOOK_ISBN, R.string.lbl_isbn,
            DBKey.LOANEE_NAME, R.string.lbl_lend_out,

            DBKey.COLOR, R.string.lbl_color,
            DBKey.FORMAT, R.string.lbl_format,
            DBKey.LANGUAGE, R.string.lbl_language,

            DBKey.EDITION__BITMASK, R.string.lbl_edition,

            DBKey.FK_BOOKSHELF, R.string.lbl_bookshelf,
            DBKey.FK_TOC_ENTRY, R.string.lbl_book_type
    );

    private FilterFactory() {
    }

    @Nullable
    public static PFilter<?> createFilter(@NonNull final String dbKey) {
        switch (dbKey) {
            case DBKey.READ__BOOL: {
                return new PBooleanFilter(
                        dbKey, R.string.lbl_read, R.array.pe_bob_filter_read,
                        TBL_BOOKS, DOM_BOOK_READ);
            }
            case DBKey.SIGNED__BOOL: {
                return new PBooleanFilter(
                        dbKey, R.string.lbl_signed, R.array.pe_bob_filter_signed,
                        TBL_BOOKS, DOM_BOOK_SIGNED);
            }


            // Does the book have an ISBN (or any other code) or none.
            case DBKey.BOOK_ISBN: {
                return new PHasValueFilter(
                        dbKey, R.string.lbl_isbn, R.array.pe_bob_filter_isbn,
                        TBL_BOOKS, DOM_BOOK_ISBN);
            }
            // Is the book lend out or not.
            case DBKey.LOANEE_NAME: {
                return new PHasValueFilter(
                        dbKey, R.string.lbl_lend_out, R.array.pe_bob_filter_lending,
                        TBL_BOOK_LOANEE, DOM_LOANEE);
            }


            case DBKey.COLOR: {
                return new PStringEqualityFilter(
                        dbKey, R.string.lbl_color,
                        TBL_BOOKS, DOM_BOOK_COLOR);
            }
            case DBKey.FORMAT: {
                return new PStringEqualityFilter(
                        dbKey, R.string.lbl_format,
                        TBL_BOOKS, DOM_BOOK_FORMAT);
            }
            case DBKey.LANGUAGE: {
                final PStringEqualityFilter filter = new PStringEqualityFilter(
                        dbKey, R.string.lbl_language,
                        TBL_BOOKS, DOM_BOOK_LANGUAGE);

                filter.setFormatter(LanguageFormatter::new);
                return filter;
            }


            case DBKey.EDITION__BITMASK: {
                return new PBitmaskFilter(
                        dbKey, R.string.lbl_edition,
                        TBL_BOOKS, DOM_BOOK_EDITION,
                        BookData.Edition::getAll);
            }


            case DBKey.FK_BOOKSHELF: {
                return new PEntityListFilter<>(
                        dbKey, R.string.lbl_bookshelves,
                        TBL_BOOK_BOOKSHELF, DOM_FK_BOOKSHELF,
                        () -> ServiceLocator.getInstance().getBookshelfDao().getAll());
            }

            case DBKey.FK_TOC_ENTRY: {
                return new PEntityListFilter<>(
                        dbKey, R.string.lbl_book_type,
                        TBL_BOOKS, DOM_BOOK_TOC_TYPE,
                        BookData.ContentType::getAll);
            }

            default:
                return null;
        }
    }

    /**
     * Create a list adapter for a string based {@link PFilter}.
     *
     * @param context Current context
     * @param dbKey   the {@link DBKey} to map
     *
     * @return adapter
     */
    @Nullable
    public static ExtArrayAdapter<String> createAdapter(@NonNull final Context context,
                                                        @NonNull final String dbKey) {
        switch (dbKey) {
            case DBKey.COLOR: {
                return FieldArrayAdapter.createStringDropDown(
                        context, ServiceLocator.getInstance().getColorDao().getList(), null);
            }
            case DBKey.FORMAT: {
                return FieldArrayAdapter.createStringDropDown(
                        context, ServiceLocator.getInstance().getFormatDao().getList(), null);
            }
            case DBKey.LANGUAGE: {
                return FieldArrayAdapter.createStringDropDown(
                        context, ServiceLocator.getInstance().getLanguageDao().getList(),
                        new LanguageFormatter(context));
            }
            case DBKey.FK_TOC_ENTRY: {
                return FieldArrayAdapter.createEntityDropDown(
                        context, BookData.ContentType.getAll());
            }

            default:
                return null;
        }
    }
}
