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
import java.net.URLEncoder;
import java.util.List;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * See notes in the package-info.java file.
 */
public class IsfdbManager
        implements SearchEngine {

    // The site claims to use ISO-8859-1, but the real encoding seems to be Windows-1252
    // For example, a books list price with a specific currency symbol (e.g. dutch guilders)
    // fails to be decoded unless we force Windows-1252
//    static final String CHARSET_DECODE_PAGE="ISO-8859-1";
//    static final String CHARSET_DECODE_PAGE="UTF-8";
    static final String CHARSET_DECODE_PAGE = "Windows-1252";
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "ISFDB.";
    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    /** Type: {@code boolean}. */
    static final String PREFS_SERIES_FROM_TOC = PREF_PREFIX + "seriesFromToc";
    /** Type: {@code boolean}. */
    public static final String PREFS_USE_PUBLISHER = PREF_PREFIX + "uses.publisher";
    // The charset to encode the search url.
    private static final String CHARSET_ENCODE_URL = "iso-8859-1";

    /**
     * Constructor.
     */
    public IsfdbManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return App.getPrefs().getString(PREFS_HOST_URL, "http://www.isfdb.org");
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

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.isfdb;
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        Resources resources = LocaleUtils.getLocalizedResources();

        List<Editions.Edition> editions;

        if (ISBN.isValid(isbn)) {
            editions = new Editions().fetch(isbn);

        } else {
            String url = getBaseURL() + "/cgi-bin/adv_search_results.cgi?"
                    + "ORDERBY=pub_title"
                    + "&ACTION=query"
                    + "&START=0"
                    + "&TYPE=Publication"
                    + "&C=AND"
                    + "&";

            int index = 0;

            if (author != null && !author.isEmpty()) {
                index++;
                url += "&USE_" + index + "=author_canonical"
                        + "&O_" + index + "=contains"
                        + "&TERM_" + index + "="
                        + URLEncoder.encode(author, CHARSET_ENCODE_URL);
            }

            if (title != null && !title.isEmpty()) {
                index++;
                url += "&USE_" + index + "=pub_title"
                        + "&O_" + index + "=contains"
                        + "&TERM_" + index + "="
                        + URLEncoder.encode(DAO.unMangleTitle(title), CHARSET_ENCODE_URL);
            }

            // as per user settings.
            if (App.getPrefs().getBoolean(PREFS_USE_PUBLISHER, false)) {
                if (publisher != null && !publisher.isEmpty()) {
                    index++;
                    url += "&USE_" + index + "=pub_publisher"
                            + "&O_" + index + "=contains"
                            + "&TERM_" + index + "="
                            + URLEncoder.encode(publisher, CHARSET_ENCODE_URL);
                }
            }

            // there is support for up to 6 search terms.
            // &USE_4=pub_title&O_4=exact&TERM_4=
            // &USE_5=pub_title&O_5=exact&TERM_5=
            // &USE_6=pub_title&O_6=exact&TERM_6=

            editions = new Editions().fetchPath(url);
        }

        if (!editions.isEmpty()) {
            return new IsfdbBook().fetch(editions, fetchThumbnail, resources);
        } else {
            return new Bundle();
        }
    }
}
