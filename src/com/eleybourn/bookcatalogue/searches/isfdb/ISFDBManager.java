package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchSiteManager;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

public class ISFDBManager
        implements SearchSiteManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "ISFDB.";

    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";

    /**
     * Constructor.
     */
    public ISFDBManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return App.getPrefs().getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    /**
     * {@link #search(String, String, String, boolean)} only implements isbn searches for now.
     *
     * @return {@code true}
     */
    @Override
    public boolean isIsbnOnly() {
        return true;
    }

    @Override
    public boolean supportsImageSize(@NonNull final ImageSizes size) {
        // support 1 size only
        return SearchSiteManager.ImageSizes.LARGE.equals(size);
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_isfdb;
    }

    /**
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
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

        if (ISBN.isValid(isbn)) {
            List<Editions.Edition> editions = new Editions().fetch(isbn);
            if (!editions.isEmpty()) {
                new ISFDBBook().fetch(editions, bookData, fetchThumbnail);
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
