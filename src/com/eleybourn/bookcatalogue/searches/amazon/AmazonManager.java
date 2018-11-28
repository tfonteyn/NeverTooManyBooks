package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class AmazonManager {

    private static final String PREFS_HOST_URL = "AmazonManager.hostUrl";

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BookCatalogueApp.getStringPreference(PREFS_HOST_URL, "http://www.amazon.com");
    }

    public static void setBaseURL(final @NonNull String url) {
        BookCatalogueApp.getSharedPreferences().edit().putString(PREFS_HOST_URL, url).apply();
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * TOMF FIXME if we can ? Search proxies through theagiledirector.com due to amazon not supporting mobile devices
     *
     * @param isbn The ISBN to search for
     */
    public static void search(final @NonNull String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              final @NonNull Bundle /* out*/ bookData,
                              final boolean fetchThumbnail) throws IOException {

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
            urlText += "?author=" + author.replace(" ", "%20") + "&title=" + title.replace(" ", "%20");
        }

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

        // Get it
        try {
            URL url = new URL(urlText);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }
    }
}
