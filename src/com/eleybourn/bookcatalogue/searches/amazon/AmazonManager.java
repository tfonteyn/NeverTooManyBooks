package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public final class AmazonManager {

    private static final String TAG = "Amazon.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    private AmazonManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "http://www.amazon.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * FIXME: Search proxies through theagiledirector.com - amazon not supporting mobile devices
     *
     * @param isbn The ISBN to search for
     */
    public static void search(@NonNull final String isbn,
                              @NonNull final String author,
                              @NonNull final String title,
                              @NonNull final Bundle /* out*/ bookData,
                              final boolean fetchThumbnail)
            throws IOException {

        String urlText = "https://bc.theagiledirector.com/getRest_v3.php";
        if (!isbn.isEmpty()) {
            // sanity check
            if (!IsbnUtils.isValid(isbn)) {
                return;
            }
            urlText += "?isbn=" + isbn;
        } else {
            // sanity check
            if (author.isEmpty() && title.isEmpty()) {
                return;
            }
            //replace spaces in author/title with %20
            urlText += "?author=" + author.replace(" ", "%20")
                    + "&title=" + title.replace(" ", "%20");
        }

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

        // Get it
        try {
            URL url = new URL(urlText);
            SAXParser parser = factory.newSAXParser();
            parser.parse(NetworkUtils.getInputStreamWithTerminator(url), handler);

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }
    }
}
