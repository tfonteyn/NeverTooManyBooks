package com.eleybourn.bookcatalogue.searches.amazon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.amazon.device.associates.AssociatesAPI;
import com.amazon.device.associates.LinkService;
import com.amazon.device.associates.NotInitializedException;
import com.amazon.device.associates.OpenSearchPageRequest;
import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Provides the {@link #open(Activity, String, String)} api.
 * This class is used by the (sub) menu "Search Amazon" to look for authors, series.
 * <p>
 * It is NOT used by the ISBN lookup/search engine,
 * see {@link AmazonManager#search(String, String, String, boolean)}
 * <p>
 * <p>
 * Either opening a link via the Amazon API (if we have a key) or open a web page.
 * <p>
 * NOTE: The project manifest must contain the app key granted by Amazon.
 * For testing purposes this key can be empty; it'll always use the web site directly in this case.
 *
 * @author pjw
 */
public final class AmazonSearchPage {

    private static final String SUFFIX_BASE_URL = "/gp/search?index=books";
    // affiliate link for the original developers.
    private static final String SUFFIX_EXTRAS = "&t=bookcatalogue-20&linkCode=da5";

    /** key into the Manifest meta-data. */
    private static final String AMAZON_KEY = "amazon.app_key";
    /** Amazon app key. Will be empty if there was no key. */
    @NonNull
    private static final String mAmazonAppKey = App.getManifestString(AMAZON_KEY);

    private AmazonSearchPage() {
    }

    /**
     * @param author to search for
     * @param series to search for
     */
    public static void open(@NonNull final Activity activity,
                            @Nullable final String author,
                            @Nullable final String series) {

        try {
            String cAuthor = cleanupSearchString(author);
            String cSeries = cleanupSearchString(series);

            // if no key, don't even bother with the AssociatesAPI.
            if (mAmazonAppKey.isEmpty()) {
                openIntent(activity, buildUrl(cAuthor, cSeries));
            } else {
                openLink(activity, cAuthor, cSeries);
            }

        } catch (RuntimeException e) {
            // An Amazon error should not crash the app
            Logger.error(AmazonSearchPage.class, e, "Unable to call the Amazon API");
            UserMessage.showUserMessage(activity, R.string.error_unexpected_error);
        }
    }

    /**
     * Start an intent to open a web page e.g. start a browser.
     *
     * @param context caller context
     * @param url     url to open
     */
    private static void openIntent(@NonNull final Context context,
                                   @NonNull final String url) {
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url + SUFFIX_EXTRAS)));
    }

    /**
     * Use Amazon AssociatesAPI to open the search.
     *
     * @param context caller context
     * @param author  to search for
     * @param series  to search for
     */
    private static void openLink(@NonNull final Context context,
                                 @Nullable final String author,
                                 @Nullable final String series) {

        String url = buildUrl(author, series);
        try {
            AssociatesAPI.initialize(new AssociatesAPI.Config(mAmazonAppKey, context));
            LinkService linkService = AssociatesAPI.getLinkService();

            try {
                linkService.overrideLinkInvocation(new WebView(context), url);

            } catch (RuntimeException e) {
                Logger.error(AmazonSearchPage.class, e);
                linkService.openRetailPage(
                        new OpenSearchPageRequest("books", author + ' ' + series));
            }
        } catch (IllegalArgumentException | NotInitializedException e) {
            Logger.error(AmazonSearchPage.class, e);
            openIntent(context, url);
        }
    }

    /**
     * @param author to search for
     * @param series to search for
     *
     * @return the search url, at worst the Amazon search home page.
     */
    private static String buildUrl(@Nullable final String author,
                                   @Nullable final String series) {
        String extra = "";
        if (author != null && !author.isEmpty()) {
            try {
                extra += "&field-author=" + URLEncoder.encode(author, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error(AmazonSearchPage.class, e, "Unable to add author to URL");
            }
        }

        if (series != null && !series.isEmpty()) {
            try {
                extra += "&field-keywords=" + URLEncoder.encode(series, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error(AmazonSearchPage.class, e, "Unable to add series to URL");
            }
        }

        return AmazonManager.getBaseURL() + SUFFIX_BASE_URL + extra.trim();
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
}
