package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ISFDBManager
        implements SearchSites.SearchSiteManager {

    private static final String TAG = "ISFDB.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    /**
     * Constructor.
     */
    public ISFDBManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }


    /**
     * ENHANCE: For now, always returns the image from the first edition found.
     *
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

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    /**
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return bundle with book data
     *
     * @throws IOException on failure
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final String isbn,
                         @NonNull final String author,
                         @NonNull final String title,
                         final boolean fetchThumbnail)
            throws IOException {

        Bundle bookData = new Bundle();

        if (IsbnUtils.isValid(isbn)) {
            List<String> editions = new Editions(isbn).fetch();
            if (!editions.isEmpty()) {
                ISFDBBook isfdbBook = new ISFDBBook(editions);
                isfdbBook.fetch(bookData, fetchThumbnail);
            }
        } else {
            //replace spaces in author/title with %20
            //TODO: implement ISFDB search by author/title
            String urlText = getBaseURL() + "/cgi-bin/adv_search_results.cgi?" +
                    "title_title%3A" + title.replace(" ", "%20") +
                    "%2B" +
                    "author_canonical%3A" + author.replace(" ", "%20");
            throw new UnsupportedOperationException(urlText);
        }
        //TODO: only let IOExceptions out (except RTE's)
        return bookData;
    }
}
