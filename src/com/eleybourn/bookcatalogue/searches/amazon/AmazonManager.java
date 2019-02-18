package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public final class AmazonManager
        implements SearchSites.SearchSiteManager {

    private static final String TAG = "Amazon.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    /**
     * Constructor.
     */
    public AmazonManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "https://www.amazon.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * FIXME: Search proxies through theagiledirector.com - amazon not supporting mobile devices
     *
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return bundle with book data
     *
     * @throws IOException on failure
     */
    @Override
    @NonNull
    @WorkerThread
    public Bundle search(@NonNull final String isbn,
                         @NonNull final String author,
                         @NonNull final String title,
                         final boolean fetchThumbnail)
            throws IOException {

        Bundle bookData = new Bundle();

        String url = "https://bc.theagiledirector.com/getRest_v3.php";
        if (!isbn.isEmpty()) {
            // sanity check
            if (!IsbnUtils.isValid(isbn)) {
                return bookData;
            }
            url += "?isbn=" + isbn;
        } else {
            // sanity check
            if (author.isEmpty() && title.isEmpty()) {
                return bookData;
            }
            //replace spaces in author/title with %20
            url += "?author=" + author.replace(" ", "%20")
                    + "&title=" + title.replace(" ", "%20");
        }

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

        // Get it
        try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }

        return bookData;
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
}
