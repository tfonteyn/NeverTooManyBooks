package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.net.URL;
import java.util.Objects;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

// ENHANCE: Get editions via: http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300

public class GoogleBooksManager {

    private static final String PREFS_HOST_URL = "GoogleBooksManager.hostUrl";

    public static String getBaseURL() {
        return BCPreferences.getString(PREFS_HOST_URL, "http://books.google.com");
    }

    public static void setBaseURL(String url) {
        if (BuildConfig.DEBUG) {
            System.out.println("GoogleBooksManager new base url: " + url);
        }
        BCPreferences.setString(PREFS_HOST_URL, url);
    }

    static public File getThumbnailFromIsbn(String isbn) {
        Bundle b = new Bundle();
        try {
            searchGoogle(isbn, "", "", b, true);
            if (b.containsKey(SearchManager.BKEY_THUMBNAIL_SEARCHES)
                    && b.getString(SearchManager.BKEY_THUMBNAIL_SEARCHES) != null) {
                File f = new File(Objects.requireNonNull(b.getString(SearchManager.BKEY_THUMBNAIL_SEARCHES)));
                File newName = new File(f.getAbsolutePath() + "_" + isbn);
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(newName);
                return newName;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.logError(e, "Error getting thumbnail from Google");
            return null;
        }
    }

    static public void searchGoogle(String mIsbn, String author, String title, Bundle bookData, boolean fetchThumbnail) {
        //replace spaces with %20
        author = author.replace(" ", "%20");
        title = title.replace(" ", "%20");

        String path = getBaseURL() + "/books/feeds/volumes";
        if (mIsbn.isEmpty()) {
            path += "?q=" + "intitle%3A" + title + "%2Binauthor%3A" + author + "";
        } else {
            path += "?q=ISBN%3C" + mIsbn + "%3E";
        }
        URL url;

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
        SearchGoogleBooksEntryHandler entryHandler = new SearchGoogleBooksEntryHandler(bookData, fetchThumbnail);

        try {
            url = new URL(path);
            parser = factory.newSAXParser();
            int count;
            // We can't Toast anything from here; it no longer runs in UI thread. So let the caller deal
            // with any exceptions.
            parser.parse(Utils.getInputStream(url), handler);
            count = handler.getCount();
            if (count > 0) {
                String id = handler.getId();
                url = new URL(id);
                parser = factory.newSAXParser();
                parser.parse(Utils.getInputStream(url), entryHandler);
            }
            return;
        } catch (Exception e) {
            Logger.logError(e);
        }
        return;
    }

}
