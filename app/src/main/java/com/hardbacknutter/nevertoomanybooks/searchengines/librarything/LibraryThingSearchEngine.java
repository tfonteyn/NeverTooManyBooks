/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

/**
 * LibraryThing is 40% owned by AbeBooks which is owned by Amazon.
 * 2024-04-24: the alternative editions is now also down.
 * 2023-01-14: the website now publicly states the API is permanently down.
 * There is also a captcha for login, so switching to JSoup style html scraping is out.
 * So as far as search/sync is concerned, this site is dead.
 * <p>
 * <a href="https://www.librarything.com/services/">LibraryThing API</a>
 * <p>
 * We can still open a "work" link to books on the site for which we previously stored a native id.
 *   The LT id can also be gathered from other sites (e.g. OpenLibrary)
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ViewBookByExternalId {

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public LibraryThingSearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getHostUrl(context));
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final Context context,
                                   @NonNull final String externalId) {
        return getHostUrl(context) + String.format("/work/%1$s", externalId);
    }
}
