package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.net.ParseException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

// ENHANCE: Get editions via: http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300

public class GoogleBooksManager {

    private static final String PREFS_HOST_URL = "GoogleBooksManager.hostUrl";

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BookCatalogueApp.Prefs.getString(PREFS_HOST_URL, "http://books.google.com");
    }

    public static void setBaseURL(final @NonNull String url) {
        BookCatalogueApp.Prefs.putString(PREFS_HOST_URL, url);
    }

    /**
     *
     * @param isbn for book cover to find
     *
     * @return found & saved File, or null when none
     */
    @Nullable
    static public File getCoverImage(final @NonNull String isbn) {
        Bundle bookData = new Bundle();
        try {
            search(isbn, "", "", bookData, true);

            String fileSpec = bookData.getString(UniqueId.BKEY_THUMBNAIL_FILE_SPEC);
            if (fileSpec != null) {
                File found = new File(fileSpec);
                File coverFile = new File(found.getAbsolutePath() + "_" + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.error(e, "Error getting thumbnail from Google");
            return null;
        }
    }

    public static void search(final @NonNull String isbn,
                              final @NonNull String author,
                              final @NonNull String title,
                              final @NonNull Bundle /* out */ book,
                              final boolean fetchThumbnail) throws IOException {

        String path = getBaseURL() + "/books/feeds/volumes";
        if (!isbn.isEmpty()) {
            path += "?q=ISBN%3C" + isbn + "%3E";
        } else {
            // if both empty, no search
            if (author.isEmpty() && title.isEmpty()) {
                return;
            }
            //replace spaces in author/title with %20
            path += "?q=" + "intitle%3A" + title.replace(" ", "%20") + "%2Binauthor%3A" + author.replace(" ", "%20") + "";
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

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | ParseException |SAXException e) {
            Logger.error(e);
        }
    }
}
