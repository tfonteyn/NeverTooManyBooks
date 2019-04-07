package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ISFDBManager
        implements SearchSites.SearchSiteManager {

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
     * {@link #search(String, String, String, boolean)} only implements isbn searches for now.
     *
     * @return <tt>true</tt>
     */
    @Override
    public boolean isIsbnOnly() {
        return true;
    }

    @Override
    public boolean supportsImageSize(@NonNull final SearchSites.ImageSizes size) {
        // support 1 size only
        return SearchSites.ImageSizes.LARGE.equals(size);
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_isfdb;
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

        if (ISBN.isValid(isbn)) {
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
