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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Handle all aspects of searching (and ultimately synchronizing with) LibraryThing.
 *
 * The basic URLs are:
 *
 * Covers via ISBN: http://covers.librarything.com/devkey/<DEVKEY>/large/isbn/<ISBN>
 *
 *
 * REST api: http://www.librarything.com/services/rest/documentation/1.1/
 *
 * Details via ISBN: http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork&apikey=<DEVKEY>&isbn=<ISBN>
 *
 * xml see {@link #search} header
 *
 * ENHANCE: extend the use of LibraryThing:
 * - Lookup title using keywords: http://www.librarything.com/api/thingTitle/hand oberon
 *
 * - consider scraping html for covers: http://www.librarything.com/work/18998/covers
 * with 18998 being the 'work' identifier.
 * * selector:
 * #coverlist_customcovers
 * then all 'img'
 * and use the href.
 *
 * @author Philip Warner
 */
public class LibraryThingManager {
    /** Name of preference that controls display of alert about LibraryThing */
    public static final String PREFS_LT_HIDE_ALERT = "lt_hide_alert";

    /** file suffix for cover files */
    public static final String FILENAME_SUFFIX = "_LT";

    /** Name of preference that contains the dev key for the user */
    static final String PREFS_LT_DEV_KEY = "lt_devkey";

    /** base urls */
    private static final String BASE_URL = "https://www.librarything.com";
    private static final String BASE_URL_COVERS = "https://covers.librarything.com";
    /** book details urls */
    private static final String DETAIL_URL = BASE_URL + "/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&isbn=%2$s";
    /** fetches all isbn's from editions related to the requested isbn */
    private static final String EDITIONS_URL = BASE_URL + "/api/thingISBN/%s";
    /** cover size specific urls */
    private static final String COVER_URL_LARGE = BASE_URL_COVERS + "/devkey/%1$s/large/isbn/%2$s";
    private static final String COVER_URL_MEDIUM = BASE_URL_COVERS + "/devkey/%1$s/medium/isbn/%2$s";
    private static final String COVER_URL_SMALL = BASE_URL_COVERS + "/devkey/%1$s/small/isbn/%2$s";

    /** to control access to mLastRequestTime, we synchronize on this final Object */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /**
     * Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    @NonNull
    private static Long mLastRequestTime = 0L;

    public LibraryThingManager() {
    }

    @NonNull
    public static String getBaseURL() {
        return BASE_URL;
    }

    /**
     * Use mLastRequestTime to determine how long until the next request is allowed; and
     * update mLastRequestTime this needs to be synchronized across threads.
     *
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     *
     * This method will sleep() until it can make a request; if ten threads call this
     * simultaneously, one will return immediately, one will return 1 second later, another
     * two seconds etc.
     */
    private static void waitUntilRequestAllowed() {
        long now = System.currentTimeMillis();
        long wait;
        synchronized (LAST_REQUEST_TIME_LOCK) {
            wait = 1000 - (now - mLastRequestTime);
            //
            // mLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            //
            if (wait < 0) {
                wait = 0;
            }
            mLastRequestTime = now + wait;
        }

        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {
            }
        }
    }


    public static void showLtAlertIfNecessary(final @NonNull Context context,
                                              final boolean always,
                                              final @NonNull String prefSuffix) {
        LibraryThingManager ltm = new LibraryThingManager();
        if (!ltm.isAvailable()) {
            needLibraryThingAlert(context, always, prefSuffix);
        }
    }

    public static void needLibraryThingAlert(final @NonNull Context context,
                                             final boolean required,
                                             final @NonNull String prefSuffix) {

        final SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();

        boolean showAlert;
        @StringRes
        int msgId;
        final String prefName = PREFS_LT_HIDE_ALERT + "_" + prefSuffix;
        if (required) {
            msgId = R.string.lt_required_info;
            showAlert = true;
        } else {
            msgId = R.string.lt_uses_info;
            showAlert = !prefs.getBoolean(prefName, false);
        }

        if (!showAlert)
            return;

        final AlertDialog dialog = new AlertDialog.Builder(context).setMessage(msgId)
                .setTitle(R.string.lt_registration_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.btn_more_info),
                new DialogInterface.OnClickListener() {
                    public void onClick(final @NonNull DialogInterface dialog, final int which) {
                        Intent i = new Intent(context, LibraryThingAdminActivity.class);
                        context.startActivity(i);
                        dialog.dismiss();
                    }
                });

        if (!required) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.btn_disable_message),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final @NonNull DialogInterface dialog, final int which) {
                            SharedPreferences.Editor ed = prefs.edit();
                            ed.putBoolean(prefName, true);
                            ed.apply();
                            dialog.dismiss();
                        }
                    });
        }

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final @NonNull DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    /**
     * Search for edition data.
     *
     * No dev-key needed for this call.
     * isbn must be valid.
     *
     * @return a list of isbn's of alternative editions of our original isbn
     */
    @NonNull
    public static List<String> searchEditions(final @NonNull String isbn) {

        // the resulting data we'll return
        List<String> editions = new ArrayList<>();

        // sanity check
        if (!IsbnUtils.isValid(isbn)) {
            return editions;
        }

        // add the original isbn, as there might be more images at the time this search is done.
        editions.add(isbn);

        // Base path for an Editions search
        String urlText = String.format(EDITIONS_URL, isbn);

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchLibraryThingEditionHandler handler = new SearchLibraryThingEditionHandler(editions);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get it
        try {
            URL url = new URL(urlText);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);

            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (ParserConfigurationException | SAXException | IOException  e) {
            Logger.error(e);
        }

        return editions;
    }

    /**
     * dev-key needed for this call
     * isbn must be valid.
     *
     * @param isbn for book cover to find
     * @param size the LT {@link ImageSizes} size to get
     *
     * @return found/saved File, or null when none found (or any other failure)
     */
    @Nullable
    public File getCoverImage(final @NonNull String isbn, final @NonNull ImageSizes size) {

        // sanity check
        if (!isAvailable()) {
            return null;
        }
        // sanity check
        if (!IsbnUtils.isValid(isbn)) {
            return null;
        }

        String path;
        switch (size) {
            case SMALL:
                path = COVER_URL_SMALL;
                break;
            case MEDIUM:
                path = COVER_URL_MEDIUM;
                break;
            case LARGE:
                path = COVER_URL_LARGE;
                break;
            default:
                path = COVER_URL_SMALL;
                break;
        }
        String url = String.format(path, getDevKey(), isbn);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Fetch, then save it with a suffix
        String fileSpec = ImageUtils.saveThumbnailFromUrl(url, FILENAME_SUFFIX + "_" + isbn + "_" + size);
        if (fileSpec != null) {
            return new File(fileSpec);
        }

        return null;
    }

    /**
     * dev-key needed for this call
     * isbn must be valid.
     *
     * @param isbn     to lookup
     * @param bookData Bundle to save results in
     *
     * @throws IOException on failure to search
     */
    void search(final @NonNull String isbn,
                final @NonNull Bundle /* out */ bookData,
                final boolean fetchThumbnail) throws IOException {

        // sanity check
        if (!isAvailable()) {
            return;
        }
        // sanity check
        if (!IsbnUtils.isValid(isbn)) {
            return;
        }

        // Base path for an ISBN search
        String urlText = String.format(DETAIL_URL, getDevKey(), isbn);

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchLibraryThingHandler handler = new SearchLibraryThingHandler(bookData);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get it
        try {
            URL url = new URL(urlText);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }

        if (fetchThumbnail) {
            File file = getCoverImage(isbn, ImageSizes.LARGE);
            if (file != null) {
                StringList.addOrAppend(bookData, UniqueId.BKEY_THUMBNAIL_FILE_SPEC, file.getAbsolutePath());
            }
        }
    }

    /**
     * external users (to this class) should call this before doing any searches
     *
     * @return <tt>true</tt>if there is a non-empty dev key
     */
    public boolean isAvailable() {
        boolean result = !getDevKey().isEmpty();
        Logger.info(this, "LT dev key not available");
        return result;
    }

    /**
     * @return the dev key, CAN BE EMPTY but won't be null
     */
    @NonNull
    private String getDevKey() {
        String key = BookCatalogueApp.getStringPreference(PREFS_LT_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return key.replaceAll("[\\r\\t\\n\\s]*", "");
        }
        return "";
    }

    // Sizes of thumbnails
    public enum ImageSizes {
        SMALL, MEDIUM, LARGE
    }

}
