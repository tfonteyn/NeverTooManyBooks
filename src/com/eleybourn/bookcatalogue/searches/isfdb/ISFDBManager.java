package com.eleybourn.bookcatalogue.searches.isfdb;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
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
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * See notes in the package-info.java file.
 */
public class ISFDBManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "ISFDB.";

    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

    /** Type: {@code boolean}. */
    private static final String PREFS_SERIES_FROM_TOC = PREF_PREFIX + "seriesFromToc";

    // The site claims to use ISO-8859-1, but the real encoding seems to be Windows-1252
    // For example, a books list price with a specific currency symbol (e.g. dutch guilders)
    // fails to be decoded unless we force Windows-1252
//    public static final String FORCE_CHARSET="ISO-8859-1";
//    public static final String FORCE_CHARSET="UTF-8";
    static final String FORCE_CHARSET="Windows-1252";

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

    /**
     * Open a Book on ISFDB web site.
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = getBaseURL() + "/cgi-bin/pl.cgi?" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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

        if (ISBN.isValid(isbn)) {
            List<Editions.Edition> editions = new Editions().fetch(isbn);
            if (!editions.isEmpty()) {
                //TODO: do not use Application Context for String resources
                Resources resources = App.getAppContext().getResources();
                return new ISFDBBook().fetch(editions, isCollectSeriesInfoFromToc(),
                                             fetchThumbnail, resources);
            }

        } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {

            //TODO: implement ISFDB search by author/title
            //replace spaces in author/title with %20
            String urlText = getBaseURL() + "/cgi-bin/adv_search_results.cgi?"
                    + "title_title%3A" + encodeSpaces(title)
                    + "%2B"
                    + "author_canonical%3A" + encodeSpaces(author);
            throw new UnsupportedOperationException(urlText);

        }

        //TODO: only let IOExceptions out (except RTE's)
        return new Bundle();
    }

    /**
     * replace spaces with %20.
     */
    private String encodeSpaces(@NonNull final String s) {
        return SPACE_PATTERN.matcher(s).replaceAll(Matcher.quoteReplacement("%20"));
    }
}
