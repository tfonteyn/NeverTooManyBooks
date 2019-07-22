package com.eleybourn.bookcatalogue.searches.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.Throttler;

/**
 * March/April 2019, I got this error:
 * //
 * //Warning: file_get_contents(http://webservices.amazon.com/onca/xml?
 * AssociateTag=theagidir-20
 * &Keywords=9780385269179
 * &Operation=ItemSearch&ResponseGroup=Medium,Images
 * &SearchIndex=Books&Service=AWSECommerceService
 * &SubscriptionId=AKIAIHF2BM6OTOA23JEQ
 * &Timestamp=2019-04-08T11:10:15.000Z
 * &Signature=XJKVvg%2BDqzB7w50fXxvqN5hbDt2ZFzuNL5W%2BsDmcGXA%3D):
 * <p>
 * failed to open stream: HTTP request failed!
 * HTTP/1.1 503 Service Unavailable in /app/getRest_v3.php on line 70
 * <p>
 * 2019-04-20: it's the actual 'theagiledirector'.
 * Initial requests from here was failing first time. FIXME: cut the dependency on that proxy.
 * but how.... seems AWS is dependent/linked to have a website.
 */
public final class AmazonManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "Amazon.";

    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

    /** Can only send requests at a throttled speed. */
    @NonNull
    private static final Throttler THROTTLER = new Throttler();
    private static final String SUFFIX_BASE_URL = "/gp/search?index=books";
    private static final String PROXY_URL = "https://bc.theagiledirector.com/getRest_v3.php?";
    public static final String UTF_8 = "UTF-8";

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

    /**
     * @param context Current context
     * @param author  to search for
     * @param series  to search for
     */
    public static void openWebsite(@NonNull final Context context,
                                   @Nullable final String author,
                                   @Nullable final String series) {

        String cAuthor = cleanupSearchString(author);
        String cSeries = cleanupSearchString(series);

        String extra = "";
        if (!cAuthor.isEmpty()) {
            try {
                extra += "&field-author=" + URLEncoder.encode(cAuthor, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(AmazonManager.class, e, "Unable to add author to URL");
            }
        }

        if (!cSeries.isEmpty()) {
            try {
                extra += "&field-keywords=" + URLEncoder.encode(cSeries, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(AmazonManager.class, e, "Unable to add series to URL");
            }
        }

        String url = getBaseURL() + SUFFIX_BASE_URL + extra.trim();
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    private static String cleanupSearchString(@Nullable final String search) {
        if (search == null || search.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
                prev = curr;
            } else {
                if (!Character.isWhitespace(prev)) {
                    out.append(' ');
                }
                prev = ' ';
            }
        }
        return out.toString().trim();
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.amazon;
    }

    /**
     * This searches the amazon REST site based on a specific isbn.
     * <p>
     * https://docs.aws.amazon.com/AWSECommerceService/latest/DG/EX_LookupbyISBN.html
     * https://docs.aws.amazon.com/AWSECommerceService/latest/DG/becomingAssociate.html#locale
     * <p>
     * Search proxies through theagiledirector.com - the service does not support mobile devices...
     * <br>
     * <br>{@inheritDoc}
     */
    @Override
    @NonNull
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        String query;

        if (ISBN.isValid(isbn)) {
            query = "isbn=" + isbn;

        } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            query = "author=" + URLEncoder.encode(author, UTF_8)
                    + "&title=" + URLEncoder.encode(title, UTF_8);

        } else {
            return new Bundle();
        }

        Bundle bookData = new Bundle();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

        // See class docs: adding throttling
        THROTTLER.waitUntilRequestAllowed();

        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(PROXY_URL + query)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException(e);
        }

        //TEST: due to the proxy, we don't get actual XML error entities, so this is not tested.
        String error = handler.getError();
        if (error != null) {
            throw new IOException(error);
        }
        return bookData;
    }
}
