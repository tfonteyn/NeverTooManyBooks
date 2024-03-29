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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

public final class FilterFactory {

    // Not always the same mapping as {@link MapDBKey}
    public static final Map<String, Integer> SUPPORTED = Map.of(
            DBKey.BOOK_ISBN, R.string.lbl_isbn,
            DBKey.COLOR, R.string.lbl_color,
            DBKey.EDITION__BITMASK, R.string.lbl_edition,
            DBKey.FK_BOOKSHELF, R.string.lbl_bookshelf,
            //FIXME: the key name is a mistake but makes no difference
            // in functionality.
            // It should have been BOOK_CONTENT_TYPE
            // fix this during an upgrade where we can update
            // the TBL_BOOKSHELF_FILTERS#filter_name column.
            DBKey.FK_TOC_ENTRY, R.string.lbl_book_type,
            DBKey.FORMAT, R.string.lbl_format,
            DBKey.LANGUAGE, R.string.lbl_language,
            // Different from MapDBKey. Here it MUST be "lbl_lend_out"
            DBKey.LOANEE_NAME, R.string.lbl_lend_out,
            DBKey.READ__BOOL, R.string.lbl_read,
            DBKey.SIGNED__BOOL, R.string.lbl_signed
    );

    private FilterFactory() {
    }

    @Nullable
    public static PFilter<?> createFilter(@NonNull final String dbKey) {
        switch (dbKey) {
            case DBKey.READ__BOOL: {
                return new ReadStatusFilter();
            }
            case DBKey.SIGNED__BOOL: {
                return new PBooleanFilter(
                        dbKey, R.string.lbl_signed, R.array.pe_bob_filter_signed,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_SIGNED);
            }


            // Does the book have an ISBN (or any other code) or none.
            case DBKey.BOOK_ISBN: {
                return new PHasValueFilter(
                        dbKey, R.string.lbl_isbn, R.array.pe_bob_filter_isbn,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_ISBN);
            }
            // Is the book lend out or not.
            case DBKey.LOANEE_NAME: {
                return new PHasValueFilter(
                        dbKey, R.string.lbl_lend_out, R.array.pe_bob_filter_lending,
                        TBL_BOOK_LOANEE, DBDefinitions.DOM_LOANEE);
            }


            case DBKey.COLOR: {
                return new PStringEqualityFilter(
                        dbKey, R.string.lbl_color,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_COLOR);
            }
            case DBKey.FORMAT: {
                return new PStringEqualityFilter(
                        dbKey, R.string.lbl_format,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_FORMAT);
            }
            case DBKey.LANGUAGE: {
                final PStringEqualityFilter filter = new PStringEqualityFilter(
                        dbKey, R.string.lbl_language,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_LANGUAGE);

                filter.setFormatter(context -> new LanguageFormatter(
                        context.getResources().getConfiguration().getLocales().get(0),
                        ServiceLocator.getInstance().getLanguages()));
                return filter;
            }


            case DBKey.EDITION__BITMASK: {
                return new PBitmaskFilter(
                        dbKey, R.string.lbl_edition,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_EDITION,
                        Book.Edition::getAll);
            }


            case DBKey.FK_BOOKSHELF: {
                return new PEntityListFilter<>(
                        dbKey, R.string.lbl_bookshelves,
                        TBL_BOOK_BOOKSHELF, DBDefinitions.DOM_FK_BOOKSHELF,
                        () -> ServiceLocator.getInstance().getBookshelfDao().getAll());
            }

            case DBKey.FK_TOC_ENTRY: {
                // FIXME: see note with SUPPORTED above
                return new PEntityListFilter<>(
                        dbKey, R.string.lbl_book_type,
                        TBL_BOOKS, DBDefinitions.DOM_BOOK_CONTENT_TYPE,
                        Book.ContentType::getAll);
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
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        switch (dbKey) {
            case DBKey.COLOR: {
                return FieldArrayAdapter.createStringDropDown(
                        context, serviceLocator.getColorDao().getList(), null);
            }
            case DBKey.FORMAT: {
                return FieldArrayAdapter.createStringDropDown(
                        context, serviceLocator.getFormatDao().getList(), null);
            }
            case DBKey.LANGUAGE: {
                final Locale userLocale = context.getResources().getConfiguration()
                                                 .getLocales().get(0);
                return FieldArrayAdapter.createStringDropDown(
                        context,
                        // list of ISO codes as we need these for storage
                        serviceLocator.getLanguageDao().getList(),
                        new LanguageFormatter(userLocale, serviceLocator.getLanguages()));
            }
            case DBKey.FK_TOC_ENTRY: {
                // FIXME: see note with SUPPORTED above
                return FieldArrayAdapter.createEntityDropDown(
                        context, Book.ContentType.getAll());
            }

            default:
                return null;
        }
    }
}
