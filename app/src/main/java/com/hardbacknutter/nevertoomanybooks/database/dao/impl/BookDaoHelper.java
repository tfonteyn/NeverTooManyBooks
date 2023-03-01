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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.Cover;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * Preprocess a Book for storage. This class does not access to database.
 * <p>
 * Normal flow:
 * <ol>
 *     <li>Create this object</li>
 *     <li>{@link #process(Context)}</li>
 *     <li>{@link #filterValues(TableInfo)}</li>
 *     <li>insert or update the book to the database</li>
 *     <li>{@link #persistCovers()}</li>
 * </ol>
 */
public class BookDaoHelper {

    private static final String TAG = "BookDaoHelper";

    /** Used to transform Java-ISO to SQL-ISO datetime format. */
    private static final Pattern T = Pattern.compile("T");

    @NonNull
    private final Supplier<AppLocale> appLocaleSupplier;
    @NonNull
    private final Book book;
    private final boolean isNew;

    @NonNull
    private final Locale bookLocale;
    private final RealNumberParser realNumberParser;
    private final MoneyParser moneyParser;

    /**
     * Constructor.
     *
     * @param context           Current context
     * @param appLocaleSupplier deferred supplier for the {@link AppLocale}.
     */
    public BookDaoHelper(@NonNull final Context context,
                         @NonNull final Supplier<AppLocale> appLocaleSupplier,
                         @NonNull final Book book,
                         final boolean isNew) {
        this.appLocaleSupplier = appLocaleSupplier;
        this.book = book;
        this.isNew = isNew;

        // Handle Language field FIRST, we need it for _OB fields.
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        bookLocale = this.book.getAndUpdateLocale(context, true).orElse(userLocale);

        final List<Locale> locales = LocaleListUtils.asList(context, bookLocale);
        realNumberParser = new RealNumberParser(locales);
        moneyParser = new MoneyParser(context, realNumberParser);
    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     * Called during {@link BookDaoImpl#insert} and {@link BookDaoImpl#update}.
     *
     * @param context Current context
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    BookDaoHelper process(@NonNull final Context context) {
        // Handle TITLE
        if (book.contains(DBKey.TITLE)) {
            final String title = book.getTitle();
            final String reorderedTitle;
            if (ReorderHelper.forSorting(context)) {
                reorderedTitle = ReorderHelper.reorder(context, appLocaleSupplier.get(),
                                                       title, bookLocale);
            } else {
                reorderedTitle = title;
            }

            book.putString(DBKey.TITLE_OB, SqlEncode.orderByColumn(reorderedTitle, bookLocale));
        }

        // store only valid bits. The 'get' will normalise any incorrect 'long' value
        if (book.contains(DBKey.TOC_TYPE__BITMASK)) {
            book.setContentType(book.getContentType());
        }

        if (book.contains(DBKey.EDITION__BITMASK)) {
            book.putLong(DBKey.EDITION__BITMASK,
                         book.getLong(DBKey.EDITION__BITMASK) & Book.Edition.BITMASK_ALL_BITS);
        }

        // cleanup/build all price related fields
        DBKey.MONEY_KEYS.forEach(key -> processPrice(context, key));

        // replace 'T' by ' ' and truncate pure date fields if needed
        processDates();

        // make sure there are only valid external id's present
        processExternalIds();

        // lastly, cleanup null and blank fields as needed.
        processNullsAndBlanks();

        return this;
    }

    /**
     * Helper for {@link #process(Context)}.
     *
     * @param key key for the money (value) field
     */
    @VisibleForTesting
    public void processPrice(@NonNull final Context context,
                             @NonNull final String key) {
        try {
            final String currencyKey = key + DBKey.CURRENCY_SUFFIX;
            if (book.contains(key) && !book.contains(currencyKey)) {
                // Verify a price without a currency.
                // This check should not be needed as it SHOULD have been done before.
                // ... but paranoia...
                final Money money = moneyParser.parse(book.getString(key));
                // If the currency could be decoded, store the Money back into the book
                if (money != null) {
                    book.putMoney(key, money);
                    return;
                }
                // else just leave the original text in the book
            }

            // Either way, make sure any currency strings present are uppercase
            if (book.contains(currencyKey)) {
                book.putString(currencyKey, book.getString(currencyKey)
                                                .toUpperCase(Locale.ENGLISH));
            }
        } catch (@NonNull final NumberFormatException e) {
            // just leave the original text in the book
        }
    }

    /**
     * Helper for {@link #process(Context)}.
     * <p>
     * Truncate Date fields to being pure dates without a time segment.
     * Replaces 'T' with ' ' to please SqLite/SQL datetime standards.
     * <p>
     * <strong>Note 1:</strong>: We do not fully parse each date string,
     * to verify/correct as an SQLite datetime string.
     * It is assumed that during normal logic flow this is already done.
     * The 'T' is the exception as that is easier to handle here for all fields.
     * <p>
     * <strong>Note 2:</strong>: such a full parse should be done during import operations.
     */
    @VisibleForTesting
    public void processDates() {
        final List<Domain> domains = DBDefinitions.TBL_BOOKS.getDomains();

        // Partial/Full Date strings
        domains.stream()
               .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Date)
               .map(Domain::getName)
               .filter(book::contains)
               .forEach(key -> {
                   final String date = book.getString(key);
                   // This is very crude... we simply truncate to 10 characters maximum
                   // i.e. 'YYYY-MM-DD', but do not verify if it's a valid date.
                   if (date.length() > 10) {
                       book.putString(key, date.substring(0, 10));
                   }
               });

        // Full UTC based DateTime strings
        domains.stream()
               .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.DateTime)
               .map(Domain::getName)
               .filter(book::contains)
               .forEach(key -> {
                   final String date = book.getString(key);
                   // Again, very crude logic... we simply check for the 11th char being a 'T'
                   // and if so, replace it with a space
                   if (date.length() > 10 && date.charAt(10) == 'T') {
                       book.putString(key, T.matcher(date).replaceFirst(" "));
                   }
               });
    }

    /**
     * Helper for {@link #process(Context)}.
     * <p>
     * Processes the external id keys.
     * <p>
     * For new books, REMOVE zero values, empty strings AND null values
     * Existing books, REPLACE zero values and empty string with a {@code null}
     * <p>
     * Invalid values are always removed.
     * <p>
     * Further processing should be done in {@link #processNullsAndBlanks()}.
     */
    @VisibleForTesting
    public void processExternalIds() {
        final List<Domain> domains = SearchEngineConfig.getExternalIdDomains();

        domains.stream()
               .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Integer)
               .map(Domain::getName)
               .filter(book::contains)
               .forEach(key -> {
                   final Object o = book.get(key, realNumberParser);
                   try {
                       if (isNew) {
                           // For new books:
                           if (o == null) {
                               // remove null values
                               book.remove(key);
                           } else {
                               final long v = book.getLong(key);
                               if (v < 1) {
                                   // remove zero values
                                   book.remove(key);
                               }
                           }
                       } else {
                           // for existing books, leave null values as-is
                           if (o != null) {
                               final long v = book.getLong(key);
                               if (v < 1) {
                                   // replace zero values with a null
                                   book.putNull(key);
                               }
                           }
                       }
                   } catch (@NonNull final NumberFormatException e) {
                       // always remove illegal input
                       book.remove(key);

                       if (BuildConfig.DEBUG /* always */) {
                           LoggerFactory.getLogger()
                                        .d(TAG, "preprocessExternalIds",
                                           "NumberFormatException"
                                           + "|name=" + key
                                           + "|value=`" + o + '`');
                       }
                   }
               });

        domains.stream()
               .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Text)
               .map(Domain::getName)
               .filter(book::contains)
               .forEach(key -> {
                   final Object o = book.get(key, realNumberParser);
                   if (isNew) {
                       // for new books,
                       if (o == null) {
                           // remove null values
                           book.remove(key);
                       } else {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // remove blank/zero values
                               book.remove(key);
                           }
                       }
                   } else {
                       // for existing books, leave null values as-is
                       if (o != null) {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // replace blank/zero values with a null
                               book.putNull(key);
                           }
                       }
                   }
               });
    }

    /**
     * Helper for {@link #process(Context)}.
     *
     * <ul>Fields in this Book, which have a default in the database and
     *      <li>which are null but not allowed to be null</li>
     *      <li>which are null/empty (i.e. blank) but not allowed to be blank</li>
     * </ul>
     * <p>
     * For new books, REMOVE those keys.
     * Existing books, REPLACE those keys with the default value for the column.
     */
    @VisibleForTesting
    public void processNullsAndBlanks() {

        DBDefinitions.TBL_BOOKS
                .getDomains()
                .stream()
                .filter(domain -> book.contains(domain.getName()) && domain.hasDefault())
                .forEach(domain -> {
                    final Object o = book.get(domain.getName(), realNumberParser);
                    if (
                        // Fields which are null but not allowed to be null
                            o == null && domain.isNotNull()
                            ||
                            // Fields which are null/empty (i.e. blank) but not allowed to be blank
                            (o == null || o.toString().isEmpty()) && domain.isNotBlank()
                    ) {
                        if (isNew) {
                            book.remove(domain.getName());
                        } else {
                            // restore the column to its default value.
                            //noinspection ConstantConditions
                            book.putString(domain.getName(), domain.getDefault());
                        }
                    }
                });
    }

    @NonNull
    ContentValues filterValues(@NonNull final TableInfo tableInfo) {

        return filterValues(tableInfo, book);
    }

    /**
     * Return a ContentValues collection containing only those values from 'source'
     * that match columns in 'dest'.
     * <ul>
     *      <li>Exclude the primary key from the list of columns.</li>
     *      <li>data will be transformed based on the column definition.<br>
     *          e.g. if a columns says it's Integer, an incoming boolean will
     *          be transformed to 0/1</li>
     * </ul>
     *
     * @param tableInfo destination table
     * @param book      A collection with the columns to be set. May contain extra data.
     *
     * @return New and filtered ContentValues
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    ContentValues filterValues(@NonNull final TableInfo tableInfo,
                               @NonNull final Book book) {
        final ContentValues cv = new ContentValues();
        for (final String key : book.keySet()) {
            // We've seen empty keys in old BC imports - this is likely due to a csv column
            // not being properly escaped, i.e. the data itself containing a comma.
            // Not much we can do about that, so skip if encountered.
            if (!key.isEmpty()) {
                // Get column info for this column.
                final ColumnInfo columnInfo = tableInfo.getColumn(key);
                // Check if we actually have a matching column, and never update a PK.
                if (columnInfo != null && !columnInfo.isPrimaryKey()) {

                    final Object entry = book.get(key, realNumberParser);
                    if (entry == null) {
                        if (columnInfo.isNullable()) {
                            cv.putNull(key);
                        } else {
                            throw new IllegalArgumentException(
                                    "NULL on a non-nullable column|key=" + key);
                        }
                    } else {
                        final String columnName = columnInfo.getName();
                        switch (columnInfo.getCursorFieldType()) {
                            case Cursor.FIELD_TYPE_STRING: {
                                if (entry instanceof String) {
                                    cv.put(columnName, (String) entry);
                                } else {
                                    cv.put(columnName, entry.toString());
                                }
                                break;
                            }
                            case Cursor.FIELD_TYPE_INTEGER: {
                                if (entry instanceof Boolean) {
                                    cv.put(columnName, (Boolean) entry ? 1 : 0);
                                } else {
                                    try {
                                        cv.put(columnName, NumberParser.toInt(entry));
                                    } catch (@NonNull final NumberFormatException e) {
                                        // We do NOT want to fail at this point.
                                        // Log, but skip this field.
                                        LoggerFactory.getLogger()
                                                     .w(TAG, e.getMessage(),
                                                        "columnName(int)=" + columnName,
                                                        "entry=" + entry,
                                                        "book=" + book);
                                    }
                                }
                                break;
                            }
                            case Cursor.FIELD_TYPE_FLOAT: {
                                try {
                                    cv.put(columnName, realNumberParser.toDouble(entry));
                                } catch (@NonNull final NumberFormatException e) {
                                    // We do NOT want to fail at this point.
                                    // Although the conclusion cannot be 100%, we're
                                    // very likely looking at a "list price" field coming
                                    // from an import which cannot be parsed.
                                    // Log, but skip this field.
                                    // This does mean that sentiments like:
                                    // list_price="a lot of money" will NOT be preserved!
                                    LoggerFactory.getLogger()
                                                 .w(TAG, e.getMessage(),
                                                    "columnName(float)=" + columnName,
                                                    "entry=" + entry,
                                                    "book=" + book);
                                }
                                break;
                            }
                            case Cursor.FIELD_TYPE_BLOB: {
                                if (entry instanceof byte[]) {
                                    cv.put(columnName, (byte[]) entry);
                                } else {
                                    throw new IllegalArgumentException(
                                            "non-null Blob but not a byte[] ?"
                                            + "|columnName(blob)=" + columnName
                                            + "|key=" + key);
                                }
                                break;
                            }
                            case Cursor.FIELD_TYPE_NULL:
                            default:
                                // ignore
                                break;
                        }
                    }
                }
            }
        }
        return cv;
    }

    /**
     * Called during {@link BookDaoImpl#insert} and {@link BookDaoImpl#update}.
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on generic/other IO failures
     */
    void persistCovers()
            throws StorageException, IOException {

        final String uuid = book.getString(DBKey.BOOK_UUID);

        for (int cIdx = 0; cIdx < Book.BKEY_TMP_FILE_SPEC.length; cIdx++) {
            if (book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx])) {
                final String fileSpec = book.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "storeCovers",
                                    "BKEY_TMP_FILE_SPEC[" + cIdx + "]=`" + fileSpec + '`');
                }

                final Cover cover = new Cover(uuid, cIdx);
                if (fileSpec.isEmpty()) {
                    // An empty fileSpec indicates we need to delete the cover
                    cover.delete();
                } else {
                    // Rename the temp file to the uuid permanent file name
                    cover.persist(new File(fileSpec));
                }

                book.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
            }
        }
    }
}
