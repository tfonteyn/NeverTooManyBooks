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
        return BookCatalogueApp.Prefs.getString(PREFS_HOST_URL, "http://books.google.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        BookCatalogueApp.Prefs.putString(PREFS_HOST_URL, url);
    }

    @Nullable
    static public File getCoverImage(@NonNull final String isbn) {
        Bundle mBookData = new Bundle();
        try {
            search(isbn, "", "", mBookData, true);
            if (mBookData.containsKey(UniqueId.BKEY_THUMBNAIL_FILES_SPEC)
                    && mBookData.getString(UniqueId.BKEY_THUMBNAIL_FILES_SPEC) != null) {
                File fromFile = new File(Objects.requireNonNull(mBookData.getString(UniqueId.BKEY_THUMBNAIL_FILES_SPEC)));
                File toFile = new File(fromFile.getAbsolutePath() + "_" + isbn);
                StorageUtils.renameFile(fromFile, toFile);
                return toFile;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.error(e, "Error getting thumbnail from Google");
            return null;
        }
    }

    public static void search(@NonNull final String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              @NonNull final Bundle /* out */ book,
                              final boolean fetchThumbnail) throws IOException {

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

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | ParseException |SAXException e) {
            Logger.error(e);
        }
    }
}
