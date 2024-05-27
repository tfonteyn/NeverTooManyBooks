/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BolSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    /** one of {"","be","nl"}. */
    static final String PK_BOL_COUNTRY = EngineId.Bol.getPreferenceKey() + ".country";

    /** Website character encoding. */
    static final String CHARSET = "UTF-8";
    /**
     * Search using a text-string.
     * <p>
     * param 1: the country "be" or "nl"
     * param 2: words, separated by spaces
     */
    static final String BY_TEXT = "/%1$s/nl/s/?searchtext=%2$s";
    /**
     * Search using the ISBN.
     * <p>
     * param 1: the country "be" or "nl"
     * param 2: the isbn
     */
    private static final String BY_ISBN = "/%1$s/nl/s/?searchtext=+%2$s+";
    /** Front-covers can be given using either of these keys. We must try both. */
    private static final List<String> FRONT_COVER_KEYS = List.of("coverImageUrl", "imageUrl");

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BolSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    /**
     * Get the country for the website: nl or be.
     * By default we use the country the user is in, defined as either Belgium or
     * The Netherlands+rest-of-the-world.
     * The user can set their personal preference to BE or NL in the settings.
     *
     * @param context Current context
     *
     * @return "be" or "nl"
     */
    @NonNull
    static String getCountry(@NonNull final Context context) {
        String country = PreferenceManager.getDefaultSharedPreferences(context)
                                          .getString(PK_BOL_COUNTRY, null);
        if (country != null && !country.isEmpty()) {
            return country;
        } else {
            // Never configured, use the users actual country
            country = ServiceLocator.getInstance().getSystemLocaleList().get(0)
                                    .getCountry();
            if ("BE".equals(country)) {
                // Belgium
                return "be";
            } else {
                // The Netherlands + rest of the world.
                return "nl";
            }
        }
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // The site can display in French, but we always access the site in Dutch for now.
        // This should not cause any issue as searches show books in all languages.
        return new Locale("nl", getCountry(context).toUpperCase(Locale.ROOT));
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @Nullable final String code,
                       @Nullable final String author,
                       @Nullable final String title,
                       @Nullable final String publisher,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final StringJoiner words = new StringJoiner(" ");
        if (author != null && !author.isEmpty()) {
            words.add(author);
        }
        if (title != null && !title.isEmpty()) {
            words.add(title);
        }
        if (publisher != null && !publisher.isEmpty()) {
            words.add(publisher);
        }
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        final String url = getHostUrl(context) + String.format(BY_TEXT, getCountry(context), words);
        final Document document = loadDocument(context, url, null);
        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String url = getHostUrl(context) + String.format(BY_ISBN, getCountry(context),
                                                               validIsbn);
        final Document document = loadDocument(context, url, null);
        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @VisibleForTesting
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        final Element section = document.selectFirst("div.product-title--inline");
        // section will be null if there were no results.
        if (section != null) {
            // Grab the first search result, and redirect to that page
            final Element urlElement = section.selectFirst(
                    "a.product-title.px_list_page_product_click.list_page_product_tracking_target");
            if (urlElement != null) {
                String url = urlElement.attr("href");
                // sanity check - it normally does NOT have the protocol/site part
                if (url.startsWith("/")) {
                    url = getHostUrl(context) + url;
                }
                final Document redirected = loadDocument(context, url, null);
                if (!isCancelled()) {
                    parse(context, redirected, fetchCovers, book);
                }
            }
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     * <p>
     * We're ignoring the label "Co Auteur" and "Hoofdredacteur" on purpose.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element titleElement = document.selectFirst("span[data-test='title']");
        if (titleElement == null || titleElement.text().isEmpty()) {
            // well, this is unexpected...
            // This is seen when accessing the site in french and looking for
            // a dutch (or german...) book....
            // The site simply does not list the title... anywhere! ... ouch...
            return;
        }
        processText(titleElement, DBKey.TITLE, book);

        final Elements specs = document.select("div.specs > dl.specs__list");
        if (specs.isEmpty()) {
            return;
        }

        final RealNumberParser realNumberParser = getRealNumberParser(context, getLocale(context));

        for (final Element specRow : specs.select("div.specs__row")) {
            final Element label = specRow.selectFirst("dt.specs__title");
            final Element value = specRow.selectFirst("dd.specs__value");
            if (label != null && value != null) {
                switch (label.text()) {
                    case "Taal":
                    case "Langue": {
                        // the first occurrence uses the iso abbreviation
                        processText(value, DBKey.LANGUAGE, book);
                        break;
                    }
                    case "Bindwijze":
                    case "Binding": {
                        processText(value, DBKey.FORMAT, book);
                        break;
                    }
                    case "Oorspronkelijke releasedatum":
                    case "Date de sortie initiale": {
                        final String text = SearchEngineUtils.cleanText(value.text());
                        if (!text.isEmpty()) {
                            processPublicationDate(context, getLocale(context), text, book);
                        }
                        break;
                    }
                    case "Aantal pagina's":
                    case "Nombre de pages": {
                        processText(value, DBKey.PAGE_COUNT, book);
                        break;
                    }
                    case "Hoofdauteur":
                    case "Auteur principal":
                    case "Tweede Auteur":
                    case "Deuxième auteur": {
                        processAuthor(value, Author.TYPE_WRITER, book);
                        break;
                    }
                    case "Hoofdillustrator":
                    case "Illustrateur en chef":
                    case "Tweede Illustrator":
                    case "Deuxième illustrateur": {
                        processAuthor(value, Author.TYPE_ARTIST, book);
                        break;
                    }
                    case "Hoofdredacteur":
                    case "Rédacteur en chef":
                    case "Tweede Redacteur":
                    case "Deuxième rédacteur": {
                        processAuthor(value, Author.TYPE_EDITOR, book);
                        break;
                    }
                    case "Eerste Vertaler":
                    case "Tweede Vertaler":
                    case "Premier traducteur":
                    case "Deuxième traducteur": {
                        processAuthor(value, Author.TYPE_TRANSLATOR, book);
                        break;
                    }
                    case "Verteller":
                    case "Narrateur": {
                        processAuthor(value, Author.TYPE_NARRATOR, book);
                        break;
                    }
                    case "Originele titel":
                    case "Titre original": {
                        processText(value, SiteField.ORIGINAL_TITLE, book);
                        break;
                    }
                    case "Serie": {
                        // The series number is only available embedded in the title
                        // but without any specific structure to it.
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String text = a.text();
                            if (!text.isEmpty()) {
                                book.add(Series.from(text));
                            }
                        }
                        break;
                    }

                    case "Hoofduitgeverij":
                    case "Editeur principal": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String text = a.text();
                            if (!text.isEmpty()) {
                                book.add(Publisher.from(text));
                            }
                        }
                        break;
                    }
                    case "EAN": {
                        processText(value, DBKey.BOOK_ISBN, book);
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        parseDescription(document, book);
        parseRating(document, book, realNumberParser);
        parsePrice(document, book, realNumberParser);

        if (fetchCovers[0]) {
            parseCovers(context, document, fetchCovers, book);
        }
    }

    private void processAuthor(@NonNull final Element value,
                               @Author.Type final int type,
                               @NonNull final Book book) {
        final Element a = value.selectFirst("a");
        if (a != null) {
            processAuthor(Author.from(a.text()), type, book);
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        final Element descrElement = document.selectFirst("div.product-description");
        if (descrElement != null) {
            final String description = descrElement.text();
            if (!description.isEmpty()) {
                book.putString(DBKey.DESCRIPTION, description);
            }
        }
    }

    private void parseRating(@NonNull final Document document,
                             @NonNull final Book book,
                             final RealNumberParser realNumberParser) {
        final Element ratingElement = document.selectFirst("div.reviews-summary__avg-score");
        if (ratingElement != null) {
            //noinspection OverlyBroadCatchBlock
            try {
                final float rating = realNumberParser.parseFloat(ratingElement.text());
                book.putFloat(DBKey.RATING, rating);
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    private void parsePrice(@NonNull final Document document,
                            @NonNull final Book book,
                            @NonNull final RealNumberParser realNumberParser) {
        //TODO: if they are out of stock, this element will NOT contain a price.
        // We should get the price from the buttons on the page just above this field
        // but those button elements are not easy to parse for.
        final Element priceElement = document.selectFirst("span.promo-price");
        if (priceElement != null) {
            //noinspection OverlyBroadCatchBlock
            try {
                // <span class="promo-price" data-test="price">22
                //    <sup class="promo-price__fraction" data-test="price-fraction">99</sup>
                // </span>
                // text() will get "22 99", so add a "," as decimal separator and parse as normal
                final String priceText = priceElement.text().replace(" ", ",");
                final double price = realNumberParser.parseDouble(priceText);
                book.putMoney(DBKey.PRICE_LISTED,
                              new Money(BigDecimal.valueOf(price), Money.EURO));

            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    /**
     * Process a value which is pure text.
     *
     * @param value value element
     * @param key   for this field
     * @param book  Bundle to update
     */
    private void processText(@Nullable final Element value,
                             @NonNull final String key,
                             @NonNull final Book book) {
        // some 'specs' can appear more than once (e.g. "Taal")
        if (!book.contains(key)) {
            if (value != null) {
                final String text = SearchEngineUtils.cleanText(value.text());
                if (!text.isEmpty()) {
                    book.putString(key, text);
                }
            }
        }
    }

    /**
     * Parse the document for cover images.
     * <p>
     * Will NOT throw if the JSON objects are messed up; we just won't have an image.
     * <p>
     * Example of the text inside an "imageSlotConfig" if it's a JSONArray:
     * <pre>
     *     {@code
     * [
     *   {
     *     "type": "book-flipper",
     *     "coverImageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/550x766.jpg",
     *     "backImageUrl": "https://media.s-bol.com/y802R6lV9MnV/550x556.jpg",
     *     "hardcover": true,
     *     "flipBookText": "Boek omdraaien",
     *     "thickness": "medium",
     *     "m2": {
     *       "bltgiselecteditembookflippertemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10"
     *       },
     *       "bltghselecteditembookflippertemplate0FlipBookByBook": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10.FlipBookByBook"
     *       },
     *       "bltghselecteditembookflippertemplate1FlipBookByLink": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10.FlipBookByLink"
     *       }
     *     }
     *   },
     *   {
     *     "type": "image",
     *     "imageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/550x766.jpg",
     *     "productTitle": "nijntjes voorleesfeest",
     *     "zoomImageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/861x1200.jpg",
     *     "isHighPriorityEnabled": true,
     *     "m2": {
     *       "bltgiselecteditemimagetemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.11"
     *       }
     *     }
     *   },
     *   {
     *     "type": "image",
     *     "imageUrl": "https://media.s-bol.com/y802R6lV9MnV/550x556.jpg",
     *     "productTitle": "nijntjes voorleesfeest",
     *     "zoomImageUrl": "https://media.s-bol.com/y802R6lV9MnV/1186x1200.jpg",
     *     "isHighPriorityEnabled": true,
     *     "m2": {
     *       "bltgiselecteditemimagetemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.12"
     *       }
     *     }
     *   },
     *   {
     *     "type": "video",
     *     "imageUrl": "https://media.s-bol.com/mEW048AnXmQG/550x309.jpg",
     *     "videoUrl": "/nl/rnwy/ajax/video/product?productId=9200000122271922",
     *     "srtText": "Video afspelen",
     *     "m2": {
     *       "bltghselecteditemvideotemplate0StartVideo": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.13.StartVideo"
     *       },
     *       "bltgiselecteditemvideotemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.13"
     *       }
     *     }
     *   }
     * ]
     *     }
     * </pre>
     *
     * @param context           Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param book        to update
     *
     * @throws StorageException The covers directory is not available
     */
    private void parseCovers(@NonNull final Context context,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        final String isbn = book.getString(DBKey.BOOK_ISBN);

        final Element imageSlotConfig = document.selectFirst(
                "section[data-group-name='product-images'] script");
        if (imageSlotConfig != null
            && imageSlotConfig.hasAttr("data-image-slot-config")) {

            // The data of this element can contain a JSONArray or a JSONObject
            final String text = imageSlotConfig.data().strip();
            try {
                if (text.startsWith("[") && text.endsWith("]")) {
                    // If it's a JSONArray, simply grab the first element.
                    // This will either be a "book-flipper" with both front- and back-cover
                    // in the keys "coverImageUrl" and backImageUrl";
                    // or an "image" (or "video") with the front-cover in the key "imageUrl"
                    final JSONArray objects = new JSONArray(text);
                    final JSONObject currentItem = objects.optJSONObject(0);
                    if (currentItem != null) {
                        parseCovers(context, currentItem, isbn, fetchCovers, book);
                    }
                } else {
                    // TEST: This 'else' branch can likely be removed.
                    final JSONObject imageSlotSlider = new JSONObject(text)
                            .optJSONObject("imageSlotSlider");
                    if (imageSlotSlider != null) {
                        final JSONObject currentItem = imageSlotSlider.optJSONObject("currentItem");
                        if (currentItem != null) {
                            parseCovers(context, currentItem, isbn, fetchCovers, book);
                        }
                    }
                }
            } catch (@NonNull final JSONException e) {
                // Log it so we can extend the above check if needed.
                // There is more than one way of listing images...
                LoggerFactory.getLogger().w(TAG, e, "text=`" + text + "`");
            }
        }
    }

    private void parseCovers(@NonNull final Context context,
                             @NonNull final JSONObject currentItem,
                             @NonNull final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        // The site uses several possible keys, loop until found or exhausted
        for (final String key : FRONT_COVER_KEYS) {
            final String coverImageUrl = currentItem.optString(key);
            if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
                final Optional<String> fileSpec = saveImage(context, coverImageUrl, isbn, 0, null);
                if (fileSpec.isPresent()) {
                    CoverFileSpecArray.setFileSpec(book, 0, fileSpec.get());
                    // only attempt to get the back-cover if we got a front-cover
                    // and (obv.) we want one.
                    if (fetchCovers.length > 1 && fetchCovers[1]) {
                        final String url = currentItem.optString("backImageUrl");
                        if (url != null && !url.isEmpty()) {
                            saveImage(context, url, isbn, 1, null).ifPresent(
                                    fs -> CoverFileSpecArray.setFileSpec(book, 1, fs));
                        }
                    }
                    // All done. We have a front-cover and maybe a back-cover.
                    return;
                }
            }
        }
    }

    /**
     * BOL specific field names we add to the bundle based on parsed data.
     */
    public static final class SiteField {

        static final String ORIGINAL_TITLE = "__original_title";

        private SiteField() {
        }
    }
}
