package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.net.URL;
import java.util.Objects;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

// ENHANCE: Get editions via: http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300

public class GoogleBooksManager {

    private static final String PREFS_HOST_URL = "GoogleBooksManager.hostUrl";

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BCPreferences.getString(PREFS_HOST_URL, "http://books.google.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        BCPreferences.setString(PREFS_HOST_URL, url);
    }

    @Nullable
    static public File getThumbnailFromIsbn(@NonNull final String isbn) {
        Bundle bookData = new Bundle();
        try {
            searchGoogle(isbn, "", "", bookData, true);
            if (bookData.containsKey(UniqueId.BKEY_THUMBNAIL_USCORE)
                    && bookData.getString(UniqueId.BKEY_THUMBNAIL_USCORE) != null) {
                File f = new File(Objects.requireNonNull(bookData.getString(UniqueId.BKEY_THUMBNAIL_USCORE)));
                File newName = new File(f.getAbsolutePath() + "_" + isbn);
                StorageUtils.renameFile(f, newName);
                return newName;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.logError(e, "Error getting thumbnail from Google");
            return null;
        }
    }

    static public void searchGoogle(@NonNull final String isbn,
                                    @NonNull String author,
                                    @NonNull String title,
                                    @NonNull final Bundle bookData,
                                    final boolean fetchThumbnail) {
        //replace spaces with %20
        author = author.replace(" ", "%20");
        title = title.replace(" ", "%20");

        String path = getBaseURL() + "/books/feeds/volumes";
        if (isbn.isEmpty()) {
            path += "?q=" + "intitle%3A" + title + "%2Binauthor%3A" + author + "";
        } else {
            path += "?q=ISBN%3C" + isbn + "%3E";
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
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

}
