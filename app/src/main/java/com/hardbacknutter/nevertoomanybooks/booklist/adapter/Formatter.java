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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.ReadStatus;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

/**
 * Format the source string according to the BooklistGroup id.
 * <p>
 * Formatting is centralized in this method; the alternative (and theoretically 'correct')
 * way would be to have a {@link RowViewHolder} for each 'case' branch
 * (and even "more" correct, for each BooklistGroup) ... which is overkill.
 * <p>
 * To keep it all straightforward, even when there is a dedicated
 * BooklistGroup (e.g. Author,Series,...),
 * we handle the formatting here regardless.
 */
class Formatter
        implements FormatFunction {

    @NonNull
    private final Context context;
    @NonNull
    private final Style style;

    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;
    @NonNull
    private final List<Locale> locales;

    Formatter(@NonNull final Context context,
              @NonNull final Style style,
              @NonNull final List<Locale> locales) {
        this.context = context;
        this.style = style;
        this.locales = locales;

        conditionDescriptions = context.getResources().getStringArray(R.array.conditions_book);
    }

    @NonNull
    @Override
    public CharSequence format(@BooklistGroup.Id final int groupId,
                               @NonNull final DataHolder rowData,
                               @NonNull final String key) {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        // NEWTHINGS: BooklistGroup
        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_author);

                } else if (serviceLocator.isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR)
                           && rowData.contains(DBKey.AUTHOR_REAL_AUTHOR)) {
                    // Specifically check for AUTHOR_REAL_AUTHOR as it will usually be 0
                    // and no lookup will be needed.
                    final long realAuthorId = rowData.getLong(DBKey.AUTHOR_REAL_AUTHOR);
                    if (realAuthorId != 0) {
                        final Optional<Author> realAuthor = serviceLocator.getAuthorDao()
                                                                          .findById(realAuthorId);
                        if (realAuthor.isPresent()) {
                            return realAuthor.get().getStyledName(context, style, text);
                        }
                    }
                }
                // already formatted by the SQL query
                return text;
            }
            case BooklistGroup.SERIES: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_series);

                } else if (style.isShowReorderedTitle()) {
                    // We don't have full Objects here for Series/Publisher so we can't use
                    // their methods for auto-reordering.
                    //
                    // FIXME: translated series are reordered in the book's language
                    // It should be done using the Series language
                    // but as long as we don't store the Series language there is no point
                    final String lang = rowData.getString(DBKey.LANGUAGE);
                    return serviceLocator.getReorderHelper().reorder(context, text, lang, locales);
                } else {
                    return text;
                }
            }
            case BooklistGroup.PUBLISHER: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_publisher);

                    // yes, we're using the 'title'. Adding specific publisher logic was
                    // to much overhead for a presumably little used feature
                } else if (style.isShowReorderedTitle()) {
                    // We don't have full Objects here for Series/Publisher so we can't use
                    // their methods for auto-reordering.
                    return serviceLocator.getReorderHelper()
                                         .reorder(context, text, (Locale) null, locales);
                } else {
                    return text;
                }
            }
            case BooklistGroup.READ_STATUS: {
                return ReadStatus.getById(rowData.getInt(key)).getLabel(context,
                                                                        Details.AutoSelect,
                                                                        style);
            }
            case BooklistGroup.LANGUAGE: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_language);
                } else {
                    return serviceLocator.getLanguages().getDisplayLanguageFromISO3(context, text);
                }
            }
            case BooklistGroup.CONDITION: {
                final int condition = rowData.getInt(key);
                if (condition < conditionDescriptions.length) {
                    return conditionDescriptions[condition];
                }
                // We should never get here... flw
                return conditionDescriptions[0];
            }
            case BooklistGroup.RATING: {
                // DOM_BOOK_RATING is a 'real' but the GroupKey will cast it to an integer.
                final int rating = rowData.getInt(key);
                // This is the text based formatting, as used by the level/scroller text.
                if (rating > 0 && rating <= Book.RATING_STARS) {
                    return context.getResources()
                                  .getQuantityString(R.plurals.n_stars, rating, rating);
                }
                return context.getString(R.string.bob_empty_rating);
            }
            case BooklistGroup.LENDING: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.lbl_available);
                } else {
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_LAST_UPDATE_YEAR:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.DATE_READ_YEAR: {
                // It's an int, but we just display it or not, so use String
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_year);
                } else {
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.DATE_PUBLISHED_MONTH:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH:
            case BooklistGroup.DATE_READ_MONTH: {
                final int month = rowData.getInt(key);
                if (month > 0 && month <= 12) {
                    return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE,
                                                          locales.get(0));
                }
                return context.getString(R.string.bob_empty_month);
            }

            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_LAST_UPDATE_DAY:
            case BooklistGroup.DATE_READ_DAY: {
                // It's an int, but we just display it or not, so use String
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_day);
                } else {
                    return text;
                }
            }


            case BooklistGroup.FORMAT:
            case BooklistGroup.GENRE:
            case BooklistGroup.LOCATION:
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.COLOR:
            case BooklistGroup.BOOK_TITLE_1ST_CHAR:
            case BooklistGroup.SERIES_TITLE_1ST_CHAR:
            case BooklistGroup.AUTHOR_FAMILY_NAME_1ST_CHAR:
            case BooklistGroup.PUBLISHER_NAME_1ST_CHAR:
                // BooklistGroup.BOOK only here to please lint
            case BooklistGroup.BOOK:
            default: {
                final String text = rowData.getString(key);
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_field);
                } else {
                    return text;
                }
            }
        }
    }
}
