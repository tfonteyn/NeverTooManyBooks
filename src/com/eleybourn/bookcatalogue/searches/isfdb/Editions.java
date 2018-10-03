package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

class Editions extends AbstractBase {

    private static final String EDITIONS_URL = "/cgi-bin/se.cgi?arg=%s&type=ISBN";
    private List<String> mEditions;

    /**
     * We assume the isbn is already checked & valid!
     */
    Editions(@NonNull final String isbn) {
        mPath = ISFDBManager.getBaseURL() + String.format(ISFDBManager.getBaseURL() + EDITIONS_URL, isbn);
    }

    /**
     * @return a list with native ISFDB book id's pointing to individual editions (with the same isbn)
     */
    long[] getBookIds() {
        if (mEditions == null) {
            fetchEditions();
        }
        long[] ids = new long[mEditions.size()];

        for (int i = 0; i < mEditions.size(); i++) {
            ids[i] = stripNumber(mEditions.get(i));
        }
        return ids;
    }

    /**
     * Example return:  "http://www.isfdb.org/cgi-bin/pl.cgi?230949"
     *
     * Fails silently, returning an empty list.
     *
     * @return a list with URLs pointing to individual editions (with the same isbn)
     */
    List<String> fetchEditions() {
        if (mEditions == null) {
            mEditions = new ArrayList<>();
        }
        if (!loadPage()) {
            return mEditions;
        }

        findEntries(mDoc, "tr.table0");
        findEntries(mDoc, "tr.table1");
        // if no editions, we were redirected to the book itself
        if (mEditions.size() == 0) {
            mEditions.add(mDoc.location());
        }

        return mEditions;
    }

    private void findEntries(@NonNull final Document doc, @NonNull final String selector) {
        Elements entries = doc.select(selector);
        for (Element entry : entries) {
            Element edLink = entry.select("a").first(); // first column has the book link
            if (edLink != null) {
                String url = edLink.attr("href");
                if (url != null) {
                    mEditions.add(url);
                }
            }
        }
    }
}
