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

package com.hardbacknutter.nevertoomanybooks.sync.calibre.coders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

// NEWTHINGS: Calibre adding a custom field
public class CalibreCustomFieldCoder {

    private static final String TAG = "CalibreCustomFieldCoder";

    /** A text "None" as value. Can/will be seen. This is the python equivalent of {@code null}. */
    private static final String VALUE_IS_NONE = "None";
    private static final String VALUE_IS_NULL = "null";
    private static final String ERROR_UNSUPPORTED_TYPE = "Unsupported custom field type: ";

    private final DateParser<LocalDateTime> dateParser;
    @NonNull
    private final RealNumberParser realNumberParser;
    @NonNull
    private final RatingParser ratingParser;

    /**
     * Constructor.
     *
     * @param dateParser       to use
     * @param ratingParser     to use
     * @param realNumberParser to use
     */
    public CalibreCustomFieldCoder(@NonNull final DateParser<LocalDateTime> dateParser,
                                   @NonNull final RatingParser ratingParser,
                                   @NonNull final RealNumberParser realNumberParser) {
        this.dateParser = dateParser;
        this.realNumberParser = realNumberParser;
        this.ratingParser = ratingParser;
    }

    /**
     * Check if the given String is a {@code null} value of any possible nulls.
     *
     * @param value to check
     *
     * @return {@code true} when {@code null}
     */
    private static boolean isNullString(@Nullable final String value) {
        return value == null || VALUE_IS_NONE.equals(value) || VALUE_IS_NULL.equals(value);
    }

    /**
     * Decode values encoded in JSON. Used during Sync.
     *
     * @param cf   field
     * @param data to decode
     * @param book to update
     *
     * @throws JSONException upon any parsing error
     */
    void decode(@NonNull final CalibreCustomField cf,
                @NonNull final JSONObject data,
                @NonNull final Book book)
            throws JSONException {

        // Special handling fields
        if (CalibreCustomField.FIELD_READ_PROGRESS.equals(cf.getCalibreKey())) {
            final String value = data.getString(CalibreCustomField.VALUE);
            decodeReadingProgress(value, book);
        } else {
            // otherwise just by type
            decodeCustomFieldByType(cf, data, book);
        }
    }

    /**
     * Decode values from raw Strings. Used by the CSV importer.
     *
     * @param cf    field
     * @param value to decode
     * @param book  to update
     */
    public void decode(@NonNull final CalibreCustomField cf,
                       @NonNull final String value,
                       @NonNull final Book book) {
        // Special handling fields
        if (CalibreCustomField.FIELD_READ_PROGRESS.equals(cf.getCalibreKey())) {
            decodeReadingProgress(value, book);
        } else {
            // otherwise just by type
            decodeCustomFieldByType(cf, value, book);
        }
    }

    private void decodeReadingProgress(@NonNull final String value,
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

    private void decodeCustomFieldByType(@NonNull final CalibreCustomField cf,
                                         @NonNull final JSONObject data,
                                         @NonNull final Book book)
            throws JSONException {
        // catch the special json null value.
        if (data.isNull(CalibreCustomField.VALUE)) {
            return;
        }

        // NEWTHINGS: Calibre adding a custom field or field type
        //  Make sure to keep in sync with decodeCustomFieldByType(... String...).
        switch (cf.getType()) {
            case CalibreCustomField.Type.RATING: {
                // We don't check the field name; if for whatever reason
                // a user defines multiple fields of this type:
                // 1. last one wins
                // 2. user will likely log an issue... tackle it then

                // Decoding: we receive a 0.0 to 5.0 value
                final String value = data.optString(CalibreCustomField.VALUE, null);
                ratingParser.parse(value).ifPresent(book::setRating);
                break;
            }
            case CalibreCustomField.Type.TEXT:
            case CalibreCustomField.Type.COMMENTS:
            case CalibreCustomField.Type.COMPOSITE:
            case CalibreCustomField.Type.ENUMERATION: {
                final String value = data.optString(CalibreCustomField.VALUE, null);
                if (!isNullString(value)) {
                    book.putString(cf.getDbKey(), SearchEngineUtils.cleanText(value));
                }
                break;
            }
            case CalibreCustomField.Type.DATETIME: {
                final String value = data.optString(CalibreCustomField.VALUE, null);
                if (!isNullString(value)) {
                    dateParser.parse(value).ifPresent(
                            date -> book.putLocalDateTime(cf.getDbKey(), date));
                }
                break;
            }
            case CalibreCustomField.Type.INT: {
                final Integer value = data.optIntegerObject(CalibreCustomField.VALUE, null);
                if (value != null) {
                    book.putInt(cf.getDbKey(), value);
                }
                break;
            }
            case CalibreCustomField.Type.FLOAT: {
                final Float value = data.optFloatObject(CalibreCustomField.VALUE, null);
                if (value != null) {
                    book.putFloat(cf.getDbKey(), value);
                }
                break;
            }
            case CalibreCustomField.Type.BOOL: {
                final Boolean value = data.optBooleanObject(CalibreCustomField.VALUE, null);
                if (value != null) {
                    book.putBoolean(cf.getDbKey(), value);
                }
                break;
            }
            case CalibreCustomField.Type.SERIES:
                // URGENT: Type.SERIES not supported
                // src/calibre/library/field_metadata.py#720
                // When there is a field "blah" of type "series"
                // then there will also be a field "blah_index" of type "float"
            default: {
                // Don't throw in case we meet types not defined yet.
                LoggerFactory.getLogger().w(TAG, ERROR_UNSUPPORTED_TYPE + cf);
                break;
            }
        }
    }

    // There is quite a lot of duplicate code/logic here
    // due to the CSV fields all being String.
    private void decodeCustomFieldByType(@NonNull final CalibreCustomField cf,
                                         @NonNull final String value,
                                         @NonNull final Book book) {
        // NEWTHINGS: Calibre adding a custom field or field type
        //  Make sure to keep in sync with decodeCustomFieldByType(... JSONObject...)
        switch (cf.getType()) {
            case CalibreCustomField.Type.RATING: {
                // Decoding: we receive a 0.0 to 5.0 value
                ratingParser.parse(value).ifPresent(book::setRating);
                break;
            }
            case CalibreCustomField.Type.TEXT:
            case CalibreCustomField.Type.COMMENTS:
            case CalibreCustomField.Type.COMPOSITE:
            case CalibreCustomField.Type.ENUMERATION: {
                book.putString(cf.getDbKey(), SearchEngineUtils.cleanText(value));
                break;
            }
            case CalibreCustomField.Type.DATETIME: {
                dateParser.parse(value).ifPresent(
                        date -> book.putLocalDateTime(cf.getDbKey(), date));
                break;
            }
            case CalibreCustomField.Type.INT: {
                try {
                    book.putInt(cf.getDbKey(), NumberParser.toInt(value));
                } catch (@NonNull final NumberFormatException ignored) {
                    // ignored
                }
                break;
            }
            case CalibreCustomField.Type.FLOAT: {
                try {
                    book.putFloat(cf.getDbKey(), realNumberParser.parseFloat(value));
                } catch (@NonNull final NumberFormatException ignored) {
                    // ignored
                }
                break;
            }
            case CalibreCustomField.Type.BOOL: {
                try {
                    final boolean b = BooleanParser.parseBoolean(value, true);
                    book.putBoolean(cf.getDbKey(), b);
                } catch (@NonNull final NumberFormatException ignored) {
                    // ignored
                }
                break;
            }
            case CalibreCustomField.Type.SERIES:
                // URGENT: Type.SERIES not supported
                // src/calibre/library/field_metadata.py#720
                // When there is a field "blah" of type "series"
                // then there will also be a field "blah_index" of type "float"
            default: {
                // Don't throw in case we meet types not defined yet.
                LoggerFactory.getLogger().w(TAG, ERROR_UNSUPPORTED_TYPE + cf);
                break;
            }
        }
    }

    /**
     * Encode the fields from the given library.  Used during Sync.
     *
     * @param library     for the list of fields to use
     * @param localBook   to read values from to encode
     * @param calibreBook to update with the encodings
     */
    void encode(@NonNull final CalibreLibrary library,
                @NonNull final Book localBook,
                @NonNull final JSONObject calibreBook) {
        for (final CalibreCustomField cf : library.getCustomFields()) {
            // NEWTHINGS: Calibre adding a custom field or field type
            switch (cf.getType()) {
                case CalibreCustomField.Type.RATING: {
                    // Encoding: we must send an int in the range 0..10
                    final float rating = localBook.getRating(realNumberParser);
                    final int scaled = (int) MathUtils.clamp(rating * 2, 0, 10);
                    calibreBook.put(cf.getCalibreKey(), scaled);
                    break;
                }
                case CalibreCustomField.Type.TEXT:
                case CalibreCustomField.Type.COMMENTS:
                case CalibreCustomField.Type.DATETIME:
                case CalibreCustomField.Type.ENUMERATION: {
                    // local empty fields ARE send, as this will tell Calibre to remove those
                    calibreBook.put(cf.getCalibreKey(), localBook.getString(cf.getDbKey()));
                    break;
                }
                case CalibreCustomField.Type.INT: {
                    calibreBook.put(cf.getCalibreKey(), localBook.getInt(cf.getDbKey()));
                    break;
                }
                case CalibreCustomField.Type.FLOAT: {
                    calibreBook.put(cf.getCalibreKey(), localBook.getFloat(cf.getDbKey(),
                                                                           realNumberParser));
                    break;
                }
                case CalibreCustomField.Type.BOOL: {
                    calibreBook.put(cf.getCalibreKey(), localBook.getBoolean(cf.getDbKey()));
                    break;
                }
                case CalibreCustomField.Type.COMPOSITE: {
                    // READ-ONLY! These are NEVER written.
                    break;
                }
                case CalibreCustomField.Type.SERIES:
                    // URGENT: Type.SERIES not supported
                    // src/calibre/library/field_metadata.py#720
                    // When there is a field "blah" of type "series"
                    // then there will also be a field "blah_index" of type "float"
                default: {
                    // Don't throw in case we meet types not defined yet.
                    LoggerFactory.getLogger().w(TAG, ERROR_UNSUPPORTED_TYPE + cf);
                    break;
                }
            }
        }
    }
}
