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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig mConfig;

    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean mCancelled = new AtomicBoolean();
    @NonNull
    private final ImageDownloader mImageDownloader;
    @Nullable
    private Cancellable mCaller;

    /**
     * Constructor.
     *
     * @param config the search engine configuration
     */
    public SearchEngineBase(@NonNull final SearchEngineConfig config) {
        mConfig = config;
        mImageDownloader = new ImageDownloader(createFutureGetRequest());
    }

    /**
     * Helper method.
     * <p>
     * Look for a book title; if present try to get a Series from it and clean the book title.
     * <p>
     * This default implementation is fine for most engines but not always needed.
     * TODO: we probably call checkForSeriesNameInTitle for sites that don't need it.
     *
     * @param bookData Bundle to update
     */
    public static void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        final String fullTitle = bookData.getString(DBKey.KEY_TITLE);
        if (fullTitle != null) {
            final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                // the cleansed title
                final String bookTitle = matcher.group(1);
                // the series title/number
                final String seriesTitleWithNumber = matcher.group(2);

                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    // we'll add to, or create the Series list
                    ArrayList<Series> seriesList =
                            bookData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
                    if (seriesList == null) {
                        seriesList = new ArrayList<>();
                    }

                    // add to the TOP of the list.
                    seriesList.add(0, Series.from(seriesTitleWithNumber));

                    // store Series back
                    bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, seriesList);
                    // and store cleansed book title back
                    bookData.putString(DBKey.KEY_TITLE, bookTitle);
                }
            }
        }
    }

    @NonNull
    @Override
    public SearchEngineConfig getConfig() {
        return mConfig;
    }

    /**
     * Derive the Locale from the actual url.
     * <p>
     * Sites which support multiple countries, should overwrite {@link #getLocale(Context)} with
     * {@code getLocale(context, getHostUrl()); }
     *
     * @param context Current context
     * @param baseUrl to digest
     *
     * @return Locale matching the url root domain
     */
    @NonNull
    protected Locale getLocale(@NonNull final Context context,
                               @NonNull final String baseUrl) {

        final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
        switch (root) {
            case "com":
                return Locale.US;

            case "uk":
                // country code is GB (july 2020: for now...)
                return Locale.UK;

            default:
                // other sites are (should be ?) just the country code.
                final Locale locale = ServiceLocator.getInstance().getAppLocale()
                                                    .getLocale(context, root);
                if (BuildConfig.DEBUG /* always */) {
                    Logger.d(TAG, "getLocale", "locale=" + locale);
                }
                return locale != null ? locale : Locale.US;
        }
    }


    //FIXME: Potentially unsafe 'if != null then cancel'
    @AnyThread
    @Override
    public void cancel() {
        mCancelled.set(true);
        synchronized (mImageDownloader) {
            mImageDownloader.cancel();
        }
    }

    @Override
    public void setCaller(@Nullable final Cancellable caller) {
        mCaller = caller;
        mCancelled.set(false);
    }

    @Override
    public boolean isCancelled() {
        // mCaller being null should only happen when we check if we're cancelled
        // before a search was started.
        return mCancelled.get() || mCaller == null || mCaller.isCancelled();
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param url    Image file URL
     * @param bookId more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx   0..n image index
     * @param size   (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code null} on failure
     *
     * @throws StorageException The covers directory is not available
     */
    @WorkerThread
    @Nullable
    public String saveImage(@NonNull final String url,
                            @Nullable final String bookId,
                            @IntRange(from = 0, to = 1) final int cIdx,
                            @Nullable final ImageFileInfo.Size size)
            throws StorageException {

        final File tmpFile = mImageDownloader.getTempFile(getConfig().getFilenameSuffix(),
                                                          bookId, cIdx, size);
        final File file = mImageDownloader.fetch(url, tmpFile);

        return file != null ? file.getAbsolutePath() : null;
    }
}
