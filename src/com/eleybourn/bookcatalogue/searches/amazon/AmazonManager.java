package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Early 2019, I got this error:
 * <br />
 * <b>Warning</b>:  file_get_contents(http://webservices.amazon.com/onca/xml?
 * AssociateTag=theagidir-20
 * &amp;Keywords=0586216871
 * &amp;Operation=ItemSearch
 * &amp;ResponseGroup=Medium,Images
 * &amp;SearchIndex=Books
 * &amp;Service=AWSECommerceService
 * &amp;SubscriptionId=AKIAIHF2BM6OTOA23JEQ
 * &amp;Timestamp=2019-02-17T21:53:11.000Z
 * &amp;Signature=dEqLlFLyj4x%2BNJaticEpqKGSQm%2F75tmnnZ7dkegK8K0%3D)
 * : failed to open stream: HTTP request failed! HTTP/1.1 503 Service Unavailable
 * in <b>/app/getRest_v3.php</b> on line <b>70</b><br />
 */
public final class AmazonManager
        implements SearchSites.SearchSiteManager {

    private static final String TAG = "Amazon.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";

    /**
     * Constructor.
     */
    public AmazonManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "https://www.amazon.com");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    @Override
    public boolean isIsbnOnly() {
        return false;
    }

    @Override
    public boolean supportsImageSize(@NonNull final SearchSites.ImageSizes size) {
        return false;
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_amazon_books;
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * FIXME: Search proxies through theagiledirector.com - amazon not supporting mobile devices?
     *
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return bundle with book data
     *
     * @throws IOException on failure
     */
    @Override
    @NonNull
    @WorkerThread
    public Bundle search(@NonNull final String isbn,
                         @NonNull final String author,
                         @NonNull final String title,
                         final boolean fetchThumbnail)
            throws IOException {

        Bundle bookData = new Bundle();

        String url = "https://bc.theagiledirector.com/getRest_v3.php";
        if (!isbn.isEmpty()) {
            // sanity check
            if (!IsbnUtils.isValid(isbn)) {
                return bookData;
            }
            url += "?isbn=" + isbn;
        } else {
            // sanity check
            if (author.isEmpty() && title.isEmpty()) {
                return bookData;
            }
            //replace spaces in author/title with %20
            url += "?author=" + author.replace(" ", "%20")
                    + "&title=" + title.replace(" ", "%20");
        }

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

        // Get it
        try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }

        return bookData;
    }

    /**
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
}
