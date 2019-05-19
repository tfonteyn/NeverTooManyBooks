package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSiteManager;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * ENHANCE: Get editions via.
 * http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300
 */
public final class GoogleBooksManager
        implements SearchSiteManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "GoogleBooks.";

    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

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

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    @Override
    public boolean supportsImageSize(@NonNull final ImageSizes size) {
        // support 1 size only
        return ImageSizes.LARGE.equals(size);
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_google_books;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.google_books;
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         final boolean fetchThumbnail)
            throws IOException {

        String query;

        if (ISBN.isValid(isbn)) {
            query = "q=ISBN%3C" + isbn + "%3E";

        } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            query = "q=" + "intitle%3A" + encodeSpaces(title)
                    + "%2Binauthor%3A" + encodeSpaces(author);

        } else {
            return new Bundle();
        }

        Bundle bookData = new Bundle();

        SAXParserFactory factory = SAXParserFactory.newInstance();

        // The main handler can return multiple books ('entry' elements)
        SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();

        // The entry handler takes care of an individual book ('entry')
        SearchGoogleBooksEntryHandler entryHandler =
                new SearchGoogleBooksEntryHandler(bookData, fetchThumbnail);

        String url = getBaseURL() + "/books/feeds/volumes?" + query;

        try {
            SAXParser parser = factory.newSAXParser();

            // get the book list
            try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
                parser.parse(con.inputStream, handler);
            }

            ArrayList<String> urlList = handler.getUrlList();
            if (!urlList.isEmpty()) {
                // only using the first one found, maybe future enhancement?
                String oneBookUrl = urlList.get(0);
                try (TerminatorConnection con = TerminatorConnection.getConnection(oneBookUrl)) {
                    parser.parse(con.inputStream, entryHandler);
                }
            }
            // wrap parser exceptions in an IOException
        } catch (ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException(e);
        }

        return bookData;
    }

    /**
     * replace spaces with %20
     */
    public String encodeSpaces(@NonNull final String s) {
        return SPACE_PATTERN.matcher(s).replaceAll(Matcher.quoteReplacement("%20"));
    }
}
