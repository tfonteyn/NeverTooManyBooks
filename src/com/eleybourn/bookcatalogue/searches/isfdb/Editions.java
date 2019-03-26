package com.eleybourn.bookcatalogue.searches.isfdb;

import androidx.annotation.NonNull;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

public class Editions
        extends AbstractBase {

    private static final String EDITIONS_URL = "/cgi-bin/se.cgi?arg=%s&type=ISBN";
    private ArrayList<String> mEditions;

    /**
     * Constructor.
     * <p>
     * We assume the isbn is already checked & valid.
     */
    public Editions(@NonNull final String isbn) {
        mPath = ISFDBManager.getBaseURL() + String.format(EDITIONS_URL, isbn);
    }

    /**
     * @return a list with native ISFDB book id's pointing to individual editions
     * (with the same isbn)
     *
     * @throws SocketTimeoutException on timeout
     */
    @NonNull
    long[] getBookIds()
            throws SocketTimeoutException {
        if (mEditions == null) {
            fetch();
        }
        long[] ids = new long[mEditions.size()];

        for (int i = 0; i < mEditions.size(); i++) {
            ids[i] = stripNumber(mEditions.get(i));
        }
        return ids;
    }

    /**
     * Example return:  "http://www.isfdb.org/cgi-bin/pl.cgi?230949".
     * <p>
     * Fails silently, returning an empty list.
     *
     * @return a list with URLs pointing to individual editions (with the same isbn)
     *
     * @throws SocketTimeoutException on timeout
     */
    public ArrayList<String> fetch()
            throws SocketTimeoutException {
        if (mEditions == null) {
            mEditions = new ArrayList<>();
        }
        if (!loadPage()) {
            return mEditions;
        }

        findEntries(mDoc, "tr.table0", "tr.table1");
        // if no editions found, we might have been redirected to the book itself
        if (mEditions.isEmpty()) {
            // check if the url looks like "http://www.isfdb.org/cgi-bin/pl.cgi?597467"
            if (mDoc.location().contains("pl.cgi")) {
                mEditions.add(mDoc.location());
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
            Logger.info(this, Tracker.State.Exit,"fetch", mEditions.toString());
        }
        return mEditions;
    }

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
                        mEditions.add(url);
                    }
                }
            }
        }
    }
}
