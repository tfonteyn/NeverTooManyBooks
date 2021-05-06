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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig mConfig;
    @Nullable
    private Canceller mCaller;

    /**
     * Constructor.
     *
     * @param config the search engine configuration
     */
    public SearchEngineBase(@NonNull final SearchEngineConfig config) {
        mConfig = config;
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

    @SearchSites.EngineId
    @Override
    public int getId() {
        return mConfig.getEngineId();
    }

    @NonNull
    @Override
    public Context getContext() {
        return ServiceLocator.getLocalizedAppContext();
    }

    @NonNull
    @Override
    public SearchEngineConfig getConfig() {
        return mConfig;
    }

    @NonNull
    @Override
    public String getName() {
        return getContext().getString(mConfig.getLabelId());
    }

    @NonNull
    @Override
    public String getSiteUrl() {
        return mConfig.getSiteUrl();
    }

    @NonNull
    @Override
    public Locale getLocale() {
        return mConfig.getLocale();
    }

    @Override
    public void setCaller(@Nullable final Canceller caller) {
        mCaller = caller;
    }

    @Override
    public boolean isCancelled() {
        // mCaller being null should only happen when we check if we're cancelled
        // before a task was started.
        return mCaller == null || mCaller.isCancelled();
    }
}
