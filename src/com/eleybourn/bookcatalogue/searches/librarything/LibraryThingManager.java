/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.librarything;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

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
 *     http://www.librarything.com/services/rest/documentation/1.1/</a>
 * <p>
 * Details via ISBN:
 * http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork
 * &apikey={DEVKEY}&isbn={ISBN}
 *
 * <p>
 * xml see {@link com.eleybourn.bookcatalogue.searches.SearchCoordinator#search} header
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
 *
 * @author Philip Warner
 */
public class LibraryThingManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "LibraryThing.";

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

    /** param 1: devkey, param 2: size; param 3: isbn. */
    private static final String BASE_URL_COVERS
            = "https://covers.librarything.com/devkey/%1$s/%2$s/isbn/%3$s";

    /** to control access to sLastRequestTime, we synchronize on this final Object. */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /**
     * Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    @NonNull
    private static Long sLastRequestTime = 0L;

    /**
     * Constructor.
     */
    public LibraryThingManager() {
    }

    @NonNull
    public static String getBaseURL() {
        return BASE_URL;
    }

    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = getBaseURL() + "/work/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * Use sLastRequestTime to determine how long until the next request is allowed;
     * and update sLastRequestTime this needs to be synchronized across threads.
     * <p>
     * Note that as a result of this approach sLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
     * This method will sleep() until it can make a request; if 10 threads call this
     * simultaneously, one will return immediately, one will return 1 second later,
     * another two seconds etc.
     */
    private static void waitUntilRequestAllowed() {
        long now = System.currentTimeMillis();
        long wait;
        synchronized (LAST_REQUEST_TIME_LOCK) {
            wait = 1_000 - (now - sLastRequestTime);
            //
            // sLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            //
            if (wait < 0) {
                wait = 0;
            }
            sLastRequestTime = now + wait;
        }

        if (wait > 0) {
            try {
                Log.d("LT", "wait=" + wait);
                Thread.sleep(wait);
            } catch (@NonNull final InterruptedException ignored) {
            }
        }
    }


    /**
     * Check if we have a key; if not alert the user.
     *
     * @param context    Current context
     * @param required   {@code true} if we must have access to LT.
     *                   {@code false} it it would be beneficial.
     * @param prefSuffix String used to flag in preferences if we showed the alert from
     *                   that caller already or not yet.
     */
    public static void showLtAlertIfNecessary(@NonNull final Context context,
                                              final boolean required,
                                              @NonNull final String prefSuffix) {
        if (LibraryThingManager.noKey()) {
            needLibraryThingAlert(context, required, prefSuffix);
        }
    }

    /**
     * Alert the user if not shown before that we require or would benefit from LT access.
     *
     * @param context    Current context
     * @param required   {@code true} if we must have access to LT.
     *                   {@code false} it it would be beneficial.
     * @param prefSuffix String used to flag in preferences if we showed the alert from
     *                   that caller already or not yet.
     */
    public static void needLibraryThingAlert(@NonNull final Context context,
                                             final boolean required,
                                             @NonNull final String prefSuffix) {

        boolean showAlert;
        @StringRes
        int msgId;
        final String prefName = PREFS_HIDE_ALERT + prefSuffix;
        if (required) {
            msgId = R.string.lt_required_info;
            showAlert = true;
        } else {
            msgId = R.string.lt_uses_info;
            showAlert = !App.getPrefs().getBoolean(prefName, false);
        }

        if (!showAlert) {
            return;
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.lt_registration_title)
                .setMessage(msgId)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_more_info, (d, which) -> {
                    Intent intent = new Intent(context, LibraryThingAdminActivity.class);
                    context.startActivity(intent);
                    d.dismiss();
                })
                .create();

        if (!required) {
            dialog.setButton(
                    DialogInterface.BUTTON_NEUTRAL,
                    context.getString(R.string.btn_disable_message),
                    (d, which) -> {
                        App.getPrefs().edit().putBoolean(prefName, true).apply();
                        d.dismiss();
                    });
        }

        dialog.show();
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.LIBRARY_THING_MANAGER) {
            Logger.debug(LibraryThingManager.class, "searchEditions", "isbn=" + isbn);
        }

        // add the original isbn, as there might be more images at the time this search is done.
        editions.add(isbn);

        // Base path for an Editions search
        String url = String.format(EDITIONS_URL, isbn);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchLibraryThingEditionHandler handler = new SearchLibraryThingEditionHandler(editions);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (@NonNull final ParserConfigurationException | SAXException | IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(LibraryThingManager.class, e);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.LIBRARY_THING_MANAGER) {
            Logger.debug(LibraryThingManager.class, "searchEditions", "editions=" + editions);
        }
        return editions;
    }

    /**
     * external users (to this class) should call this before doing any searches.
     *
     * @return {@code true} if there is no dev key configured.
     */
    public static boolean noKey() {
        boolean noKey = getDevKey().isEmpty();
        if (noKey) {
            Logger.warn(LibraryThingManager.class, "noKey", "LibraryThing key not available");
        }
        return noKey;
    }

    /**
     * @return the dev key, CAN BE EMPTY but won't be {@code null}
     */
    @NonNull
    private static String getDevKey() {
        String key = App.getPrefs().getString(PREFS_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return key.replaceAll("[\\r\\t\\n\\s]*", "");
        }
        return "";
    }

    /**
     * dev-key needed for this call.
     *
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or {@code null} if none found (or any other failure)
     */
    @Nullable
    @WorkerThread
    @Override
    public File getCoverImage(@NonNull final String isbn,
                              @Nullable final ImageSizes size) {

        // sanity check
        if (noKey()) {
            return null;
        }
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        String sizeParam;
        if (size == null) {
            sizeParam = "L";
        } else {
            switch (size) {
                case SMALL:
                    sizeParam = "small";
                    break;
                case MEDIUM:
                    sizeParam = "medium";
                    break;
                case LARGE:
                    sizeParam = "large";
                    break;

                default:
                    sizeParam = "large";
                    break;
            }
        }

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Fetch, then save it with a suffix
        String url = String.format(BASE_URL_COVERS, getDevKey(), sizeParam, isbn);
        String fileSpec = ImageUtils.saveImage(url, isbn, FILENAME_SUFFIX + '_' + size);
        if (fileSpec != null) {
            return new File(fileSpec);
        }

        return null;
    }

    /**
     * Dev-key needed for this call.
     * <br>Only the ISBN is supported.
     *
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
    public Bundle search(@Nullable final String isbn,
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
        SearchLibraryThingHandler handler = new SearchLibraryThingHandler(bookData);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException(e);
        }

        if (fetchThumbnail) {
            File file = getCoverImage(isbn, SearchEngine.ImageSizes.LARGE);
            if (file == null) {
                file = getCoverImage(isbn, SearchEngine.ImageSizes.MEDIUM);
                if (file == null) {
                    file = getCoverImage(isbn, SearchEngine.ImageSizes.SMALL);
                }
            }
            if (file != null) {
                ArrayList<String> imageList =
                        bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(file.getAbsolutePath());
                bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
            }
        }

        return bookData;
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return !noKey() && NetworkUtils.isAlive(getBaseURL());
    }

    @Override
    public boolean isIsbnOnly() {
        return true;
    }

    @Override
    public boolean siteSupportsMultipleSizes() {
        return true;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.library_thing;
    }
}
