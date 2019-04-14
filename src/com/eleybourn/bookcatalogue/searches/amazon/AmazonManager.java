package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Throttler;

/**
 * March/April 2019, I got this error:
 * //
 * //Warning: file_get_contents(http://webservices.amazon.com/onca/xml?AssociateTag=theagidir-20
 * // &Keywords=9780385269179
 * // &Operation=ItemSearch&ResponseGroup=Medium,Images
 * // &SearchIndex=Books&Service=AWSECommerceService
 * // &SubscriptionId=AKIAIHF2BM6OTOA23JEQ
 * // &Timestamp=2019-04-08T11:10:15.000Z
 * // &Signature=XJKVvg%2BDqzB7w50fXxvqN5hbDt2ZFzuNL5W%2BsDmcGXA%3D):
 * // failed to open stream: HTTP request failed!
 * // HTTP/1.1 503 Service Unavailable in /app/getRest_v3.php on line 70
 * //
 * // When executing the actual url:
 * //
 * // <ItemSearchErrorResponse xmlns="http://ecs.amazonaws.com/doc/2005-10-05/">
 * //<Error>
 * //<Code>RequestThrottled</Code>
 * //<Message>
 * //AWS Access Key ID: AKIAIHF2BM6OTOA23JEQ. You are submitting requests too quickly.
 * // Please retry your requests at a slower rate.
 * //</Message>
 * //</Error>
 * //<RequestID>ecf67cf8-e363-4b08-a140-48fcdbebd956</RequestID>
 * //</ItemSearchErrorResponse>
 * <p>
 * So... adding throttling. But obviously if the issue is on the actual 'theagiledirector'
 * site, this will not help. But with throttling I will at least not cause extra trouble.
 */
public final class AmazonManager
        implements SearchSites.SearchSiteManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "Amazon.";

    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";

    /** Can only send requests at a throttled speed. */
    @NonNull
    private static final Throttler THROTTLER = new Throttler();

    /**
     * Constructor.
     */
    public AmazonManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return App.getPrefs().getString(PREFS_HOST_URL, "https://www.amazon.com");
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
        // support 1 size only
        return SearchSites.ImageSizes.LARGE.equals(size);
    }

    @StringRes
    @Override
    public int getSearchingResId() {
        return R.string.searching_amazon;
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
            if (!ISBN.isValid(isbn)) {
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

        // See class docs: adding throttling
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
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
