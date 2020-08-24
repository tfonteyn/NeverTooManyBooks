/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;

public abstract class SearchEngineBase
        implements SearchEngine {

    @SearchSites.EngineId
    protected final int mId;

    @NonNull
    protected final Context mAppContext;

    @Nullable
    private Canceller mCaller;

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public SearchEngineBase(@NonNull final Context appContext) {
        mAppContext = appContext;

        final SearchEngine.Configuration se = getClass().getAnnotation(
                SearchEngine.Configuration.class);
        Objects.requireNonNull(se);
        mId = se.id();
    }

    /**
     * Helper method.
     * <p>
     * Look for a book title; if present try to get a Series from it and clean the book title.
     * <p>
     * This default implementation is fine for most engines but not always needed.
     * TODO: we probably call checkForSeriesNameInTitleDefaultImpl for sites that don't need it.
     * It's static so we can use it from
     * {@link com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookApiHandler}
     * until that one is converted.
     *
     * @param bookData Bundle to update
     */
    public static void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        final String fullTitle = bookData.getString(DBDefinitions.KEY_TITLE);
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
                            bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
                    if (seriesList == null) {
                        seriesList = new ArrayList<>();
                    }

                    // add to the TOP of the list. This is based on translated books/comics
                    // on Goodreads where the Series is in the original language, but the
                    // Series name embedded in the title is in the same language as the title.
                    seriesList.add(0, Series.from(seriesTitleWithNumber));

                    // store Series back
                    bookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, seriesList);
                    // and store cleansed book title back
                    bookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
                }
            }
        }
    }

    @Override
    public int getId() {
        return mId;
    }

    @NonNull
    @Override
    public Context getAppContext() {
        return mAppContext;
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
