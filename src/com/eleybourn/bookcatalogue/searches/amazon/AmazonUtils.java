package com.eleybourn.bookcatalogue.searches.amazon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amazon.device.associates.AssociatesAPI;
import com.amazon.device.associates.LinkService;
import com.amazon.device.associates.NotInitializedException;
import com.amazon.device.associates.OpenSearchPageRequest;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.UserMessage;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Provides the {@link #openSearchPage(Activity, String, String)} api.
 * <p>
 * Either opening a link via the Amazon API (if we have a key) or open a web page.
 * <p>
 * NOTE: The project manifest must contain the app key granted by Amazon.
 * For testing purposes this KEY can be empty.
 * <p>
 * <p>
 * Not used, but as a reminder this url is also usable:
 * http://www.amazon.com/gp/product/ASIN-VALUE-HERE
 *
 * @author pjw
 */
public final class AmazonUtils {

    private static final String SUFFIX_BASE_URL = "/gp/search?index=books";
    private static final String SUFFIX_EXTRAS = "&tag=bookcatalogue-20&linkCode=da5";

    /** key into the Manifest meta-data. */
    private static final String AMAZON_KEY = "amazon.app_key";

    private AmazonUtils() {
    }

    private static void openLink(@NonNull final Context context,
                                 @Nullable String author,
                                 @Nullable String series) {
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
            AssociatesAPI.initialize(new AssociatesAPI.Config(
                    BookCatalogueApp.getManifestString(AMAZON_KEY), context));

            linkService = AssociatesAPI.getLinkService();
            try {
                linkService.overrideLinkInvocation(wv, url);
            } catch (RuntimeException e2) {
                OpenSearchPageRequest request =
                        new OpenSearchPageRequest("books", author + ' ' + series);
                linkService.openRetailPage(request);
            }
        } catch (NotInitializedException e) {
            Logger.error(e, "Unable to use Amazon API, starting external browser instead");
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url + SUFFIX_EXTRAS)));
        }
    }

    @Nullable
    private static String buildSearchArgs(@Nullable String author,
                                          @Nullable String series) {
        // This code works, but Amazon have a nasty tendency to cancel Associate IDs...
        //String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
        String extra = "";
        // http://www.amazon.com/gp/search?index=books&field-author=steven+a.+mckay&field-keywords=the+forest+lord
        if (author != null && !author.isEmpty()) {
            //FIXME: the replaceAll call discards the result!
            // Use: s = s.replaceAll  to have it take effect
            // not fixing this until the code/reason of the non-used replace and working ok, is understood.
            author.replaceAll("\\.,+", " ");
            author.replaceAll(" *", "+");
            try {
                extra += "&field-author=" + URLEncoder.encode(author, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error(e, "Unable to add author to URL");
                return null;
            }
        }
        if (series != null && !series.isEmpty()) {
            series.replaceAll("\\.,+", " ");
            series.replaceAll(" *", "+");
            try {
                extra += "&field-keywords=" + URLEncoder.encode(series, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error(e, "Unable to add series to URL");
                return null;
            }
        }
        return extra.trim();
    }

    private static String cleanupSearchString(@Nullable final String search) {
        if (search == null) {
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

    public static void openSearchPage(@NonNull final Activity activity,
                                      @Nullable final String author,
                                      @Nullable final String series) {
        try {
            openLink(activity, author, series);
        } catch (RuntimeException e) {
            // An Amazon error should not crash the app
            Logger.error(e, "Unable to call the Amazon API");
            UserMessage.showUserMessage(activity, R.string.error_unexpected_error);
            /* This code works, but Amazon have a nasty tendency to cancel Associate IDs... */
//            String baseUrl = "http://www.amazon.com/gp/search?"
//                    + "index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
//            String extra = buildSearchArgs(author, series);
//            if (extra != null && !extra.isEmpty()) {
//               activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra)));
//            }
        }
    }
}
