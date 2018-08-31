package com.eleybourn.bookcatalogue.searches.amazon;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
 *
 */
public class AmazonUtils {

	private static final String AMAZON_LINK_EXTRAS = "&tag=bookcatalogue-20&linkCode=da5";
	private static final String AMAZON_BOOKS_BASE = "http://www.amazon.com/gp/search?index=books";
	private static final String AMAZON_KEY = "bookcatalogue.amazon.appkey";

	private static final String UTF8 = "UTF-8";

	private static void openLink(Activity context, String author, String series) {
		// Build the URL and args
		String url = AMAZON_BOOKS_BASE;
		
		author = cleanupSearchString(author);
		series = cleanupSearchString(series);

		String extra = buildSearchArgs(author, series);

		if (extra != null && !extra.trim().isEmpty()) {
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
			} catch(Exception e2) {
				OpenSearchPageRequest request = new OpenSearchPageRequest("books", author + " " + series);
				linkService.openRetailPage(request);				
			}
		} catch (Exception e) {
			Logger.logError(e, "Unable to use Amazon API");
			Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(url + AMAZON_LINK_EXTRAS));
			context.startActivity(loadweb);					
		}
	}

	private static String buildSearchArgs(String author, String series) {
		// This code works, but Amazon have a nasty tendency to cancel Associate IDs...
		//String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
		String extra = "";
		// http://www.amazon.com/gp/search?index=books&field-author=steven+a.+mckay&field-keywords=the+forest+lord
		if (author != null && !author.trim().isEmpty()) {
			//FIXME: the replaceAll call in fact discard the result....
			//FIXME: you need to:   s = s.replaceAll  to have them take effect
			//FIXME: not fixing this until the code/reason of the replace is understood.
			author.replaceAll("\\.,+"," ");
			author.replaceAll(" *","+");
			try {
				extra += "&field-author=" + URLEncoder.encode(author, UTF8);
			} catch (UnsupportedEncodingException e) {
				Logger.logError(e, "Unable to add author to URL");
				return null;
			}
		}
		if (series != null && !series.trim().isEmpty()) {
			series.replaceAll("\\.,+"," ");
			series.replaceAll(" *","+");
			try {
				extra += "&field-keywords=" + URLEncoder.encode(series, UTF8);
			} catch (UnsupportedEncodingException e) {
				Logger.logError(e, "Unable to add series to URL");
				return null;
			}
		}
		return extra;
		//if (extra != null && !extra.trim().isEmpty()) {
		//	Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
		//	context.startActivity(loadweb); 			
		//}			
		
	}
	
	private static String cleanupSearchString(String search) {
		if (search == null)
			return "";

		StringBuilder out = new StringBuilder(search.length());
		char prev = ' ';
		for(char curr: search.toCharArray()) {
			if (Character.isLetterOrDigit(curr) ) {
				out.append(curr);
			} else {
				curr = ' ';
				if (!Character.isWhitespace(prev)) {
					out.append(curr);
				}
			}
			prev = curr;
		}
		return out.toString();
	}

	public static void openAmazonSearchPage(Activity context, String author, String series) {

		try {
			openLink(context, author, series);
		} catch(Exception ae) {
			// An Amazon error should not crash the app
			Logger.logError(ae, "Unable to call the Amazon API");
			Toast.makeText(context, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			// This code works, but Amazon have a nasty tendency to cancel Associate IDs...
			//String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
			//String extra = buildSearchArgs(author, series);
			//if (extra != null && !extra.trim().equals("")) {
			//	Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
			//	context.startActivity(loadweb);
			//}
		}
		return;
	}
}
