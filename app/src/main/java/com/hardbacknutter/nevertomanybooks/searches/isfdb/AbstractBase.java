package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Objects;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.EditBookTocFragment;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection;

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
    private static final String CLEANUP_TITLE_REGEX = "[,.':;`~@#$%^&*(\\-=_+]*$";

    /** The parsed downloaded web page. */
    Document mDoc;

    private boolean afterEofTryAgain = true;
    private boolean afterBrokenRedirectTryAgain = true;

    /**
     * Fetch the URL and parse it into {@link #mDoc}.
     * <p>
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link org.jsoup.helper.HttpConnection#DEFAULT_UA}
     * <p>
     * The content encoding by default is: "Accept-Encoding", "gzip"
     *
     * @param url      to fetch
     * @param redirect {@code true} to follow redirects. This should normally be the default.
     *
     * @return the actual URL for the page we got (after redirects etc), or {@code null}
     * on failure to load.
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @Nullable
    String loadPage(@NonNull final String url,
                    final boolean redirect)
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
                // connect-timeout. Default is 5_000
                con.setConnectTimeout(30_000);
                // read-timeout. Default is 10_000
                con.setReadTimeout(60_000);
                // the default is true...
                con.setInstanceFollowRedirects(true);

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
                                "pageUrl=" + pageUrl,
                                "location=" + mDoc.location());
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
                    return loadPage(url, redirect);
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
            afterBrokenRedirectTryAgain = true;
            afterEofTryAgain = true;
        }

        return mDoc.location();
    }

    /**
     * Using Jsoup.connect(url) became problematic due to server character set encoding
     * being different then what the server tells us.
     * Rewritten in {@link #loadPage(String, boolean)}}
     * TODO: delete this method...
     * <p>
     * Fetch the URL and parse it into {@link #mDoc}.
     * <p>
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link org.jsoup.helper.HttpConnection#DEFAULT_UA}
     * <p>
     * The content encoding by default is: "Accept-Encoding", "gzip"
     *
     * @param url      to fetch
     * @param redirect {@code true} to follow redirects. This should normally be the default.
     *
     * @return {@code true} when fetched and parsed ok.
     *
     * @throws SocketTimeoutException if the connection times out
     */
    boolean loadPageWithBrokenRedirectSupport(@NonNull final String url,
                                              final boolean redirect)
            throws SocketTimeoutException {
        if (mDoc == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                Logger.debug(this, "loadPage_old", "url=" + url);
            }
            Connection con = Jsoup.connect(url)
                                  // added due to https://github.com/square/okhttp/issues/1517
                                  // it's a server issue, this is a workaround.
                                  .header("Connection", "close")
                                  // connect and read-timeout. Default is 30.
                                  .timeout(60_000)
                                  // maximum bytes to read before connection is closed, default 1mb
                                  .maxBodySize(2_000_000)
                                  .followRedirects(redirect);

            try {
                /*
                 * @throws MalformedURLException if the request URL is not a HTTP
                 * or HTTPS URL, or is otherwise malformed
                 *
                 * @throws HttpStatusException(IOException) if the response is not OK and
                 * HTTP response errors are not ignored
                 *
                 * @throws UnsupportedMimeTypeException if the response mime type is not
                 * supported and those errors are not ignored
                 *
                 * @throws SocketTimeoutException if the connection times out
                 *
                 * @throws IOException on failure
                 */
                mDoc = con.get();

                if (!redirect
                        && con.response().statusCode() == 303
                        && afterBrokenRedirectTryAgain) {
                    afterBrokenRedirectTryAgain = false;

                    Connection.Response response = con.response();
                    String location = response.header("Location");

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                        Logger.debug(this, "loadPage_old", "303",
                                     "Location=" + location);
                    }

                    // 2019-04-20: it seems the website was updated/fixed.
                    if (location != null) {

                        // This is nasty... getting editions from ISFDB for a book where there
                        // is only one edition results in a redirect 303 with header:
                        //    Location: http:/cgi-bin/pl.cgi?367574
                        // Note the single '/' after the protocol.
                        // The source code of Jsoup recognises this issue and tries to deal with it,
                        // org\jsoup\helper\HttpConnection.java
                        // line 725:
                        //      static Response execute(Connection.Request req, Response previousResponse) throws IOException {
                        // lines 764:
                        //      String location = res.header(LOCATION);
                        //      if (location != null && location.startsWith("http:/") && location.charAt(6) != '/') // fix broken Location: http:/temp/AAG_New/en/index.php
                        //      location = location.substring(6);
                        //      URL redir = StringUtil.resolve(req.url(), location);
                        //      req.url(encodeUrl(redir));
                        // however, in this case.. it fails to handle it correctly.
                        // The single '/' is part of the location.
                        //
                        // So we manually re-construct the edition url here.
                        if (location.startsWith("http:/") && location.charAt(6) != '/') {
                            // The single '/' is part of the location.
                            location = IsfdbManager.getBaseURL() + location.substring(5);

                        } else if (location.startsWith("https:/") && location.charAt(7) != '/') {
                            // The single '/' is part of the location.
                            location = IsfdbManager.getBaseURL() + location.substring(6);

                        }

                        mDoc = null;
                        return loadPageWithBrokenRedirectSupport(location, false);
                    }
                    return false;
                }

            } catch (@NonNull final HttpStatusException e) {
                Logger.error(this, e);
                return false;

            } catch (@NonNull final EOFException e) {
                // this happens often with ISFDB... Google search says it's a server issue.
                // not so sure that Google search is correct thought but what do I know...
                // So, retry once.
                if (afterEofTryAgain) {
                    afterEofTryAgain = false;
                    mDoc = null;
                    return loadPageWithBrokenRedirectSupport(url, redirect);
                } else {
                    return false;
                }

            } catch (@NonNull final SocketTimeoutException e) {
                throw e;

            } catch (@NonNull final IOException e) {
                Logger.error(this, e, url);
                return false;
            }
            // reset the flags.
            afterBrokenRedirectTryAgain = true;
            afterEofTryAgain = true;
        }
        return true;
    }


    @NonNull
    String cleanUpName(@NonNull final String s) {
        return s.trim()
                .replace("\n", " ")
                .replaceAll(CLEANUP_TITLE_REGEX, "")
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
