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
    private final String unreadStr;
    private final String a_space_b;

    @NonNull
    private final List<Locale> locales;

    Formatter(@NonNull final Context context,
              @NonNull final Style style,
              @NonNull final List<Locale> locales) {
        this.context = context;
        this.style = style;
        this.locales = locales;

        unreadStr = context.getString(R.string.lbl_unread);
        a_space_b = context.getString(R.string.a_space_b);

        conditionDescriptions = context.getResources().getStringArray(R.array.lbl_book_condition);
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
                return formatAuthor(rowData, key);
            }
            case BooklistGroup.SERIES: {
                return formatSeries(rowData, key);
            }
            case BooklistGroup.PUBLISHER: {
                return formatPublisher(rowData, key);
            }
            case BooklistGroup.READ_STATUS: {
                return ReadStatus.byId(rowData.getInt(key))
                                 .getLabel(context, Details.AutoSelect, style);
            }
            case BooklistGroup.LANGUAGE:
            case BooklistGroup.ORIGINAL_LANGUAGE: {
                return formatLanguage(rowData, key, serviceLocator);
            }
            case BooklistGroup.CONDITION: {
                return formatCondition(rowData, key);
            }
            case BooklistGroup.RATING: {
                return formatRating(rowData, key);
            }
            case BooklistGroup.LENDING: {
                return formatLending(rowData, key);
            }

            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_LAST_UPDATE_YEAR:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR: {
                return formatYear(rowData, key);
            }
            case BooklistGroup.DATE_READ_YEAR: {
                return formatDateRead(rowData, formatYear(rowData, key));
            }

            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.DATE_PUBLISHED_MONTH:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH: {
                return formatMonth(rowData, key);
            }
            case BooklistGroup.DATE_READ_MONTH: {
                return formatDateRead(rowData, formatMonth(rowData, key));
            }

            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_LAST_UPDATE_DAY: {
                return formatDay(rowData, key);
            }
            case BooklistGroup.DATE_READ_DAY: {
                return formatDateRead(rowData, formatDay(rowData, key));
            }

            case BooklistGroup.AUTHOR_FAMILY_NAME_1ST_CHAR:
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.BOOK_TITLE_1ST_CHAR:
            case BooklistGroup.COLOR:
            case BooklistGroup.FORMAT:
            case BooklistGroup.IDENTIFIER:
            case BooklistGroup.LOCATION:
            case BooklistGroup.PUBLISHER_NAME_1ST_CHAR:
            case BooklistGroup.SERIES_TITLE_1ST_CHAR:
            case BooklistGroup.TAGS_GENRE:
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

    @NonNull
    private CharSequence formatAuthor(@NonNull final DataHolder rowData,
                                      @NonNull final String key) {

        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_author);
        }

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        if (serviceLocator.isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
            && rowData.contains(DBKey.FK_AUTHOR_REAL_AUTHOR)) {
            // Specifically check for AUTHOR_REAL_AUTHOR as it will usually be 0
            // and no lookup will be needed.
            final long realAuthorId = rowData.getLong(DBKey.FK_AUTHOR_REAL_AUTHOR);
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

    @NonNull
    private String formatSeries(@NonNull final DataHolder rowData,
                                @NonNull final String key) {

        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_series);

        }

        if (style.isShowReorderedTitle()) {
            // We don't have full Objects here for Series/Publisher so we can't use
            // their methods for auto-reordering.
            //
            // FIXME: translated series are reordered in the book's language
            // It should be done using the Series language
            // but as long as we don't store the Series language there is no point
            final String lang = rowData.getString(DBKey.LANGUAGE);
            return ServiceLocator.getInstance().getReorderHelper()
                                 .reorder(context, text, lang, locales);
        } else {
            return text;
        }
    }

    @NonNull
    private String formatPublisher(@NonNull final DataHolder rowData,
                                   @NonNull final String key) {

        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_publisher);
        }

        // yes, we're using the 'title'. Adding specific publisher logic was
        // to much overhead for a presumably little used feature
        if (style.isShowReorderedTitle()) {
            // We don't have full Objects here for Series/Publisher so we can't use
            // their methods for auto-reordering.
            return ServiceLocator.getInstance().getReorderHelper()
                                 .reorder(context, text, (Locale) null, locales);
        } else {
            return text;
        }
    }

    @NonNull
    private String formatLanguage(@NonNull final DataHolder rowData,
                                  @NonNull final String key,
                                  final ServiceLocator serviceLocator) {
        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_language);
        } else {
            return serviceLocator.getLanguages().getDisplayLanguageFromISO3(context, text);
        }
    }

    @NonNull
    private String formatCondition(@NonNull final DataHolder rowData,
                                   @NonNull final String key) {
        final int condition = rowData.getInt(key);
        if (condition < conditionDescriptions.length) {
            return conditionDescriptions[condition];
        }
        // We should never get here... flw
        return conditionDescriptions[0];
    }

    @NonNull
    private String formatRating(@NonNull final DataHolder rowData,
                                @NonNull final String key) {
        // DOM_BOOK_RATING is a 'real' but the GroupKey will cast it to an integer.
        final int rating = rowData.getInt(key);
        // This is the text based formatting, as used by the level/scroller text.
        if (rating > 0 && rating <= Book.RATING_STARS) {
            return context.getResources()
                          .getQuantityString(R.plurals.n_stars, rating, rating);
        }
        return context.getString(R.string.bob_empty_rating);
    }

    @NonNull
    private String formatLending(@NonNull final DataHolder rowData,
                                 @NonNull final String key) {
        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.lbl_available);
        } else {
            return text;
        }
    }

    @NonNull
    private String formatYear(@NonNull final DataHolder rowData,
                              @NonNull final String key) {
        // It's an int, but we just display it or not, so use String
        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_year);
        } else {
            return text;
        }
    }

    private String formatMonth(@NonNull final DataHolder rowData,
                               @NonNull final String key) {
        final int month = rowData.getInt(key);
        if (month > 0 && month <= 12) {
            return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE,
                                                  locales.get(0));
        }
        return context.getString(R.string.bob_empty_month);
    }

    @NonNull
    private String formatDay(@NonNull final DataHolder rowData,
                             @NonNull final String key) {
        // It's an int, but we just display it or not, so use String
        final String text = rowData.getString(key);
        if (text.isEmpty()) {
            return context.getString(R.string.bob_empty_day);
        } else {
            return text;
        }
    }

    /**
     * This formatter fixes an issue that when we have one ore more of the groups
     * DATE_READ_YEAR/DATE_READ_MONTH/DATE_READ_DAY
     * there will be a duplicate "(Year not set)" (and similar for the others).
     * Reproduce this by creating a style with DATE_READ_YEAR, DATE_READ_MONTH, AUTHOR
     * <p>
     * This is caused by those domains have .addGroupDomain(BD_BOOK_IS_READ);
     * In theory this is CORRECT !
     * <p>
     * The first "(Year not set)" contains books which have been 'read'
     * but for which the date-read is NOT set.
     * The second "(Year not set)" contains books which have NOT been 'read'
     * But this is confusing to the user (and myself...)
     * <p>
     * Solution 1: remove the BD_BOOK_IS_READ from those 3 group definitions now we
     * have a single "(Year not set)"
     * Problem: that group now has both read/not-read books intermixed.
     * <p>
     * Solution 2: keep those BD_BOOK_IS_READ in the group definition,
     * but explicitly prevent BD_BOOK_IS_READ from being added to the whereClause
     * ... NOT a solution: this will remove the "(Year not set)" and "(Month not set)"
     * ... but the Author heading will now be duplicated.
     * This is worse than solution 1
     * <p>
     * Solution 3: don't change anything, but create a RowViewHolder for
     * those groups and change the label to include the 'Unread' status
     */
    @NonNull
    private String formatDateRead(@NonNull final DataHolder rowData,
                                  @NonNull final String text) {

        // Check presence first, and only then test on 'false'
        if (rowData.contains(DBKey.READ__BOOL) && !rowData.getBoolean(DBKey.READ__BOOL)) {
            return String.format(a_space_b, text, unreadStr);
        }

        return text;
    }
}
