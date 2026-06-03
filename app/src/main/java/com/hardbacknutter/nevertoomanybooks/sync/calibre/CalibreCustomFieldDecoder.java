/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.org.json.JSONObject;

// NEWTHINGS: adding a Calibre custom field
public class CalibreCustomFieldDecoder {

    /** A text "None" as value. Can/will be seen. This is the python equivalent of {@code null}. */
    private static final String VALUE_IS_NONE = "None";

    private final DateParser<LocalDateTime> dateParser;

    public CalibreCustomFieldDecoder(@NonNull final DateParser<LocalDateTime> dateParser) {
        this.dateParser = dateParser;
    }

    public void decode(@NonNull final CalibreCustomField cf,
                       @NonNull final JSONObject data,
                       @NonNull final Book book) {

        // Special handling fields
        if (CalibreCustomField.FIELD_READ_PROGRESS.equals(cf.getCalibreKey())) {
            final String value = data.getString(CalibreCustomField.VALUE);
            convertReadingProgress(value, book);
        } else {
            // otherwise just by type
            convertCustomFieldByType(cf, data, book);
        }
    }

    public void decode(@NonNull final CalibreCustomField cf,
                       @NonNull final String value,
                       @NonNull final Book book) {
        // Special handling fields
        if (CalibreCustomField.FIELD_READ_PROGRESS.equals(cf.getCalibreKey())) {
            convertReadingProgress(value, book);
        } else {
            // otherwise just by type
            convertCustomFieldByType(cf, value, book);
        }
    }

    private void convertReadingProgress(@NonNull final String value,
                                        @NonNull final Book book) {
        // 3 possible formats:
        // "4%"
        // "100 / 332"
        // "0.25"

        if (value.length() > 1 && value.endsWith("%")) {
            try {
                final int percentage = Integer
                        .parseInt(value.substring(0, value.length() - 1));
                book.setReadingProgress(new ReadingProgress(percentage));
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        } else if (value.length() > 3 && value.contains("/")) {
            final String[] split = value.split("/");
            if (split.length == 2) {
                try {
                    final int page = Integer.parseInt(split[0]);
                    final int total = Integer.parseInt(split[1]);
                    if (total > 0) {
                        book.setReadingProgress(new ReadingProgress(page, total));
                        if (!book.contains(DBKey.PAGES)) {
                            book.setPages(total);
                        }
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        } else {
            // fraction or unknown format
            try {
                final int percentage = (int) (Float.parseFloat(value) * 100);
                book.setReadingProgress(new ReadingProgress(percentage));
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
    }

    private void convertCustomFieldByType(@NonNull final CalibreCustomField cf,
                                          @NonNull final JSONObject data,
                                          @NonNull final Book book) {
        // NEWTHINGS: adding a Calibre custom field or field type
        switch (cf.getType()) {
            case CalibreCustomField.TYPE_BOOL: {
                book.putBoolean(cf.getDbKey(), data.getBoolean(CalibreCustomField.VALUE));
                break;
            }
            case CalibreCustomField.TYPE_INT: {
                book.putInt(cf.getDbKey(), data.getInt(CalibreCustomField.VALUE));
                break;
            }
            case CalibreCustomField.TYPE_COMMENTS:
            case CalibreCustomField.TYPE_COMPOSITE:
            case CalibreCustomField.TYPE_ENUMERATION:
            case CalibreCustomField.TYPE_TEXT: {
                final String value = data.getString(CalibreCustomField.VALUE);
                // ignore a remote 'not-set' value
                if (!VALUE_IS_NONE.equals(value)) {
                    book.putString(cf.getDbKey(), SearchEngineUtils.cleanText(value));
                }
                break;
            }
            case CalibreCustomField.TYPE_DATETIME: {
                final String value = data.getString(CalibreCustomField.VALUE);
                dateParser.parse(value).ifPresent(
                        date -> book.putLocalDateTime(cf.getDbKey(), date));
                break;
            }
            case CalibreCustomField.TYPE_RATING: {
                // We don't check the field name; if for whatever reason
                // a user defines multiple fields of this type:
                // 1. last one wins
                // 2. user will likely log an issue... tackle it then
                final int value = data.getInt(CalibreCustomField.VALUE);
                if (value > 0) {
                    book.setRating(value);
                }
                break;
            }
            default:
                throw new IllegalArgumentException(cf.getType());
        }
    }

    // There is quite a lot of duplicate code/logic here
    // due to the CSV fields all being String.
    // Make sure to keep in sync with the ,method above.
    private void convertCustomFieldByType(@NonNull final CalibreCustomField cf,
                                          @NonNull final String value,
                                          @NonNull final Book book) {
        // NEWTHINGS: adding a Calibre custom field or field type
        switch (cf.getType()) {
            case CalibreCustomField.TYPE_BOOL: {
                try {
                    final boolean b = BooleanParser.parseBoolean(value, true);
                    book.putBoolean(cf.getDbKey(), b);
                } catch (@NonNull final NumberFormatException ignored) {
                    // ignored
                }
                break;
            }
            case CalibreCustomField.TYPE_INT: {
                try {
                    final int i = NumberParser.toInt(value);
                    book.putInt(cf.getDbKey(), i);
                } catch (@NonNull final NumberFormatException ignored) {
                    // ignored
                }
                break;
            }
            case CalibreCustomField.TYPE_COMMENTS:
            case CalibreCustomField.TYPE_COMPOSITE:
            case CalibreCustomField.TYPE_ENUMERATION:
            case CalibreCustomField.TYPE_TEXT: {
                book.putString(cf.getDbKey(), SearchEngineUtils.cleanText(value));
                break;
            }
            case CalibreCustomField.TYPE_DATETIME: {
                dateParser.parse(value).ifPresent(
                        date -> book.putLocalDateTime(cf.getDbKey(), date));
                break;
            }
            case CalibreCustomField.TYPE_RATING: {
                final int i = NumberParser.toInt(value);
                if (i > 0) {
                    book.setRating(i);
                }
                break;
            }
        }
    }
}
