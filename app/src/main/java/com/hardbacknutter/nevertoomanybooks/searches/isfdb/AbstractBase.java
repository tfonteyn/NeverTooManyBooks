/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.EditBookTocFragment;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

abstract class AbstractBase {

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors.
     * <p>
     * Original code in {@link EditBookTocFragment} had:
     * {@code CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";}
     * <p>
     * Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * So that became:
     * {@code private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";}
     * <p>
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final Pattern CLEANUP_TITLE_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

    // connect-timeout. Default is 5_000
    private static final int CONNECT_TIMEOUT = 30_000;
    // read-timeout. Default is 10_000
    private static final int READ_TIMEOUT = 60_000;
    private static final Pattern CR_PATTERN = Pattern.compile("\n", Pattern.LITERAL);

    /** The parsed downloaded web page. */
    Document mDoc;

    /** If the site drops connection, we retry once. */
    private boolean afterEofTryAgain = true;

    AbstractBase() {
    }

    @VisibleForTesting
    AbstractBase(final Document doc) {
        mDoc = doc;
    }

    /**
     * Fetch the URL and parse it into {@link #mDoc}.
     * <p>
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link HttpConnection#DEFAULT_UA}
     * <p>
     * The content encoding by default is: "Accept-Encoding", "gzip"
     *
     * @param url to fetch
     *
     * @return the actual URL for the page we got (after redirects etc), or {@code null}
     * on failure to load.
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @Nullable
    String loadPage(@NonNull final String url)
            throws SocketTimeoutException {

        if (mDoc == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                Logger.debug(this, "loadPage", "url=" + url);
            }

            try (TerminatorConnection terminatorConnection = new TerminatorConnection(url)) {
                HttpURLConnection con = terminatorConnection.getHttpURLConnection();

                // added due to https://github.com/square/okhttp/issues/1517
                // it's a server issue, this is a workaround.
                con.setRequestProperty("Connection", "close");
                con.setConnectTimeout(CONNECT_TIMEOUT);
                con.setReadTimeout(READ_TIMEOUT);
                // GO!
                terminatorConnection.open();

                // the original url will change after a redirect.
                // We need the actual url for further processing.
                String pageUrl = con.getURL().toString();
                mDoc = Jsoup.parse(terminatorConnection.inputStream,
                                   IsfdbManager.CHARSET_DECODE_PAGE, pageUrl);

                // sanity check
                if (!Objects.equals(pageUrl, mDoc.location())) {
                    Logger.warn(this, "loadPage",
                                "pageUrl=" + pageUrl, "location=" + mDoc.location());
                }

            } catch (@NonNull final HttpStatusException e) {
                Logger.error(this, e);
                return null;

            } catch (@NonNull final EOFException e) {
                // this happens often with ISFDB... Google search says it's a server issue.
                // not so sure that Google search is correct thought but what do I know...
                // So, retry once.
                if (afterEofTryAgain) {
                    afterEofTryAgain = false;
                    mDoc = null;
                    return loadPage(url);
                } else {
                    return null;
                }

            } catch (@NonNull final SocketTimeoutException e) {
                throw e;

            } catch (@NonNull final IOException e) {
                Logger.error(this, e, url);
                return null;
            }
            // reset the flags.
            afterEofTryAgain = true;
        }

        return mDoc.location();
    }

    @NonNull
    String cleanUpName(@NonNull final String s) {
        String tmp = CR_PATTERN.matcher(s.trim())
                               .replaceAll(Matcher.quoteReplacement(" "));
        return CLEANUP_TITLE_PATTERN.matcher(tmp)
                                    .replaceAll("")
                                    .trim();
    }

    /**
     * A url ends with 'last'123.  Strip and return the '123' part.
     *
     * @param url  to handle
     * @param last character to look for as last-index
     *
     * @return the number
     */
    long stripNumber(@NonNull final String url,
                     final char last) {
        int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return 0;
        }

        return Long.parseLong(url.substring(index));
    }

    String stripString(@NonNull final String url,
                       final char last) {
        int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return "";
        }

        return url.substring(index);
    }
}
