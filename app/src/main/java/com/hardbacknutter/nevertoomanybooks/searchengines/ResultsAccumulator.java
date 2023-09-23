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
import android.graphics.BitmapFactory;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
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
                                                        Book.BKEY_FILE_SPEC_ARRAY[0],
                                                        Book.BKEY_FILE_SPEC_ARRAY[1]);

    private final Map<EngineId, SearchEngine> engineCache;
    /** Mappers to apply. */
    private final Collection<Mapper> mappers = new ArrayList<>();
    @NonNull
    private final Locale systemLocale;

    ResultsAccumulator(@NonNull final Context context,
                       @NonNull final Map<EngineId, SearchEngine> engineCache,
                       @NonNull final Locale systemLocale) {

        this.engineCache = engineCache;
        this.systemLocale = systemLocale;

        if (FormatMapper.isMappingAllowed(context)) {
            mappers.add(new FormatMapper());
        }
        if (ColorMapper.isMappingAllowed(context)) {
            mappers.add(new ColorMapper());
        }
    }

    /**
     * Accumulate all data from the given sites.
     * <p>
     * The Bundle will contain by default only String and ArrayList based data.
     * Long etc... types will be stored as String data.
     * <p>
     * NEWTHINGS: if you add a new Search task that adds non-string based data,
     * handle that here.
     *
     * @param context Current context
     * @param sites   the ordered list of engines
     * @param book    Destination bundle
     */
    void process(@NonNull final Context context,
                 @NonNull final List<EngineId> sites,
                 @NonNull final Map<EngineId, SearchCoordinator.WrappedTaskResult> searchResultsBySite,
                 @NonNull final Book book) {
        sites.forEach(engineId -> {
            final SearchCoordinator.WrappedTaskResult siteData = searchResultsBySite.get(engineId);
            if (siteData != null && siteData.result != null && !siteData.result.isEmpty()) {
                final SearchEngine searchEngine = engineCache.get(engineId);
                //noinspection DataFlowIssue
                final Locale siteLocale = searchEngine.getLocale(context);
                final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
                final RealNumberParser realNumberParser = new RealNumberParser(locales);
                final DateParser dateParser = new FullDateParser(systemLocale, locales);
                siteData.result.keySet().forEach(key -> {
                    if (DBKey.DATE_KEYS.contains(key)) {
                        processDate(key, siteData.result, book, dateParser);

                    } else if (DBKey.MONEY_KEYS.contains(key)) {
                        processMoney(key, siteData.result, book, siteLocale, realNumberParser);

                    } else if (LIST_KEYS.contains(key)) {
                        processList(key, siteData.result, book);

                    } else if (DBKey.LANGUAGE.equals(key)) {
                        processLanguage(context, key, siteData.result, book, siteLocale);

                    } else if (DBKey.RATING.equals(key)) {
                        processRating(key, siteData.result, book, realNumberParser);

                    } else {
                        // when we get here, we should only have String, int, or long data
                        processGenericKey(key, siteData.result, book, realNumberParser);
                    }
                });
            }
        });

        // run the mappers
        mappers.forEach(mapper -> mapper.map(context, book));

        // Pick the best covers for each list (if any) and clean/delete all others.
        processCovers(book);
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
                LoggerFactory.getLogger().d(TAG, "processLanguage", "skipping",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
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
            LoggerFactory.getLogger().d(TAG, "processLanguage", "copied",
                                        "key=" + key,
                                        "value=`" + dataToAdd + '`');
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
                LoggerFactory.getLogger().d(TAG, "processDate", "copied",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
            return;
        }


        // FIXME: there is overlap with some SearchEngines which already do a full
        //  validity check on the dates they gather. We should avoid a double-check.
        //
        // Overwrite with the new date IF we can parse it, i.e. it's a FULL date
        // AND if the previous one was NOT a full date.
        final LocalDateTime newDate = dateParser.parse(dataToAdd);
        if (newDate != null) {
            // URGENT: this call is using the CURRENT Locales.. but the 'previous'
            //  data is from a different site! but see below... paranoia
            if (dateParser.parse(previous) == null) {
                // previous date was invalid or partial, use the new one instead.
                dstBook.putString(key, newDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    LoggerFactory.getLogger().d(TAG, "processDate", "copied",
                                                "key=" + key,
                                                "value=`" + dataToAdd + '`');
                }
                return;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "processDate", "skipping",
                                        "key=" + key,
                                        "value=`" + dataToAdd + '`');
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
                LoggerFactory.getLogger().d(TAG, "processList", "copied",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            } else {
                LoggerFactory.getLogger().d(TAG, "processList", "appended",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
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
                LoggerFactory.getLogger().d(TAG, "processMoney", "skipping",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
            return;
        }

        // Money, double or float ? Just copy the new data.
        if (dataToAdd instanceof Money) {
            book.putMoney(key, (Money) dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processMoney", "copied",
                                            "key=" + key,
                                            "Money=`" + dataToAdd + '`');
            }
            return;
        }
        if (dataToAdd instanceof Double || dataToAdd instanceof Float) {
            book.putDouble(key, (double) dataToAdd);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processMoney", "copied",
                                            "key=" + key,
                                            "double=`" + dataToAdd + '`');
            }
            return;
        }

        // this is a fallback in case the SearchEngine has not already parsed the data!
        final MoneyParser moneyParser = new MoneyParser(siteLocale, realNumberParser);
        final Money money = moneyParser.parse(dataToAdd.toString());
        if (money != null) {
            book.putMoney(key, money);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processMoney", "copied",
                                            "key=" + key,
                                            "data=`" + dataToAdd + '`');
            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processMoney", "skipping",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
        }
    }

    /**
     * Accumulate rating data.
     *
     * @param key              Key of data
     * @param siteData         Source Bundle
     * @param book             Destination bundle
     * @param realNumberParser shared for the current site
     */
    private void processRating(@NonNull final String key,
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
                LoggerFactory.getLogger().d(TAG, "processRating", "skipping",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
            return;
        }

        // double or float ? Just copy the new data.
        if (dataToAdd instanceof Float || dataToAdd instanceof Double) {
            book.putFloat(key, (float) dataToAdd);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processRating", "copied",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
            return;
        }

        try {
            // this is a fallback in case the SearchEngine has not already parsed the data!
            final float rating = realNumberParser.toFloat(dataToAdd);
            book.putFloat(key, rating);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processRating", "copied",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
        } catch (@NonNull final IllegalArgumentException e) {
            // covers NumberFormatException
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "processRating", e,
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
                LoggerFactory.getLogger().d(TAG, "processGenericKey", "skipping",
                                            "key=" + key,
                                            "value=`" + dataToAdd + '`');
            }
            return;
        }

        // Copy the data using the incoming type.
        book.put(key, dataToAdd);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "processGenericKey", "copied",
                                        "key=" + key,
                                        "double=`" + dataToAdd + '`');
        }
    }

    /**
     * Filter the {@link Book#getCoverFileSpecList} present, selecting only the best
     * image for each index, and store those in {@link Book#BKEY_TMP_FILE_SPEC}.
     * This may result in removing ALL images if none are found suitable.
     *
     * @param book to filter
     */
    private void processCovers(@NonNull final Book book) {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            final List<String> imageList = book.getCoverFileSpecList(cIdx);
            if (!imageList.isEmpty()) {
                // ALWAYS call even if we only have 1 image...
                // We want to remove bad ones if needed.
                final String coverName = getBestImage(imageList);
                if (coverName != null) {
                    book.putString(Book.BKEY_TMP_FILE_SPEC[cIdx], coverName);
                }
            }
            book.setCoverFileSpecList(cIdx, null);
        }
    }

    /**
     * Pick the largest image from the given list, and delete all others.
     *
     * @param imageList a list of images
     *
     * @return name of cover found, or {@code null} for none.
     */
    @Nullable
    private String getBestImage(@NonNull final List<String> imageList) {

        // biggest size based on height * width
        long bestImageSize = -1;
        // index of the file which is the biggest
        int bestFileIndex = -1;

        // Just read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            final String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    final long size = (long) opt.outHeight * (long) opt.outWidth;
                    if (size > bestImageSize) {
                        bestImageSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one.
        // Note there *may* be no best one, so all would be deleted. This is fine.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                FileUtils.delete(new File(imageList.get(i)));
            }
        }

        if (bestFileIndex >= 0) {
            return imageList.get(bestFileIndex);
        }

        return null;
    }
}
