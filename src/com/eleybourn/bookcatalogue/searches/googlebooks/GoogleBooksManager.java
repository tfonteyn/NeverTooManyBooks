package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.net.ParseException;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * ENHANCE: Get editions via.
 * http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300
 */
public final class GoogleBooksManager {

    private static final String TAG = "GoogleBooks.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    private GoogleBooksManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "http://books.google.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }

    /**
     * @param isbn for book cover to find
     *
     * @return found/saved File, or null when none found (or any other failure)
     */
    @Nullable
    public static File getCoverImage(@NonNull final String isbn) {
        // sanity check
        if (!IsbnUtils.isValid(isbn)) {
            return null;
        }

        Bundle bookData = new Bundle();
        try {
            // no specific API, just go search the book
            search(isbn, "", "", bookData, true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                File found = new File(imageList.get(0));
                File coverFile = new File(found.getAbsolutePath() + '_' + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            }
        } catch (IOException e) {
            Logger.error(e, "Error getting thumbnail from Google");
        }
        return null;
    }

    public static void search(@NonNull final String isbn,
                              @NonNull final String author,
                              @NonNull final String title,
                              @NonNull final Bundle /* out */ book,
                              final boolean fetchThumbnail)
            throws IOException {

        String urlText = getBaseURL() + "/books/feeds/volumes";
        if (!isbn.isEmpty()) {
            // sanity check
            if (!IsbnUtils.isValid(isbn)) {
                return;
            }
            urlText += "?q=ISBN%3C" + isbn + "%3E";
        } else {
            // sanity check
            if (author.isEmpty() && title.isEmpty()) {
                return;
            }
            //replace spaces in author/title with %20
            urlText += "?q=" + "intitle%3A" + title.replace(" ", "%20")
                    + "%2Binauthor%3A" + author.replace(" ", "%20");
        }

        // Setup the parser; the handler can return multiple books.
        // The entry handler takes care of them
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
        SearchGoogleBooksEntryHandler entryHandler =
                new SearchGoogleBooksEntryHandler(book, fetchThumbnail);

        try {
            URL url = new URL(urlText);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);

            ArrayList<String> urlList = handler.getUrlList();
            if (urlList.size() > 0) {
                // only using the first one found, maybe future enhancement?
                urlText = urlList.get(0);
                url = new URL(urlText);
                parser = factory.newSAXParser();
                parser.parse(Utils.getInputStreamWithTerminator(url), entryHandler);
            }

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | ParseException | SAXException e) {
            Logger.error(e);
        }
    }
}
