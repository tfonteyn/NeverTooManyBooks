package com.eleybourn.bookcatalogue.searches.titelbank;

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

public class TitelBankManager
        implements SearchSites.SearchSiteManager {

    private static final String TAG = "TitelBank.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    /**
     * Constructor.
     */
    public TitelBankManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "https://www.titelbank.nl");
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

        }
        return bookData;
    }
}
