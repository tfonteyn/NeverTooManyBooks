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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
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
 * we handle the formatting is here regardless.
 */
class Formatter
        implements FormatFunction {

    private static final String TAG = "Formatter";

    @NonNull
    private final Context context;
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;
    @NonNull
    private final Supplier<Languages> languagesSupplier;
    @NonNull
    private final Supplier<AuthorDao> authorDaoSupplier;
    @NonNull
    private final Style style;

    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;
    @NonNull
    private final List<Locale> locales;

    Formatter(@NonNull final Context context,
              @NonNull final Supplier<ReorderHelper> reorderHelperSupplier,
              @NonNull final Supplier<Languages> languagesSupplier,
              @NonNull final Supplier<AuthorDao> authorDaoSupplier,
              @NonNull final Style style,
              @NonNull final List<Locale> locales) {
        this.context = context;
        this.reorderHelperSupplier = reorderHelperSupplier;
        this.languagesSupplier = languagesSupplier;
        this.authorDaoSupplier = authorDaoSupplier;
        this.style = style;
        this.locales = locales;

        conditionDescriptions = context.getResources().getStringArray(R.array.conditions_book);
    }

    /**
     * Format the source string according to the BooklistGroup id.
     *
     * @param groupId the BooklistGroup id
     * @param rowData read only access to the row data
     * @param key     the {@link DBKey} for the item to be formatted
     *
     * @return Formatted string,
     *         or original string when no special format was needed or on any failure
     */
    @NonNull
    @Override
    public CharSequence format(final int groupId,
                               @NonNull final DataHolder rowData,
                               @NonNull final String key) {
        final String text = rowData.getString(key);

        // NEWTHINGS: BooklistGroup
        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_author);

                } else if (rowData.contains(DBKey.AUTHOR_REAL_AUTHOR)) {
                    // Specifically check for AUTHOR_REAL_AUTHOR as it will usually be 0
                    // and no lookup will be needed.
                    final long realAuthorId = rowData.getLong(DBKey.AUTHOR_REAL_AUTHOR);
                    if (realAuthorId != 0) {
                        final Author realAuthor = authorDaoSupplier.get().getById(realAuthorId);
                        if (realAuthor != null) {
                            return realAuthor.getStyledName(context, Details.Normal, style, text);
                        }
                    }
                }
                // already formatted by the SQL query
                return text;
            }
            case BooklistGroup.SERIES: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_series);

                } else if (reorderHelperSupplier.get().forDisplay(context)) {
                    // We don't have full Objects here for Series/Publisher so we can't use
                    // their methods for auto-reordering.
                    //
                    // FIXME: translated series are reordered in the book's language
                    // It should be done using the Series language
                    // but as long as we don't store the Series language there is no point
                    final String lang = rowData.getString(DBKey.LANGUAGE);
                    return reorderHelperSupplier.get().reorder(context, text, lang, locales);
                } else {
                    return text;
                }
            }
            case BooklistGroup.PUBLISHER: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_publisher);

                } else if (reorderHelperSupplier.get().forDisplay(context)) {
                    // We don't have full Objects here for Series/Publisher so we can't use
                    // their methods for auto-reordering.
                    //
                    return reorderHelperSupplier
                            .get().reorder(context, text, (Locale) null, locales);
                } else {
                    return text;
                }
            }
            case BooklistGroup.READ_STATUS: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_read_status);
                } else {
                    if (BooleanParser.parseBoolean(text, true)) {
                        return context.getString(R.string.lbl_read);
                    } else {
                        return context.getString(R.string.lbl_unread);
                    }
                }
            }
            case BooklistGroup.LANGUAGE: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_language);
                } else {
                    return languagesSupplier.get().getDisplayNameFromISO3(context, text);
                }
            }
            case BooklistGroup.CONDITION: {
                if (!text.isEmpty()) {
                    try {
                        final int i = Integer.parseInt(text);
                        if (i < conditionDescriptions.length) {
                            return conditionDescriptions[i];
                        }
                    } catch (@NonNull final NumberFormatException ignore) {
                        // ignore
                    }
                }
                return context.getString(R.string.unknown);
            }
            case BooklistGroup.RATING: {
                // This is the text based formatting, as used by the level/scroller text.
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_rating);
                } else {
                    try {
                        // Locale independent.
                        final int i = Integer.parseInt(text);
                        // If valid, get the name
                        if (i >= 0 && i <= Book.RATING_STARS) {
                            return context.getResources()
                                          .getQuantityString(R.plurals.n_stars, i, i);
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            LoggerFactory.getLogger()
                                         .e(TAG, e, "RATING=" + text);
                        }
                    }
                    return text;
                }
            }
            case BooklistGroup.LENDING: {
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
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_month);
                } else {
                    try {
                        final int m = Integer.parseInt(text);
                        // If valid, get the short name
                        if (m > 0 && m <= 12) {
                            return Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE,
                                                              locales.get(0));
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.e(TAG, "|text=`" + text + '`', e);
                        }
                    }
                    // Was invalid, just show the original
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_LAST_UPDATE_DAY:
            case BooklistGroup.DATE_READ_DAY: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_day);
                } else {
                    return text;
                }
            }

            // BooklistGroup.BOOK only here to please lint
            case BooklistGroup.BOOK:
            case BooklistGroup.FORMAT:
            case BooklistGroup.GENRE:
            case BooklistGroup.LOCATION:
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.COLOR:
            case BooklistGroup.BOOK_TITLE_1ST_CHAR:
            case BooklistGroup.SERIES_TITLE_1ST_CHAR:
            case BooklistGroup.AUTHOR_FAMILY_NAME_1ST_CHAR:
            case BooklistGroup.PUBLISHER_NAME_1ST_CHAR:
            default: {
                if (text.isEmpty()) {
                    return context.getString(R.string.bob_empty_field);
                } else {
                    return text;
                }
            }
        }
    }
}
