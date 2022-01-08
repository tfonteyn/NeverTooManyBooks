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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

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
    private final Book mBook;
    private final boolean mIsNew;

    @NonNull
    private final Locale mBookLocale;

    public BookDaoHelper(@NonNull final Context context,
                         @NonNull final Book book,
                         final boolean isNew) {
        mBook = book;
        mIsNew = isNew;

        // Handle Language field FIRST, we need it for _OB fields.
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        mBookLocale = mBook.getAndUpdateLocale(context, userLocale, true);
    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     * Called during {@link BookDaoImpl#insert(Context, Book, int)}
     * and {@link BookDaoImpl#update(Context, Book, int)}.
     *
     * @param context Current context
     */
    BookDaoHelper process(@NonNull final Context context) {
        // Handle TITLE
        if (mBook.contains(DBKey.KEY_TITLE)) {
            final String obTitle = mBook.reorderTitleForSorting(context, mBookLocale);
            mBook.putString(DBKey.KEY_TITLE_OB, SqlEncode
                    .orderByColumn(obTitle, mBookLocale));
        }

        // store only valid bits. The 'get' will normalise any incorrect 'long' value
        if (mBook.contains(DBKey.BITMASK_TOC)) {
            mBook.setContentType(mBook.getContentType());
        }

        if (mBook.contains(DBKey.BITMASK_EDITION)) {
            mBook.putLong(DBKey.BITMASK_EDITION,
                          mBook.getLong(DBKey.BITMASK_EDITION) & Book.Edition.BITMASK_ALL_BITS);
        }

        // cleanup/build all price related fields
        processPrice(DBKey.PRICE_LISTED);
        processPrice(DBKey.PRICE_PAID);

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
    public void processPrice(@NonNull final String key) {

        final String currencyKey = key + DBKey.SUFFIX_KEY_CURRENCY;
        // handle a price without a currency.
        if (mBook.contains(key) && !mBook.contains(currencyKey)) {
            // we presume the user bought the book in their own currency.
            final Money money = new Money(mBookLocale, mBook.getString(key));
            if (money.getCurrency() != null) {
                mBook.putMoney(key, money);
                return;
            }
            // else just leave the original in the data
        }

        // Make sure currencies are uppercase
        if (mBook.contains(currencyKey)) {
            mBook.putString(currencyKey, mBook.getString(currencyKey).toUpperCase(Locale.ENGLISH));
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
     *
     * <strong>Note 2:</strong>: such a full parse should be done during import operations.
     */
    @VisibleForTesting
    public void processDates() {
        final List<Domain> domains = DBDefinitions.TBL_BOOKS.getDomains();

        // Partial/Full Date strings
        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_DATE))
               .map(Domain::getName)
               .filter(mBook::contains)
               .forEach(key -> {
                   final String date = mBook.getString(key);
                   // This is very crude... we simply truncate to 10 characters maximum
                   // i.e. 'YYYY-MM-DD', but do not verify if it's a valid date.
                   if (date.length() > 10) {
                       mBook.putString(key, date.substring(0, 10));
                   }
               });

        // Full UTC based DateTime strings
        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_DATETIME))
               .map(Domain::getName)
               .filter(mBook::contains)
               .forEach(key -> {
                   final String date = mBook.getString(key);
                   // Again, very crude logic... we simply check for the 11th char being a 'T'
                   // and if so, replace it with a space
                   if (date.length() > 10 && date.charAt(10) == 'T') {
                       mBook.putString(key, T.matcher(date).replaceFirst(" "));
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
        final List<Domain> domains = SearchEngineRegistry.getInstance().getExternalIdDomains();

        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_INTEGER))
               .map(Domain::getName)
               .filter(mBook::contains)
               .forEach(key -> {
                   final Object o = mBook.get(key);
                   try {
                       if (mIsNew) {
                           // For new books:
                           if (o == null) {
                               // remove null values
                               mBook.remove(key);
                           } else {
                               final long v = mBook.getLong(key);
                               if (v < 1) {
                                   // remove zero values
                                   mBook.remove(key);
                               }
                           }
                       } else {
                           // for existing books, leave null values as-is
                           if (o != null) {
                               final long v = mBook.getLong(key);
                               if (v < 1) {
                                   // replace zero values with a null
                                   mBook.putNull(key);
                               }
                           }
                       }
                   } catch (@NonNull final NumberFormatException e) {
                       // always remove illegal input
                       mBook.remove(key);

                       if (BuildConfig.DEBUG /* always */) {
                           Logger.d(TAG, "preprocessExternalIds", "NumberFormatException"
                                                                  + "|name=" + key
                                                                  + "|value=`" + o + '`');
                       }
                   }
               });

        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_TEXT))
               .map(Domain::getName)
               .filter(mBook::contains)
               .forEach(key -> {
                   final Object o = mBook.get(key);
                   if (mIsNew) {
                       // for new books,
                       if (o == null) {
                           // remove null values
                           mBook.remove(key);
                       } else {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // remove empty/zero values
                               mBook.remove(key);
                           }
                       }
                   } else {
                       // for existing books, leave null values as-is
                       if (o != null) {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // replace "0" and empty strings with a null
                               mBook.putNull(key);
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
                .filter(domain -> mBook.contains(domain.getName()) && domain.hasDefault())
                .forEach(domain -> {
                    final Object o = mBook.get(domain.getName());
                    if (
                        // Fields which are null but not allowed to be null
                            (o == null && domain.isNotNull())
                            ||
                            // Fields which are null/empty (i.e. blank) but not allowed to be blank
                            ((o == null || o.toString().isEmpty()) && domain.isNotBlank())
                    ) {
                        if (mIsNew) {
                            mBook.remove(domain.getName());
                        } else {
                            // restore the column to its default value.
                            //noinspection ConstantConditions
                            mBook.putString(domain.getName(), domain.getDefault());
                        }
                    }
                });
    }

    @NonNull
    ContentValues filterValues(@NonNull final TableInfo tableInfo) {
        return filterValues(tableInfo, mBook, mBookLocale);
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
     * @param tableInfo   destination table
     * @param dataManager A collection with the columns to be set. May contain extra data.
     * @param bookLocale  the Locale to use for character case manipulation
     *
     * @return New and filtered ContentValues
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    ContentValues filterValues(@NonNull final TableInfo tableInfo,
                               @NonNull final DataManager dataManager,
                               @NonNull final Locale bookLocale) {

        final ContentValues cv = new ContentValues();
        for (final String key : dataManager.keySet()) {
            // Get column info for this column.
            final ColumnInfo columnInfo = tableInfo.getColumn(key);
            // Check if we actually have a matching column, and never update a PK.
            if (columnInfo != null && !columnInfo.isPrimaryKey()) {
                final Object entry = dataManager.get(key);
                if (entry == null) {
                    if (columnInfo.isNullable()) {
                        cv.putNull(key);
                    } else {
                        throw new IllegalArgumentException(
                                "NULL on a non-nullable column|key=" + key);
                    }
                } else {
                    switch (columnInfo.storageClass) {
                        case Real: {
                            if (entry instanceof Number) {
                                cv.put(columnInfo.name, ((Number) entry).doubleValue());
                            } else {
                                // Theoretically we should only get here during an import,
                                // where everything is handled as a String.
                                final String stringValue = entry.toString().trim();
                                if (stringValue.isEmpty()) {
                                    cv.put(columnInfo.name, "");
                                } else {
                                    // Sqlite does not care about float/double,
                                    // Using double covers float as well.
                                    //Reminder: do NOT use the bookLocale to parse.
                                    cv.put(columnInfo.name, Double.parseDouble(stringValue));
                                }
                            }
                            break;
                        }
                        case Integer: {
                            if (entry instanceof Boolean) {
                                if ((Boolean) entry) {
                                    cv.put(columnInfo.name, 1);
                                } else {
                                    cv.put(columnInfo.name, 0);
                                }
                            } else if (entry instanceof Integer) {
                                cv.put(columnInfo.name, (Integer) entry);
                            } else if (entry instanceof Long) {
                                cv.put(columnInfo.name, (Long) entry);
                            } else {
                                // Theoretically we should only get here during an import,
                                // where everything is handled as a String.
                                final String s = entry.toString().toLowerCase(bookLocale);
                                if (s.isEmpty()) {
                                    cv.put(columnInfo.name, "");
                                } else {
                                    // It's not strictly needed to do these conversions.
                                    // parseInt/catch(Exception) works,
                                    // but it's not elegant...
                                    switch (s) {
                                        case "1":
                                        case "true":
                                        case "t":
                                        case "yes":
                                        case "y":
                                            cv.put(columnInfo.name, 1);
                                            break;

                                        case "0":
                                        case "0.0":
                                        case "false":
                                        case "f":
                                        case "no":
                                        case "n":
                                            cv.put(columnInfo.name, 0);
                                            break;

                                        default:
                                            //Reminder: do NOT use the bookLocale to parse.
                                            cv.put(columnInfo.name, Integer.parseInt(s));
                                    }

                                }
                            }
                            break;
                        }
                        case Text: {
                            if (entry instanceof String) {
                                cv.put(columnInfo.name, (String) entry);
                            } else {
                                cv.put(columnInfo.name, entry.toString());
                            }
                            break;
                        }
                        case Blob: {
                            if (entry instanceof byte[]) {
                                cv.put(columnInfo.name, (byte[]) entry);
                            } else {
                                throw new IllegalArgumentException(
                                        "non-null Blob but not a byte[] ?"
                                        + "|column.name=" + columnInfo.name
                                        + "|key=" + key);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return cv;
    }

    /**
     * Called during {@link BookDaoImpl#insert(Context, Book, int)}
     * and {@link BookDaoImpl#update(Context, Book, int)}.
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on failure
     */
    void persistCovers()
            throws CoverStorageException, IOException {

        final String uuid = mBook.getString(DBKey.KEY_BOOK_UUID);

        if (BuildConfig.DEBUG /* always */) {
            // the UUID should always be valid here
            SanityCheck.requireValue(uuid, "uuid");
        }

        for (int cIdx = 0; cIdx < 2; cIdx++) {
            if (mBook.contains(Book.BKEY_TMP_FILE_SPEC[cIdx])) {
                final String fileSpec = mBook.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "storeCovers",
                             "BKEY_TMP_FILE_SPEC[" + cIdx + "]=`" + fileSpec + '`');
                }

                if (fileSpec.isEmpty()) {
                    // An empty fileSpec indicates we need to delete the cover
                    FileUtils.delete(mBook.getPersistedCoverFile(cIdx));
                    // Delete from the cache. And yes, we also delete the ones
                    // where != index, but we don't care; it's a cache.
                    if (ImageUtils.isImageCachingEnabled()) {
                        ServiceLocator.getInstance().getCoverCacheDao().delete(uuid);
                    }
                } else {
                    // Rename the temp file to the uuid permanent file name
                    mBook.persistCover(new File(fileSpec), cIdx);
                }

                mBook.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
            } else {
                // If the key is NOT present, we don't need to do anything!
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "storeCovers",
                             "BKEY_TMP_FILE_SPEC[" + cIdx + "]=<not present>");
                }
            }
        }
    }
}
