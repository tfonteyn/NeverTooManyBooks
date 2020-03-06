/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
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
 * <p>
 * ENHANCE: extend the use of LibraryThing:
 * - Lookup title using keywords: http://www.librarything.com/api/thingTitle/hand oberon
 * <p>
 * - consider scraping html for covers: http://www.librarything.com/work/18998/covers
 * with 18998 being the 'work' identifier.
 * * selector:
 * #coverlist_customcovers
 * then all 'img'
 * and use the href.
 */
public class LibraryThingSearchEngine
        implements SearchEngine,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByNativeId,
                   SearchEngine.CoverByIsbn,
                   SearchEngine.AlternativeEditions {

    /** base urls. */
    public static final String BASE_URL = "https://www.librarything.com";
    private static final String TAG = "LibraryThingSE";
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "librarything.";
    /** Preference that contains the dev key for the user. Type: {@code String}. */
    public static final String PREFS_DEV_KEY = PREF_PREFIX + "dev_key";
    /** Preference that controls display of alert about LibraryThing. */
    private static final String PREFS_HIDE_ALERT = PREF_PREFIX + "hide_alert.";
    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_LT";
    private static final String WORK_URL = BASE_URL + "/work/%1$s";
    /**
     * book details urls.
     * <p>
     * param 1: dev-key; param 2: search-key; param 3: value
     */
    private static final String BOOK_URL =
            BASE_URL + "/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&%2$s=%3$s";

    /** fetches all isbn's from editions related to the requested isbn. */
    private static final String EDITIONS_URL = BASE_URL + "/api/thingISBN/%1$s";

    /** param 1: dev-key; param 2: size; param 3: isbn. */
    private static final String COVER_BY_ISBN_URL =
            "https://covers.librarything.com/devkey/%1$s/%2$s/isbn/%3$s";

    /** Can only send requests at a throttled speed. */
    private static final Throttler THROTTLER = new Throttler();
    private static final Pattern DEV_KEY_PATTERN = Pattern.compile("[\\r\\t\\n\\s]*");

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = String.format(WORK_URL, bookId);
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * external users (to this class) should call this before doing any searches.
     *
     * @param context Current context
     *
     * @return {@code true} if there is a developer key configured.
     */
    public static boolean hasKey(@NonNull final Context context) {
        boolean hasKey = !getDevKey(context).isEmpty();
        if (BuildConfig.DEBUG && !hasKey) {
            Log.d(TAG, "hasKey|key not available");
        }
        return hasKey;
    }

    /**
     * Alert the user if not shown before that we require or would benefit from LibraryThing access.
     *
     * @param context    Current context
     * @param required   {@code true} if we <strong>must</strong> have access to LT.
     *                   {@code false} if it would be beneficial.
     * @param prefSuffix String used to flag in preferences if we showed the alert from
     *                   that caller already or not yet.
     *
     * @return {@code true} if an alert is currently shown
     */
    public static boolean alertRegistrationNeeded(@NonNull final Context context,
                                                  final boolean required,
                                                  @NonNull final String prefSuffix) {

        final String prefName = PREFS_HIDE_ALERT + prefSuffix;
        boolean showAlert;
        if (required) {
            showAlert = true;
        } else {
            showAlert = !PreferenceManager.getDefaultSharedPreferences(context)
                                          .getBoolean(prefName, false);
        }

        if (showAlert) {
            Intent intent = new Intent(context, LibraryThingRegistrationActivity.class);
            StandardDialogs.registerOnSite(context, R.string.site_library_thing,
                                           intent, required, prefName);
        }

        return showAlert;
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
        String key = PreferenceManager.getDefaultSharedPreferences(context)
                                      .getString(PREFS_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return DEV_KEY_PATTERN.matcher(key).replaceAll("");
        }
        return "";
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return Locale.US;
    }

    /**
     * Search for edition data.
     * <p>
     * No dev-key needed for this call.
     *
     * <strong>Note:</strong> we assume the isbn numbers from the site are valid.
     * No extra checks are made.
     * <p>
     * {@inheritDoc}
     */
    @WorkerThread
    @NonNull
    @Override
    public List<String> getAlternativeEditions(@NonNull final Context appContext,
                                               @NonNull final String isbn) {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        LibraryThingEditionHandler handler = new LibraryThingEditionHandler(isbn);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        String url = String.format(EDITIONS_URL, isbn);
        try (TerminatorConnection con = TerminatorConnection.open(appContext, url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);
        } catch (@NonNull final ParserConfigurationException | SAXException | IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getAlternativeEditions|e=" + e);
            }
        }
        return handler.getResult();
    }

    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String prefSuffix) {
        if (!hasKey(context)) {
            return alertRegistrationNeeded(context, required, prefSuffix);
        }
        return false;
    }

    /**
     * Dev-key needed for this call.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        String url = String.format(BOOK_URL, getDevKey(context), "isbn", validIsbn);
        Bundle bookData = fetchBook(context, url, new Bundle());

        if (fetchThumbnail[0]) {
            getCoverImage(context, validIsbn, 0, bookData);
        }

        return bookData;
    }

    /**
     * Dev-key needed for this call.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        String url = String.format(BOOK_URL, getDevKey(context), "id", nativeId);

        Bundle bookData = fetchBook(context, url, new Bundle());

        if (fetchThumbnail[0]) {
            String isbn = bookData.getString(DBDefinitions.KEY_ISBN);
            if (isbn != null && !isbn.isEmpty()) {
                getCoverImage(context, isbn, 0, bookData);
            }
        }

        return bookData;
    }

    private Bundle fetchBook(@NonNull final Context localizedAppContext,
                             @NonNull final String url,
                             @NonNull final Bundle bookData)
            throws IOException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        LibraryThingHandler handler = new LibraryThingHandler(bookData);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.open(localizedAppContext, url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);
            return handler.getResult();

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            // wrap parser exceptions in an IOException
            throw new IOException(e);
        }
    }

    /**
     * ENHANCE: See if we can get the alternate user-contributed images from LibraryThing.
     * The latter are often the best source but at present could only be obtained by HTML scraping.
     * <p>
     * {@inheritDoc}
     */
    @Nullable
    @WorkerThread
    @Override
    public String getCoverImage(@NonNull final Context context,
                                @NonNull final String isbn,
                                final int cIdx,
                                @Nullable final ImageSize size) {
        String sizeParam;
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

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();
        // ignore cIdx, site has only one image.
        String url = String.format(COVER_BY_ISBN_URL, getDevKey(context), sizeParam, isbn);
        String name = isbn + FILENAME_SUFFIX + "_" + sizeParam;
        return ImageUtils.saveImage(context, url, name);
    }

    @Override
    public boolean isAvailable(@NonNull final Context appContext) {
        return hasKey(appContext);
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return BASE_URL;
    }

    @Override
    public boolean supportsMultipleSizes() {
        return true;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.site_library_thing;
    }
}
