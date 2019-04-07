package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.net.ParseException;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * ENHANCE: Get editions via.
 * http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300
 */
public final class GoogleBooksManager
        implements SearchSites.SearchSiteManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "GoogleBooks.";

    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";

    /**
     * Constructor.
     */
    public GoogleBooksManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return App.getPrefs().getString(PREFS_HOST_URL, "https://books.google.com");
    }

    /**
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or null when none found (or any other failure)
     */
    @Nullable
    @Override
    @WorkerThread
    public File getCoverImage(@NonNull final String isbn,
                              @Nullable final SearchSites.ImageSizes size) {

        return SearchSites.getCoverImageFallback(this, isbn);
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    @Override
    public boolean isIsbnOnly() {
        return false;
    }

    @Override
    public boolean supportsImageSize(@NonNull final SearchSites.ImageSizes size) {
        // support 1 size only
        return SearchSites.ImageSizes.LARGE.equals(size);
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_google_books;
    }

    /**
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return bundle with book data
     *
     * @throws IOException on failure
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final String isbn,
                         @NonNull final String author,
                         @NonNull final String title,
                         final boolean fetchThumbnail)
            throws IOException {

        Bundle bookData = new Bundle();

        String url = getBaseURL() + "/books/feeds/volumes";
        if (!isbn.isEmpty()) {
            // sanity check
            if (!ISBN.isValid(isbn)) {
                return bookData;
            }
            url += "?q=ISBN%3C" + isbn + "%3E";
        } else {
            // sanity check
            if (author.isEmpty() && title.isEmpty()) {
                return bookData;
            }
            //replace spaces in author/title with %20
            url += "?q=" + "intitle%3A" + title.replace(" ", "%20")
                    + "%2Binauthor%3A" + author.replace(" ", "%20");
        }

        // Setup the parser; the handler can return multiple books ('entry' elements)
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
        // The entry handler takes care of individual entries
        SearchGoogleBooksEntryHandler entryHandler =
                new SearchGoogleBooksEntryHandler(bookData, fetchThumbnail);

        // yes, 'try' nesting makes this ugly to read,... but the code is very clean.
        try {
            // get the book list
            try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
                SAXParser parser = factory.newSAXParser();
                parser.parse(con.inputStream, handler);
            }

            ArrayList<String> urlList = handler.getUrlList();
            if (!urlList.isEmpty()) {
                // only using the first one found, maybe future enhancement?
                url = urlList.get(0);

                try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
                    SAXParser parser = factory.newSAXParser();
                    parser.parse(con.inputStream, entryHandler);
                }
            }
            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | ParseException | SAXException e) {
            if (BuildConfig.DEBUG /* always log */) {
                Logger.debug(e);
            }
        }

        return bookData;
    }
}
