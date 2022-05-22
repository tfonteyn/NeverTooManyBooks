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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;

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

    public static final List<String> SUPPORTED =
            List.of(DBKey.READ__BOOL,
                    DBKey.SIGNED__BOOL,
                    DBKey.BOOK_ISBN,
                    DBKey.FK_TOC_ENTRY,
                    DBKey.LOANEE_NAME,

                    DBKey.FK_BOOKSHELF,

                    DBKey.COLOR,
                    DBKey.FORMAT,
                    DBKey.LANGUAGE,

                    DBKey.EDITION__BITMASK
                   );

    private FilterFactory() {
    }

    @NonNull
    public static Optional<PFilter<?>> createFilter(@NonNull final String dbKey) {
        switch (dbKey) {
            case DBKey.READ__BOOL:
                return Optional.of(new PBooleanFilter(
                        dbKey, R.string.lbl_read, R.array.pe_bob_filter_read,
                        TBL_BOOKS, DOM_BOOK_READ));

            case DBKey.SIGNED__BOOL:
                return Optional.of(new PBooleanFilter(
                        dbKey, R.string.lbl_signed, R.array.pe_bob_filter_signed,
                        TBL_BOOKS, DOM_BOOK_SIGNED));

            case DBKey.BOOK_ISBN:
                return Optional.of(new PBooleanFilter(
                        dbKey, R.string.lbl_isbn, R.array.pe_bob_filter_isbn,
                        TBL_BOOKS, DOM_BOOK_ISBN));

            case DBKey.FK_TOC_ENTRY:
                return Optional.of(new PBooleanFilter(
                        dbKey, R.string.lbl_anthology, R.array.pe_bob_filter_anthology,
                        TBL_BOOKS, DOM_BOOK_TOC_TYPE));

            case DBKey.LOANEE_NAME:
                return Optional.of(new PBooleanFilter(
                        dbKey, R.string.lbl_lend_out, R.array.pe_bob_filter_lending,
                        TBL_BOOK_LOANEE, DOM_LOANEE));


            case DBKey.COLOR:
                return Optional.of(new PStringEqualityFilter(
                        dbKey, R.string.lbl_color,
                        TBL_BOOKS, DOM_BOOK_COLOR));

            case DBKey.FORMAT:
                return Optional.of(new PStringEqualityFilter(
                        dbKey, R.string.lbl_format,
                        TBL_BOOKS, DOM_BOOK_FORMAT));

            case DBKey.LANGUAGE:
                return Optional.of(new PStringEqualityFilter(
                        dbKey, R.string.lbl_language,
                        TBL_BOOKS, DOM_BOOK_LANGUAGE));


            case DBKey.FK_BOOKSHELF:
                return Optional.of(new PEntityListFilter<>(
                        dbKey, R.string.lbl_bookshelves,
                        TBL_BOOK_BOOKSHELF, DOM_FK_BOOKSHELF,
                        () -> ServiceLocator.getInstance().getBookshelfDao().getAll(),
                        id -> ServiceLocator.getInstance().getBookshelfDao().getById(id)));


            case DBKey.EDITION__BITMASK:
                return Optional.of(new PBitmaskFilter(
                        dbKey, R.string.lbl_edition,
                        TBL_BOOKS, DOM_BOOK_EDITION,
                        Book.Edition::getEditions));

            default:
                return Optional.empty();
        }
    }

    /**
     * Create a list adapter for a {@link PStringEqualityFilter}.
     *
     * @param context Current context
     * @param filter  a {@link PStringEqualityFilter}.
     *
     * @return adapter
     */
    @Nullable
    public static FieldArrayAdapter createListAdapter(@NonNull final Context context,
                                                      @SuppressWarnings("TypeMayBeWeakened")
                                                      @NonNull final PStringEqualityFilter filter) {
        switch (filter.getDBKey()) {
            case DBKey.COLOR: {
                final ArrayList<String> items =
                        ServiceLocator.getInstance().getColorDao().getList();
                return new FieldArrayAdapter(context, items, null);
            }

            case DBKey.FORMAT: {
                final ArrayList<String> items =
                        ServiceLocator.getInstance().getFormatDao().getList();
                return new FieldArrayAdapter(context, items, null);
            }

            case DBKey.LANGUAGE: {
                final ArrayList<String> items =
                        ServiceLocator.getInstance().getLanguageDao().getList();
                final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
                final FieldFormatter<String> formatter = new LanguageFormatter(locale);
                return new FieldArrayAdapter(context, items, formatter);
            }

            default:
                return null;
        }
    }

}
