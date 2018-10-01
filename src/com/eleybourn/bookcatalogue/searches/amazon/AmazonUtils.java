package com.eleybourn.bookcatalogue.searches.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.WebView;
import android.widget.Toast;

import com.amazon.device.associates.AssociatesAPI;
import com.amazon.device.associates.LinkService;
import com.amazon.device.associates.OpenSearchPageRequest;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Wrappers for Amazon API
 *
 * NOTE: The project manifest must contain the app key granted by Amazon.
 * For testing purposes this KEY can be junk.
 *
 * @author pjw
 */
public class AmazonUtils {

    private static final String SUFFIX_BASE_URL = "/gp/search?index=books";
    private static final String SUFFIX_EXTRAS = "&tag=bookcatalogue-20&linkCode=da5";

    // key into the Manifest meta-data
    private static final String AMAZON_KEY = "amazon.appkey";

    private static void openLink(@NonNull final Context context, @Nullable String author, @Nullable String series) {
        // Build the URL and args
        String url = AmazonManager.getBaseURL() + SUFFIX_BASE_URL;
        author = cleanupSearchString(author);
        series = cleanupSearchString(series);

        String extra = buildSearchArgs(author, series);

        if (extra != null && !extra.isEmpty()) {
            url += extra;
        }

        WebView wv = new WebView(context);

        LinkService linkService;

        // Try to setup the API calls; if not possible, just open directly and return
        try {
            // Init Amazon API
            AssociatesAPI.initialize(new AssociatesAPI.Config(BookCatalogueApp.getManifestString(AMAZON_KEY), context));
            linkService = AssociatesAPI.getLinkService();
            try {
                linkService.overrideLinkInvocation(wv, url);
            } catch (Exception e2) {
                OpenSearchPageRequest request = new OpenSearchPageRequest("books", author + " " + series);
                linkService.openRetailPage(request);
            }
        } catch (Exception e) {
            Logger.logError(e, "Unable to use Amazon API");
            Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(url + SUFFIX_EXTRAS));
            context.startActivity(loadweb);
        }
    }

    @Nullable
    private static String buildSearchArgs(String author, String series) {
        // This code works, but Amazon have a nasty tendency to cancel Associate IDs...
        //String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
        String extra = "";
        // http://www.amazon.com/gp/search?index=books&field-author=steven+a.+mckay&field-keywords=the+forest+lord
        if (author != null && !author.isEmpty()) {
            //FIXME: the replaceAll call in fact discards the result....
            //FIXME: you need to:   s = s.replaceAll  to have them take effect
            //FIXME: not fixing this until the code/reason of the non-used replace is understood.
            author.replaceAll("\\.,+", " ");
            author.replaceAll(" *", "+");
            try {
                extra += "&field-author=" + URLEncoder.encode(author, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.logError(e, "Unable to add author to URL");
                return null;
            }
        }
        if (series != null && !series.isEmpty()) {
            series.replaceAll("\\.,+", " ");
            series.replaceAll(" *", "+");
            try {
                extra += "&field-keywords=" + URLEncoder.encode(series, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.logError(e, "Unable to add series to URL");
                return null;
            }
        }
        return extra.trim();
    }

    private static String cleanupSearchString(@Nullable final String search) {
        if (search == null)
            return "";

        StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
            } else {
                curr = ' ';
                if (!Character.isWhitespace(prev)) {
                    out.append(curr);
                }
            }
            prev = curr;
        }
        return out.toString().trim();
    }

    public static void openAmazonSearchPage(@NonNull final Context context,
                                            @Nullable final String author,
                                            @Nullable final String series) {

        try {
            openLink(context, author, series);
        } catch (Exception ae) {
            // An Amazon error should not crash the app
            Logger.logError(ae, "Unable to call the Amazon API");
            Toast.makeText(context, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            // This code works, but Amazon have a nasty tendency to cancel Associate IDs...
            //String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
            //String extra = buildSearchArgs(author, series);
            //if (extra != null && !extra.isEmpty()) {
            //	Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
            //	context.startActivity(loadweb);
            //}
        }
    }
}
