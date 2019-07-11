package com.eleybourn.bookcatalogue.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Given an ISBN, search for other editions on the site.
 *
 * Uses JSoup screen scraping.
 */
public class Editions
        extends AbstractBase {

    /** Search URL template. */
    private static final String EDITIONS_URL = "/cgi-bin/se.cgi?arg=%s&type=ISBN";

    /** List of ISFDB native book id for all found editions. */
    private final ArrayList<Edition> mEditions = new ArrayList<>();

    /**
     * Constructor.
     */
    public Editions() {
    }

    /**
     * Fails silently, returning an empty list.
     *
     * @param isbn to get editions for. MUST be valid.
     *
     * @return a list with native ISFDB book ID's pointing to individual editions
     * (with the same isbn)
     *
     * @throws SocketTimeoutException on timeout
     */
    public ArrayList<Edition> fetch(@NonNull final String isbn)
            throws SocketTimeoutException {

        String url = ISFDBManager.getBaseURL() + String.format(EDITIONS_URL, isbn);

        // do not auto-redirect, handled manually. See the comments inside the loadPage method.
        if (!loadPage(url, false)) {
            // failed to load, return an empty list.
            return mEditions;
        }

        if (mDoc.location().contains("pl.cgi")) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            mEditions.add(new Edition(stripNumber(mDoc.location(), '?'), mDoc));

        } else if (mDoc.location().contains("title.cgi")) {
            // we have have multiple editions.
            findEntries(mDoc, "tr.table0", "tr.table1");
        } else {
            // dunno, let's log it
            Logger.warn(this, "fetch",
                        "location=" + mDoc.location());
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
            Logger.debugExit(this, "fetch", mEditions);
        }
        return mEditions;
    }

    /**
     * Search/scrape for the selectors to build the edition list.
     *
     * @param doc       to parse
     * @param selectors to search for
     */
    private void findEntries(@NonNull final Document doc,
                             @NonNull final String... selectors) {
        for (String selector : selectors) {
            Elements entries = doc.select(selector);
            for (Element entry : entries) {
                // first column has the book link
                Element edLink = entry.select("a").first();
                if (edLink != null) {
                    String url = edLink.attr("href");
                    if (url != null) {
                        mEditions.add(new Edition(stripNumber(url, '?')));
                    }
                }
            }
        }
    }

    /**
     * A data class for holding the ISFDB native book id and its doc (web page).
     */
    public static class Edition {

        /** The ISFDB native book id. */
        public final long isfdbId;
        /**
         * If a fetch of editions resulted in a single book returned (via redirects),
         * then the doc is kept here for immediate processing.
         */
        @Nullable
        public final Document doc;

        /**
         * Constructor: we found a link to a book.
         *
         * @param isfdbId of the book link we found
         */
        Edition(final long isfdbId) {
            this.isfdbId = isfdbId;
            doc = null;
        }

        /**
         * Constructor: we found a single edition, the doc contains the book for further processing.
         *
         * @param isfdbId of the book we found
         * @param doc     of the book we found
         */
        Edition(final long isfdbId,
                @Nullable final Document doc) {
            this.isfdbId = isfdbId;
            this.doc = doc;
        }
    }
}
