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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
 * FIXME: 2020-03-27. Started getting "APIs Temporarily disabled" for book and cover searches.
 * Searching for alternative editions still works.
 * The code for this site is kept up to date but not tested.
 * We might have to switch the html scraping.
 * <p>
 * <p>
 * Handle all aspects of searching (and ultimately synchronizing with) LibraryThing.
 * <p>
 * The basic URLs are:
 * <p>
 * Covers via ISBN: http://covers.librarything.com/devkey/{DEVKEY}/{SIZE}/isbn/{ISBN}
 * with size: large,medium,small
 * <br>
 * <p>
 * REST api: <a href="http://www.librarything.com/services/rest/documentation/1.1/">
 * http://www.librarything.com/services/rest/documentation/1.1/</a>
 * <p>
 * Details via ISBN:
 * http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork
 * &apikey={DEVKEY}&isbn={ISBN}
 *
 * <p>
 * xml see {@link SearchCoordinator#search} header
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.CoverByIsbn,
                   SearchEngine.AlternativeEditions {

    /** Preferences prefix. */
    private static final String PREF_KEY = "librarything";

    /** Preference that contains the dev key for the user. Type: {@code String}. */
    static final String PREFS_DEV_KEY = PREF_KEY + ".dev_key";
    private static final String TAG = "LibraryThingSE";

    /**
     * book details urls.
     * <p>
     * param 1: dev-key; param 2: search-key; param 3: value
     */
    private static final String BOOK_URL =
            "/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&%2$s=%3$s";
    /** param 1: dev-key; param 2: size; param 3: isbn. */
    private static final String COVER_BY_ISBN_URL =
            "https://covers.librarything.com/devkey/%1$s/%2$s/isbn/%3$s";
    /** Can only send requests at a throttled speed. */
    private static final Throttler THROTTLER = new Throttler();
    private static final Pattern DEV_KEY_PATTERN = Pattern.compile("[\\r\\t\\n\\s]*");

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext Application context
     */
    @SuppressWarnings("WeakerAccess")
    public LibraryThingSearchEngine(@NonNull final Context appContext,
                                    final int engineId) {
        super(appContext, engineId);
    }

    public static SearchEngineRegistry.Config createConfig() {
        return new SearchEngineRegistry.Config.Builder(LibraryThingSearchEngine.class,
                                                       SearchSites.LIBRARY_THING,
                                                       R.string.site_library_thing,
                                                       PREF_KEY,
                                                       "https://www.librarything.com")
                .setSupportsMultipleCoverSizes(true)
                .setFilenameSuffix("LT")

                .setDomainKey(DBDefinitions.KEY_EID_LIBRARY_THING)
                .setDomainViewId(R.id.site_library_thing)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING)
                .build();
    }

    /**
     * external users (to this class) should call this before doing any searches.
     *
     * @param context Current context
     *
     * @return {@code true} if there is a developer key configured.
     */
    private static boolean hasKey(@NonNull final Context context) {
        final boolean hasKey = !getDevKey(context).isEmpty();
        if (BuildConfig.DEBUG && !hasKey) {
            Log.d(TAG, "hasKey|key not available");
        }
        return hasKey;
    }

    /**
     * Get the dev key.
     *
     * @param context Current context
     *
     * @return the dev key, CAN BE EMPTY but never {@code null}
     */
    @NonNull
    private static String getDevKey(@NonNull final Context context) {
        final String key = PreferenceManager.getDefaultSharedPreferences(context)
                                            .getString(PREFS_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return DEV_KEY_PATTERN.matcher(key).replaceAll("");
        }
        return "";
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format("/work/%1$s", externalId);
    }

    @Override
    public boolean isAvailable() {
        return hasKey(mAppContext);
    }

    @NonNull
    @Override
    public Throttler getThrottler() {
        return THROTTLER;
    }

    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @Nullable final String callerIdString,
                                    @Nullable final Consumer<RegistrationAction> onResult) {
        // sanity check
        if (isAvailable()) {
            return false;
        }

        return showRegistrationDialog(context, required, callerIdString, action -> {
            if (action == RegistrationAction.Register) {
                final Intent intent = new Intent(context, LibraryThingRegistrationActivity.class);
                context.startActivity(intent);

            } else if (onResult != null) {
                onResult.accept(action);
            }
        });
    }

    /**
     * Dev-key needed for this call.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BOOK_URL, getDevKey(mAppContext),
                                                        "id", externalId);
        fetchBook(url, bookData);

        if (isCancelled()) {
            return bookData;
        }

        if (fetchThumbnail[0]) {
            final String isbnStr = bookData.getString(DBDefinitions.KEY_ISBN);
            if (isbnStr != null && !isbnStr.isEmpty()) {
                final ArrayList<String> imageList = searchBestCoverImageByIsbn(isbnStr, 0);
                if (!imageList.isEmpty()) {
                    bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0],
                                                imageList);
                }
            }
        }

        return bookData;
    }

    /**
     * Dev-key needed for this call.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BOOK_URL, getDevKey(mAppContext),
                                                        "isbn", validIsbn);
        fetchBook(url, bookData);

        if (isCancelled()) {
            return bookData;
        }

        if (fetchThumbnail[0]) {
            final ArrayList<String> imageList = searchBestCoverImageByIsbn(validIsbn, 0);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0],
                                            imageList);
            }
        }

        return bookData;
    }

    /**
     * ENHANCE: See if we can get the alternate user-contributed images from LibraryThing.
     * The latter are often the best source but at present could only be obtained by HTML scraping.
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @WorkerThread
    @Override
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0, to = 1) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        final String sizeParam;
        if (size == null) {
            sizeParam = "large";
        } else {
            switch (size) {
                case Small:
                    sizeParam = "small";
                    break;
                case Medium:
                    sizeParam = "medium";
                    break;
                case Large:
                default:
                    sizeParam = "large";
                    break;
            }
        }

        final String url = String.format(COVER_BY_ISBN_URL, getDevKey(mAppContext),
                                         sizeParam, validIsbn);
        return saveImage(url, validIsbn, cIdx, size);
    }

    /**
     * Search for edition data.
     * <p>
     * No dev-key needed for this call.
     *
     * <strong>Note:</strong> we assume the isbn numbers retrieved from the site are valid.
     * No extra checks are made at this point.
     *
     * <br>{@inheritDoc}
     */
    @WorkerThread
    @NonNull
    @Override
    public List<String> searchAlternativeEditions(@NonNull final String validIsbn)
            throws IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();

        final String url = getSiteUrl() + String.format("/api/thingISBN/%1$s", validIsbn);
        try (TerminatorConnection con = createConnection(url, true)) {
            final SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
        return handler.getResult();
    }

    /**
     * Fetch a book by url.
     *
     * @param url      to fetch
     * @param bookData Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @throws IOException on failure
     */
    private void fetchBook(@NonNull final String url,
                           @NonNull final Bundle bookData)
            throws IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final LibraryThingHandler handler = new LibraryThingHandler(bookData);
//        final XmlDumpParser handler = new XmlDumpParser();

        // Get it
        try (TerminatorConnection con = createConnection(url, true)) {
            final SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            final String msg = e.getMessage();
            // Horrible hack... but once again the underlying apache class makes life difficult.
            // Sure, the Locator could be used to see that the line==1 and column==0,
            // but other than that it does not seem possible to get full details.
            if (msg != null && msg.contains("At line 1, column 0: syntax error")) {
                // 2020-03-27. Started getting "APIs Temporarily disabled"
                throw new IOException(
                        mAppContext.getString(R.string.error_network_site_has_problems));
            }

            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "fetchBook", e);
            }
            throw new IOException(e);
        }

        checkForSeriesNameInTitle(bookData);
    }
}
