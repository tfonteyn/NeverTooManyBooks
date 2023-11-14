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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.ColorMapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.Mapper;

class ResultsAccumulator {

    private static final String TAG = "ResultsAccumulator";

    private static final Set<String> LIST_KEYS = Set.of(Book.BKEY_AUTHOR_LIST,
                                                        Book.BKEY_SERIES_LIST,
                                                        Book.BKEY_PUBLISHER_LIST,
                                                        Book.BKEY_TOC_LIST,
                                                        Book.BKEY_BOOKSHELF_LIST,
                                                        CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0],
                                                        CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[1]);

    @NonNull
    private final Map<EngineId, Locale> engineLocales;
    /** Mappers to apply. */
    private final Collection<Mapper> mappers = new ArrayList<>();
    @NonNull
    private final Locale systemLocale;

    ResultsAccumulator(@NonNull final Context context,
                       @NonNull final Map<EngineId, Locale> engineLocales,
                       @NonNull final Locale systemLocale) {

        this.engineLocales = engineLocales;
        this.systemLocale = systemLocale;

        ColorMapper.create(context).ifPresent(mappers::add);
        FormatMapper.create(context).ifPresent(mappers::add);
    }

    private static void dbgLogValueCopied(@NonNull final String method,
                                          @NonNull final String key,
                                          @Nullable final Object dataToAdd) {
        LoggerFactory.getLogger().d(TAG, method, "copied",
                                    "key=" + key,
                                    "value=`" + dataToAdd + '`');
    }

    @SuppressWarnings("SameParameterValue")
    private static void dbgLogValueAppended(@NonNull final String method,
                                            @NonNull final String key,
                                            @Nullable final Object dataToAdd) {
        LoggerFactory.getLogger().d(TAG, method, "appended",
                                    "key=" + key,
                                    "value=`" + dataToAdd + '`');
    }

    private static void dbgLogValueSkipped(@NonNull final String method,
                                           @NonNull final String key,
                                           @Nullable final Object dataToAdd) {
        LoggerFactory.getLogger().d(TAG, method, "skipping",
                                    "key=" + key,
                                    "value=`" + dataToAdd + '`');
    }

    /**
     * Accumulate all data from the given sites.
     * <p>
     * NEWTHINGS: when adding a new Search task that adds non-string based data,
     *  also handle that here.
     *
     * @param context Current context
     * @param results ordered Map of engineId's and the Book we found for that engine
     * @param book    to update; this is the Book which will be returned as
     *                the final result for this search.
     */
    void process(@NonNull final Context context,
                 @NonNull final Map<EngineId, Book> results,
                 @NonNull final Book book) {
        results.forEach((engineId, result) -> {
            final Locale siteLocale = engineLocales.get(engineId);
            // Sanity check, should never be null... flw
            Objects.requireNonNull(siteLocale);

            final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
            final RealNumberParser realNumberParser = new RealNumberParser(locales);
            final DateParser dateParser = new FullDateParser(systemLocale, locales);
            result.keySet().forEach(key -> {
                if (DBKey.DATE_KEYS.contains(key)) {
                    processDate(key, result, book, dateParser);

                } else if (DBKey.MONEY_KEYS.contains(key)) {
                    processMoney(key, result, book, siteLocale, realNumberParser);

                } else if (LIST_KEYS.contains(key)) {
                    processList(key, result, book);

                } else if (DBKey.LANGUAGE.equals(key)) {
                    processLanguage(context, key, result, book, siteLocale);

                } else if (DBKey.RATING.equals(key)) {
                    processDouble(key, result, book, realNumberParser);

                } else {
                    // when we get here, we should only have String, int, or long data
                    processGenericKey(key, result, book, realNumberParser);
                }
            });
        });

        // run the mappers
        mappers.forEach(mapper -> mapper.map(context, book));

        // Pick the best covers for each list (if any) and clean/delete all others.
        CoverFileSpecArray.process(book);
    }

    /**
     * Grabs the 'new' language and checks if it's parsable.
     * If so, then check if the previous language was actually valid at all.
     * if not, use new language.
     * <p>
     * The data is always expected to be a {@code String}.
     *
     * @param context    Current context
     * @param key        Key of data
     * @param siteData   Source Bundle
     * @param book       Destination bundle
     * @param siteLocale for parsing
     */
    private void processLanguage(@NonNull final Context context,
                                 @NonNull final String key,
                                 @NonNull final Book siteData,
                                 @NonNull final Book book,
                                 @NonNull final Locale siteLocale) {
        // No new data ? we're done.
        String dataToAdd = siteData.getString(key, null);
        if (dataToAdd == null || dataToAdd.trim().isEmpty()) {
            return;
        }

        // If we already have previous data, we're done
        final String previous = book.getString(key, null);
        if (previous != null && !previous.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueSkipped("processLanguage", key, dataToAdd);
            }
            return;
        }

        // If more than 3 characters, it's likely a 'display' name of a language.
        if (dataToAdd.length() > 3) {
            dataToAdd = ServiceLocator
                    .getInstance().getLanguages()
                    .getISO3FromDisplayName(context, siteLocale, dataToAdd);
        }

        // copy the new data
        book.putString(key, dataToAdd);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            dbgLogValueCopied("processLanguage", key, dataToAdd);
        }
    }

    /**
     * Grabs the 'new' date and checks if it's parsable.
     * If so, then check if the previous date was actually valid at all.
     * if not, use new date.
     * <p>
     * The data is always expected to be a {@code String}.
     *
     * @param key        for the field
     * @param srcBook    Source Bundle
     * @param dstBook    Destination bundle
     * @param dateParser shared for the current site
     */
    private void processDate(@NonNull final String key,
                             @NonNull final Book srcBook,
                             @NonNull final Book dstBook,
                             @NonNull final DateParser dateParser) {
        // No new data ? we're done.
        final String dataToAdd = srcBook.getString(key, null);
        if (dataToAdd == null || dataToAdd.trim().isEmpty()) {
            return;
        }

        // No previous data ? Copy the new data, even if the incoming date
        // might not be valid. We'll deal with that later.
        // We do this as we WILL accept partial dates (e.g. just a year)
        final String previous = dstBook.getString(key, null);
        if (previous == null || previous.trim().isEmpty()) {
            dstBook.putString(key, dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processDate", key, dataToAdd);
            }
            return;
        }


        // FIXME: there is overlap with some SearchEngines which already do a full
        //  validity check on the dates they gather. We should avoid a double-check.
        //
        // Overwrite with the new date IF we can parse it, i.e. it's a FULL date
        // AND if the previous one was NOT a full date.
        dateParser.parse(dataToAdd).ifPresent(newDate -> {
            // URGENT: this call is using the CURRENT Locales.. but the 'previous'
            //  data is from a different site! but see below... paranoia
            if (dateParser.parse(previous).isEmpty()) {
                // previous date was invalid or partial, use the new one instead.
                dstBook.putString(key, newDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        });

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            dbgLogValueSkipped("processDate", key, dataToAdd);
        }
    }

    /**
     * Accumulate ParcelableArrayList data.
     * <p>
     * Data is always appended to any previous data.
     * There are <strong>NO CHECKS FOR DUPLICATION</strong>.
     *
     * @param <T>      type of items in the ArrayList
     * @param key      Key of data
     * @param siteData Source Bundle
     * @param book     Destination bundle
     */
    private <T extends Parcelable> void processList(@NonNull final String key,
                                                    @NonNull final Book siteData,
                                                    @NonNull final Book book) {
        final List<T> dataToAdd = siteData.getParcelableArrayList(key);
        if (dataToAdd.isEmpty()) {
            return;
        }

        final List<T> list = book.getParcelableArrayList(key);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            if (list.isEmpty()) {
                dbgLogValueCopied("processList", key, dataToAdd);
            } else {
                dbgLogValueAppended("processList", key, dataToAdd);
            }
        }

        list.addAll(dataToAdd);
        book.putParcelableArrayList(key, list);
    }

    /**
     * Accumulate price data.
     *
     * @param key              Key of data
     * @param siteData         Source Bundle
     * @param book             Destination bundle
     * @param siteLocale       for parsing
     * @param realNumberParser shared for the current site
     */
    private void processMoney(@NonNull final String key,
                              @NonNull final Book siteData,
                              @NonNull final Book book,
                              @NonNull final Locale siteLocale,
                              @NonNull final RealNumberParser realNumberParser) {
        // Fetch as Object, as engines MAY store typed data
        final Object dataToAdd = siteData.get(key, realNumberParser);
        if (dataToAdd == null || dataToAdd.toString().isEmpty()) {
            return;
        }

        // If we already have previous data, we're done
        // (fetch as String; we don't care about the actual data-type)
        final String previous = book.getString(key, null);
        if (previous != null && !previous.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueSkipped("processMoney", key, dataToAdd);
            }
            return;
        }

        // Money, double or float ? Just copy the new data.
        if (dataToAdd instanceof Money) {
            book.putMoney(key, (Money) dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processMoney", key, dataToAdd);
            }
            return;
        }
        if (dataToAdd instanceof Double || dataToAdd instanceof Float) {
            book.putDouble(key, (double) dataToAdd);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processMoney", key, dataToAdd.toString());
            }
            return;
        }

        // this is a fallback in case the SearchEngine has not already parsed the data!
        final MoneyParser moneyParser = new MoneyParser(siteLocale, realNumberParser);
        final Money money = moneyParser.parse(dataToAdd.toString());
        if (money != null) {
            book.putMoney(key, money);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processMoney", key, dataToAdd);
            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueSkipped("processMoney", key, dataToAdd);
            }
        }
    }

    /**
     * Accumulate {@code double} or {@code float} data.
     *
     * @param key              Key of data
     * @param siteData         Source Bundle
     * @param book             Destination bundle
     * @param realNumberParser shared for the current site
     */
    private void processDouble(@NonNull final String key,
                               @NonNull final Book siteData,
                               @NonNull final Book book,
                               @NonNull final RealNumberParser realNumberParser) {
        // Fetch as Object, as engines MAY store typed data
        final Object dataToAdd = siteData.get(key, realNumberParser);
        if (dataToAdd == null || dataToAdd.toString().isEmpty()) {
            return;
        }

        // If we already have previous data, we're done
        // (fetch as String; we don't care about the actual data-type)
        final String previous = book.getString(key, null);
        if (previous != null && !previous.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueSkipped("processDouble", key, dataToAdd);
            }
            return;
        }

        // double or float ? Just copy the new data.
        if (dataToAdd instanceof Float || dataToAdd instanceof Double) {
            book.putFloat(key, (float) dataToAdd);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processDouble", key, dataToAdd);
            }
            return;
        }

        //noinspection OverlyBroadCatchBlock
        try {
            // this is a fallback in case the SearchEngine has not already parsed the data!
            final float rating = realNumberParser.toFloat(dataToAdd);
            book.putFloat(key, rating);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueCopied("processRating", key, dataToAdd);
            }
        } catch (@NonNull final IllegalArgumentException e) {
            // covers NumberFormatException
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processDouble", e,
                                            "key=" + key,
                                            "data=`" + dataToAdd + '`');
            }
        }
    }

    /**
     * Accumulate generic keys not processed already.
     *
     * @param key              Key of data
     * @param siteData         Source Bundle
     * @param book             Destination bundle
     * @param realNumberParser shared for the current site
     */
    private void processGenericKey(@NonNull final String key,
                                   @NonNull final Book siteData,
                                   @NonNull final Book book,
                                   @NonNull final RealNumberParser realNumberParser) {
        // Fetch as Object, as engines MAY store typed data
        final Object dataToAdd = siteData.get(key, realNumberParser);
        if (dataToAdd == null || dataToAdd.toString().isEmpty()) {
            return;
        }

        // If we already have previous data, we're done
        // (fetch as String; we don't care about the actual data-type)
        final String previous = book.getString(key, null);
        if (previous != null && !previous.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                dbgLogValueSkipped("processGenericKey", key, dataToAdd);
            }
            return;
        }

        // Copy the data using the incoming type.
        book.put(key, dataToAdd);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            dbgLogValueCopied("processGenericKey", key, dataToAdd);
        }
    }
}
