package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.EditBookTOCFragment;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;

abstract class AbstractBase {

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors
     *
     * Original code in {@link EditBookTOCFragment} had:
     * CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";
     *
     * Android Studio:
     * Reports character escapes that are replaceable with the unescaped character without a
     * change in meaning. Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * For example the regex [\.] is identical to [.]
     *
     * So that became:
     * private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";
     *
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final String CLEANUP_TITLE_REGEX = "[,.':;`~@#$%^&*(\\-=_+]*$";

    String mPath;
    Document mDoc;

    /**
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link org.jsoup.helper.HttpConnection#DEFAULT_UA}
     *
     * The content encoding by default is: "Accept-Encoding", "gzip"
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean loadPage() throws SocketTimeoutException {
        if (mDoc == null) {
            /* Originally using:  mDoc = Jsoup.connect(mPath).followRedirects(true).get();
               but that seems to leak connections upon error
             */
            Connection con = Jsoup.connect(mPath)
                    .followRedirects(true)
                    .method(Connection.Method.GET)
                    .ignoreHttpErrors(false);
            Connection.Response response = null;
            try {
                /*
                 * @throws java.net.MalformedURLException if the request URL is not a HTTP or HTTPS URL, or is otherwise malformed
                 * @throws HttpStatusException(IOException) if the response is not OK and HTTP response errors are not ignored
                 * @throws UnsupportedMimeTypeException if the response mime type is not supported and those errors are not ignored
                 * @throws java.net.SocketTimeoutException if the connection times out
                 * @throws IOException on error
                 */
                response = con.execute();

                /*
                 * @throws IOException on error
                 */
                mDoc = response.parse();

            } catch (java.net.SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    if (response != null) {
                        Logger.info(this,"charset      : " + response.charset());
                        Logger.info(this,"contentType  : " + response.contentType());
                        Logger.info(this,"statusCode   : " + response.statusCode());
                        Logger.info(this,"statusMessage: " + response.statusMessage());
                        Logger.info(this,"contentType  : " + response.contentType());
                    }
                }
                Logger.error(e, mPath);
                return false;
            } finally {
                // W/OkHttpClient: A connection to http://www.isfdb.org/ was leaked. Did you forget to close a response body?
                // is this what they mean ? Mr. Internet mentions a lot of response.body().close; but that seems to be newer versions.
                if (mDoc == null && response != null) {
                    try {
                        Logger.info(this,"Trying to close the body of " + mPath);
                        BufferedInputStream s = response.bodyStream();
                        if (s != null) {
                            s.close();
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        return true;
    }

    @NonNull
    String cleanUpName(final @NonNull String s) {
        return s.trim()
                .replace("\n", " ")
                .replaceAll(CLEANUP_TITLE_REGEX, "")
                .trim();
    }

    long stripNumber(final @NonNull String url) {
        return Long.parseLong(url.split("\\?")[1]);
    }
}
