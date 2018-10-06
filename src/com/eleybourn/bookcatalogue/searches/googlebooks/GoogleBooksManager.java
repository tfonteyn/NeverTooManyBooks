package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
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
    static public File getCoverImage(@NonNull final String isbn) {
        Bundle bookData = new Bundle();
        try {
            search(isbn, "", "", bookData, true);
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

    public static void search(@NonNull final String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              @NonNull final Bundle book,
                              final boolean fetchThumbnail) {

        String path = getBaseURL() + "/books/feeds/volumes";
        if (!isbn.isEmpty()) {
            path += "?q=ISBN%3C" + isbn + "%3E";
        } else {
            // if both empty, no search
            if (author.isEmpty() && title.isEmpty()) {
                return;
            }
            //replace spaces with %20
            author = author.replace(" ", "%20");
            title = title.replace(" ", "%20");
            path += "?q=" + "intitle%3A" + title + "%2Binauthor%3A" + author + "";
        }

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
        SearchGoogleBooksEntryHandler entryHandler = new SearchGoogleBooksEntryHandler(book, fetchThumbnail);

        try {
            URL url = new URL(path);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), handler);

            if (handler.getCount() > 0) {
                String id = handler.getId();
                url = new URL(id);
                parser = factory.newSAXParser();
                parser.parse(Utils.getInputStreamWithTerminator(url), entryHandler);
            }
            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (ParserConfigurationException | IOException | SAXException e) {
            Logger.logError(e);
        }
    }
}
