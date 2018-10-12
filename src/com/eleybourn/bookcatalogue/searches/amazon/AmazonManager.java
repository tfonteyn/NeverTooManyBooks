package com.eleybourn.bookcatalogue.searches.amazon;

import android.net.ParseException;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class AmazonManager {

    private static final String PREFS_HOST_URL = "AmazonManager.hostUrl";

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BCPreferences.getString(PREFS_HOST_URL, "http://www.amazon.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        BCPreferences.setString(PREFS_HOST_URL, url);
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * It proxies through theagiledirector.com due to amazon not supporting mobile devices
     * FIXME: can we avoid this ?
     *
     * @param isbn The ISBN to search for
     */
    public static void search(@NonNull final String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              @NonNull final Bundle book,
                              final boolean fetchThumbnail) {

        String path = "https://bc.theagiledirector.com/getRest_v3.php";
        if (!isbn.isEmpty()) {
            path += "?isbn=" + isbn;
        } else {
            // if both empty, no search
            if (author.isEmpty() && title.isEmpty()) {
                return;
            }
            //replace spaces with %20
            author = author.replace(" ", "%20");
            //try {
            //	mAuthor = URLEncoder.encode(mAuthor, "utf-8");
            //} catch (UnsupportedEncodingException e1) {
            //	// Just use raw author...
            //}

            title = title.replace(" ", "%20");
            //try {
            //	mTitle = URLEncoder.encode(mTitle, "utf-8");
            //} catch (UnsupportedEncodingException e1) {
            //	// Just use raw title...
            //}
            path += "?author=" + author + "&title=" + title;
        }

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        SearchAmazonHandler handler = new SearchAmazonHandler(book, fetchThumbnail);

        try {
            URL url = new URL(path);
            parser = factory.newSAXParser();
            // We can't Toast anything here, so let exceptions fall through.
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);
        } catch (@NonNull MalformedURLException | ParserConfigurationException | ParseException | SAXException e) {
            Logger.error(e, "Error parsing XML");
        } catch (Exception e) {
            Logger.error(e, "Error retrieving or parsing XML");
        }
    }
}
