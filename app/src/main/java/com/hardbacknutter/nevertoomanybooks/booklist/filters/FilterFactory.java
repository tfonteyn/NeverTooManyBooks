/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;

public final class FilterFactory {

    /**
     * Used to build the GUI list of user options to create filters.
     * <p>
     * Dev. note: Not always the same mapping as {@link MapDBKey}
     */
    @SuppressWarnings("NonFinalStaticVariableUsedInClassInitialization")
    private static final Map<String, Function<Context, String>> SUPPORTED = Map.ofEntries(
            Map.entry(DBKey.FK_BOOKSHELF, context -> context.getString(R.string.lbl_bookshelf)),
            Map.entry(DBKey.FK_IDENTIFIER, context -> context.getString(R.string.lbl_identifier)),
            Map.entry(DBKey.FK_TAG, context -> context.getString(R.string.lbl_tag)),
            Map.entry(DBKey.FK_TOC_ENTRY, context -> context.getString(R.string.lbl_book_type)),

            Map.entry(DBKey.AUTHOR.FAMILY_NAME, context -> context.getString(
                    R.string.a_bracket_b_bracket,
                    context.getString(R.string.lbl_author),
                    context.getString(R.string.lbl_family_name))),
            Map.entry(DBKey.AUTHOR.GIVEN_NAMES, context -> context.getString(
                    R.string.a_bracket_b_bracket,
                    context.getString(R.string.lbl_author),
                    context.getString(R.string.lbl_given_names))),

            Map.entry(DBKey.COLOR, context -> context.getString(R.string.lbl_color)),
            Map.entry(DBKey.EDITION, context -> context.getString(R.string.lbl_edition)),
            Map.entry(DBKey.FORMAT, context -> context.getString(R.string.lbl_format)),
            Map.entry(DBKey.ISBN, context -> context.getString(R.string.lbl_isbn)),
            Map.entry(DBKey.LANGUAGE, context -> context.getString(R.string.lbl_language)),
            Map.entry(DBKey.LOCATION, context -> context.getString(R.string.lbl_location)),
            Map.entry(DBKey.LOANEE_NAME, context -> context.getString(R.string.lbl_lend_out)),
            Map.entry(DBKey.READ__BOOL, context -> context.getString(R.string.lbl_read)),
            Map.entry(DBKey.SIGNED__BOOL, context -> context.getString(R.string.lbl_signed))
    );

    private FilterFactory() {
    }

    /**
     * UI usage: get a label/dbKey Map for all supported filters.
     * The map will be sorted alphabetically on the labels according to to the Locale
     * <p>
     * Key: the label, value: the DBKey
     *
     * @param context Current context
     *
     * @return sorted by localized label
     */
    @NonNull
    public static Map<String, String> getLabels(@NonNull final Context context) {
        final Map<String, String> map = new TreeMap<>();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        SUPPORTED
                .entrySet()
                .stream()
                .filter(entry -> serviceLocator.isFieldEnabled(entry.getKey()))
                .forEach(entry -> map.put(entry.getValue().apply(context), entry.getKey()));
        return map;
    }

    /**
     * UI usage: get a human readable name for the given {@code dbKey} filter.
     * <p>
     * Returns the localized text "Filter" for generic/dynamic filters.
     *
     * @param context Current context
     * @param dbKey   for the filter
     *
     * @return a human readable label/name
     */
    public static String getLabel(@NonNull final Context context,
                                  @NonNull final String dbKey) {
        final Function<Context, String> f = SUPPORTED.get(dbKey);
        if (f != null) {
            return f.apply(context);
        } else {
            return context.getString(R.string.lbl_filter);
        }
    }

    /**
     * Create a suitable {@link PFilter} for the given {@link DBKey}.
     * <p>
     * Dev. note: It's these dbKey's which get stored in
     * {@link DBDefinitions#TBL_BOOKSHELF_FILTERS} as the filter name.
     * So if we ever fix the misnamed keys, we MUST
     * update any existing filters during the update.
     *
     * @param dbKey for the filter
     *
     * @return a filter
     */
    @Nullable
    public static PFilter<?> createFilter(@NonNull final String dbKey) {
        switch (dbKey) {
            case DBKey.FK_BOOKSHELF: {
                // Reminder: the BooklistBuilder#createBookshelfFilters
                // will always add the current Bookshelf to the existing filter
                // if the user is viewing an actual Bookshelf.
                // Hence this filter should be seen as "show books from ADDITIONAL bookshelves.
                return new PEntityListFilter<>(
                        dbKey, DBDefinitions.TBL_BOOK_BOOKSHELF, DBDefinitions.DOM_FK_BOOKSHELF,
                        () -> ServiceLocator.getInstance().getBookshelfDao().getAll());
            }
            case DBKey.FK_IDENTIFIER: {
                return new PEntityListFilter<>(
                        dbKey, DBDefinitions.TBL_BOOK_IDENTIFIER, DBDefinitions.DOM_FK_IDENTIFIER,
                        () -> ServiceLocator.getInstance().getIdentifierDao().getAll());
            }
            case DBKey.FK_TAG: {
                return new PEntityListFilter<>(
                        dbKey, DBDefinitions.TBL_BOOK_TAG, DBDefinitions.DOM_FK_TAG,
                        () -> ServiceLocator.getInstance().getTagDao().getAll());
            }
            case DBKey.FK_TOC_ENTRY: {
                // FIXME: the dbKey name is a mistake, but makes no difference in functionality.
                //  It should have been DBKey.BOOK_CONTENT_TYPE
                return new PEntityListFilter<>(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_CONTENT_TYPE,
                        Book.ContentType::getAll);
            }

            case DBKey.AUTHOR.FAMILY_NAME: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_AUTHORS, DBDefinitions.DOM_AUTHOR_FAMILY_NAME,
                        // full join, books always have authors
                        // Added here for future compatibility.
                        // When used in the BoB, we already link with the authors.
                        new Pair<>(DBDefinitions.TBL_BOOK_AUTHOR.getName(),
                                   DBDefinitions.TBL_BOOKS.startJoin(DBDefinitions.TBL_BOOK_AUTHOR,
                                                                     DBDefinitions.TBL_AUTHORS)))
                        .setWildcards(true);
            }
            case DBKey.AUTHOR.GIVEN_NAMES: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_AUTHORS, DBDefinitions.DOM_AUTHOR_GIVEN_NAMES,
                        // full join, books always have authors
                        // Added here for future compatibility.
                        // When used in the BoB, we already link with the authors.
                        new Pair<>(DBDefinitions.TBL_BOOK_AUTHOR.getName(),
                                   DBDefinitions.TBL_BOOKS.startJoin(DBDefinitions.TBL_BOOK_AUTHOR,
                                                                     DBDefinitions.TBL_AUTHORS)))
                        .setWildcards(true);
            }
            case DBKey.COLOR: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_COLOR);
            }
            case DBKey.EDITION: {
                return new PBitmaskFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_EDITION,
                        Book.Edition::getAll);
            }
            case DBKey.FORMAT: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_FORMAT);
            }
            case DBKey.ISBN: {
                // Does the book have an ISBN (or any other code) or none.
                return new PHasValueFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_ISBN,
                        R.array.lbl_bob_filter_isbn);
            }
            case DBKey.LANGUAGE: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_LANGUAGE)
                        .setFormatter(context -> new LanguageFormatter(
                                context.getResources().getConfiguration().getLocales().get(0),
                                ServiceLocator.getInstance().getLanguages()));
            }
            case DBKey.LOCATION: {
                return new PStringEqualityFilter(
                        dbKey, DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_LOCATION);
            }
            case DBKey.LOANEE_NAME: {
                // Is the book lend out or not.
                // FIXME: The dbKey name is a mistake, but makes no difference in functionality.
                //  This is not filtering on the name but on the book being lend-out or not.
                return new PHasValueFilter(
                        dbKey, DBDefinitions.TBL_BOOK_LOANEE, DBDefinitions.DOM_LOANEE,
                        R.array.lbl_bob_filter_lending);
            }
            case DBKey.READ__BOOL: {
                return new ReadStatusFilter();
            }
            case DBKey.SIGNED__BOOL: {
                return new PBooleanFilter(
                        dbKey,
                        DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_SIGNED,
                        R.array.lbl_bob_filter_signed);
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
            case DBKey.LOCATION: {
                return FieldArrayAdapter.createStringDropDown(
                        context, serviceLocator.getLocationDao().getList(), null);
            }

            case DBKey.FK_TOC_ENTRY: {
                // TODO: see note with SUPPORTED above
                return FieldArrayAdapter.createEntityDropDown(
                        context, Book.ContentType.getAll());
            }

            default:
                return null;
        }
    }
}
