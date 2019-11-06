/*
 * @Copyright 2019 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
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
public class LibraryThingManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "librarything.";

    /** Preference that contains the dev key for the user. Type: {@code String}. */
    public static final String PREFS_DEV_KEY = PREF_PREFIX + "dev_key";

    /** Preference that controls display of alert about LibraryThing. */
    public static final String PREFS_HIDE_ALERT = PREF_PREFIX + "hide_alert.";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_LT";

    /** base urls. */
    private static final String BASE_URL = "https://www.librarything.com";
    /** book details urls. */
    private static final String DETAIL_URL =
            BASE_URL + "/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&isbn=%2$s";

    /** fetches all isbn's from editions related to the requested isbn. */
    private static final String EDITIONS_URL = BASE_URL + "/api/thingISBN/%s";

    /** param 1: dev key, param 2: size; param 3: isbn. */
    private static final String BASE_URL_COVERS
            = "https://covers.librarything.com/devkey/%1$s/%2$s/isbn/%3$s";

    /** Can only send requests at a throttled speed. */
    @NonNull
    private static final Throttler THROTTLER = new Throttler();
    private static final Pattern DEV_KEY_PATTERN = Pattern.compile("[\\r\\t\\n\\s]*");

    /**
     * Constructor.
     */
    public LibraryThingManager() {
    }

    @NonNull
    public static String getBaseURL(@NonNull final Context context) {
        return BASE_URL;
    }

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = getBaseURL(context) + "/work/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * Check if we have a key; if not alert the user.
     *
     * @param context    Current context
     * @param required   {@code true} if we must have access to LT.
     *                   {@code false} it it would be beneficial.
     * @param prefSuffix String used to flag in preferences if we showed the alert from
     *                   that caller already or not yet.
     *
     * @return {@code true} if an alert is currently shown
     */
    public static boolean alertRegistrationBeneficial(@NonNull final Context context,
                                                      final boolean required,
                                                      @NonNull final String prefSuffix) {
        if (!hasKey()) {
            return alertRegistrationNeeded(context, required, prefSuffix);
        }
        return false;
    }

    /**
     * Alert the user if not shown before that we require or would benefit from LibraryThing access.
     *
     * @param context    Current context
     * @param required   {@code true} if we must have access to LT.
     *                   {@code false} it it would be beneficial.
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
            StandardDialogs.registrationDialog(context, R.string.library_thing,
                                               intent, required, prefName);
        }

        return showAlert;
    }

    /**
     * Search for edition data.
     * <p>
     * No dev-key needed for this call.
     *
     * @param isbn to lookup. Must be a valid ISBN
     *
     * @return a list of isbn's of alternative editions of our original isbn, can be empty.
     */
    @NonNull
    public static ArrayList<String> searchEditions(@NonNull final String isbn) {

        // the resulting data we'll return
        ArrayList<String> editions = new ArrayList<>();

        // sanity check
        if (!ISBN.isValid(isbn)) {
            return editions;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.LIBRARY_THING) {
            Logger.debug(LibraryThingManager.class, "searchEditions", "isbn=" + isbn);
        }

        // add the original isbn, as there might be more images at the time this search is done.
        editions.add(isbn);

        // Base path for an Editions search
        String url = String.format(EDITIONS_URL, isbn);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        LibraryThingEditionHandler handler = new LibraryThingEditionHandler(editions);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (@NonNull final ParserConfigurationException | SAXException | IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(LibraryThingManager.class, e, url);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.LIBRARY_THING) {
            Logger.debug(LibraryThingManager.class, "searchEditions", "editions=" + editions);
        }
        return editions;
    }

    /**
     * external users (to this class) should call this before doing any searches.
     *
     * @return {@code true} if there is a developer key configured.
     */
    public static boolean hasKey() {
        boolean hasKey = !getDevKey().isEmpty();
        if (BuildConfig.DEBUG && !hasKey) {
            Logger.debug(LibraryThingManager.class, "hasKey", "LibraryThing key not available");
        }
        return hasKey;
    }

    /**
     * @return the dev key, CAN BE EMPTY but never {@code null}
     */
    @NonNull
    private static String getDevKey() {
        String key = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                      .getString(PREFS_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return DEV_KEY_PATTERN.matcher(key).replaceAll("");
        }
        return "";
    }

    @SuppressWarnings("unused")
    static void resetTips(@NonNull final Context context) {
        TipManager.reset(context, PREFS_HIDE_ALERT);
    }

    /**
     * Dev-key needed for this call.
     * <br>Only the ISBN is supported.
     *
     * @param context   Current context (i.e. with the current Locale)
     * @param isbn      to lookup. Must be a valid ISBN
     * @param author    unused
     * @param title     unused
     * @param publisher unused
     *                  <br>
     *                  <br>{@inheritDoc}
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final Context context,
                         @Nullable final String isbn,
                         @Nullable final /* not supported */ String author,
                         @Nullable final /* not supported */ String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        // sanity check
        if (!ISBN.isValid(isbn)) {
            return new Bundle();
        }

        // Base path for an ISBN search
        String url = String.format(DETAIL_URL, getDevKey(), isbn);

        Bundle bookData = new Bundle();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        LibraryThingHandler handler = new LibraryThingHandler(bookData);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(this, e, url);
            }
            throw new IOException(e);
        }

        if (fetchThumbnail) {
            getCoverImage(context, isbn, bookData);
        }

        return bookData;
    }

    @Nullable
    @WorkerThread
    @Override
    public File getCoverImage(@NonNull final Context context,
                              @NonNull final String isbn,
                              @Nullable final ImageSize size) {

        // sanity check
        if (!hasKey()) {
            return null;
        }
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

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

        // Fetch, then save it with a suffix
        String coverUrl = String.format(BASE_URL_COVERS, getDevKey(), sizeParam, isbn);
        String fileSpec = ImageUtils.saveImage(coverUrl, isbn, FILENAME_SUFFIX, sizeParam);
        if (fileSpec != null) {
            return new File(fileSpec);
        }

        return null;
    }

    @Override
    public boolean isAvailable() {
        return hasKey();
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return getBaseURL(context);
    }

    @Override
    public boolean requiresIsbn() {
        return true;
    }

    @Override
    public boolean hasMultipleSizes() {
        return true;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.library_thing;
    }
}
