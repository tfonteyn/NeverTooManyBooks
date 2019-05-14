package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchSiteManager;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

public class ISFDBManager
        implements SearchSiteManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "ISFDB.";

    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

    /** Type: {@code boolean}. */
    private static final String PREFS_SERIES_FROM_TOC = PREF_PREFIX + "seriesFromToc";

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

    public static boolean isCollectSeriesInfoFromToc() {
        return App.getPrefs().getBoolean(PREFS_SERIES_FROM_TOC, false);
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

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.isfdb;
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final /* not supported */ String author,
                         @Nullable final /* not supported */ String title,
                         final boolean fetchThumbnail)
            throws IOException {

        Bundle bookData = new Bundle();

        if (ISBN.isValid(isbn)) {
            List<Editions.Edition> editions = new Editions().fetch(isbn);
            if (!editions.isEmpty()) {
                new ISFDBBook().fetch(editions, bookData, isCollectSeriesInfoFromToc(), fetchThumbnail);
            }

        } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {

            //replace spaces in author/title with %20
            //TODO: implement ISFDB search by author/title
            String urlText = getBaseURL() + "/cgi-bin/adv_search_results.cgi?" +
                    "title_title%3A" + encodeSpaces(title) +
                    "%2B" +
                    "author_canonical%3A" + encodeSpaces(author);
            throw new UnsupportedOperationException(urlText);

        } else {
            return new Bundle();
        }

        //TODO: only let IOExceptions out (except RTE's)
        return bookData;
    }

    /**
     * replace spaces with %20
     */
    public String encodeSpaces(@NonNull final String s) {
        return SPACE_PATTERN.matcher(s).replaceAll(Matcher.quoteReplacement("%20"));
    }
}
