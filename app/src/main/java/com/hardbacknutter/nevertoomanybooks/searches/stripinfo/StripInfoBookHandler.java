/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.stripinfo;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

public class StripInfoBookHandler
        extends JsoupBase {

    public static final String FILENAME_SUFFIX = "_SI";
    private static final String TAG = "StripInfoBookHandler";
    /** Color string values as used on the site. Complete 2019-10-29. */
    private static final String COLOR_STRINGS = "Kleur|Zwart/wit|Zwart/wit met steunkleur";

    /** Param 1: search criteria. */
    private static final String BOOK_SEARCH_URL = "/zoek/zoek?zoekstring=%1$s";

    /** Param 1: the native id; really a 'long'. */
    private static final String BOOK_BY_NATIVE_ID = "/reeks/strip/%1$s";


    /** The description contains h4 tags which we need to remove. */
    private static final Pattern H4_OPEN_PATTERN = Pattern.compile("<h4>", Pattern.LITERAL);
    private static final Pattern H4_CLOSE_PATTERN = Pattern.compile("</h4>", Pattern.LITERAL);
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&amp;", Pattern.LITERAL);
    /** accumulate all Authors for this book. */
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();
    /** extracted from the page header. */
    private String mPrimarySeriesTitle;
    /** extracted from the title section. */
    private String mPrimarySeriesBookNr;
    private String mIsbn;

    /**
     * Constructor.
     */
    StripInfoBookHandler() {
    }

    /**
     * Constructor for mocking.
     *
     * @param doc the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    StripInfoBookHandler(@NonNull final Document doc) {
        super(doc);
    }

    /**
     * Fetch a book.
     *
     * @param context        Current context
     * @param isbn           to search
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    public Bundle fetch(@NonNull final Context context,
                        @NonNull final String isbn,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {
        // keep for reference
        mIsbn = isbn;

        Bundle bookData = new Bundle();

        String path = StripInfoManager.getBaseURL(context)
                      + String.format(BOOK_SEARCH_URL, isbn);
        if (loadPage(path) == null) {
            return bookData;
        }

        if (mDoc.title().startsWith("Zoeken naar")) {
            // handle multi-results page.
            return parseMultiResult(context, bookData, fetchThumbnail);
        }

        return parseDoc(context, bookData, fetchThumbnail);
    }

    @NonNull
    Bundle fetchByNativeId(@NonNull final Context context,
                           @NonNull final String nativeId,
                           final boolean fetchThumbnail)
            throws SocketTimeoutException {

        Bundle bookData = new Bundle();

        String path = StripInfoManager.getBaseURL(context)
                      + String.format(BOOK_BY_NATIVE_ID, nativeId);
        if (loadPage(path) == null) {
            return bookData;
        }

        return parseDoc(context, bookData, fetchThumbnail);
    }

    /**
     * Fetch a book.
     *
     * @param context        Current context
     * @param path           to load
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    Bundle fetchByPath(@NonNull final Context context,
                       @NonNull final String path,
                       final boolean fetchThumbnail)
            throws SocketTimeoutException {

        if (loadPage(path) == null) {
            return new Bundle();
        }

        Bundle bookData = new Bundle();

        if (mDoc.title().startsWith("Zoeken naar")) {
            // prevent looping.
            return bookData;
        }

        return parseDoc(context, bookData, fetchThumbnail);
    }

    /**
     * Parses the downloaded {@link #mDoc}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context        Current context
     * @param bookData       a new Bundle()  (must be passed in so we can mock it in test)
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     */
    Bundle parseDoc(@NonNull final Context context,
                    @NonNull final Bundle bookData,
                    @SuppressWarnings("SameParameterValue") final boolean fetchThumbnail) {

        Elements rows = mDoc.select("div.row");

        for (Element row : rows) {
            // this code is not 100% foolproof yet, so surround with try/catch.
            try {
                if (mPrimarySeriesTitle == null) {
                    Element seriesElement = mDoc.selectFirst("h1.c12");
                    // Two possibilities:
                    // <h1 class="c12">
                    // <a href="https://www.stripinfo.be/reeks/index/831_Capricornus">
                    // <img src="https://www.stripinfo.be/images/images/380000/381645.gif"
                    //      alt="Capricornus">
                    // </a>
                    // </h1>
                    // or:
                    // <h1 class="c12">
                    // <a href="https://www.stripinfo.be/reeks/index/632_Coutoo">
                    //    Coutoo
                    // </a>
                    // </h1>
                    if (seriesElement != null) {
                        // 2019-10-11: "img" is the norm with "a" being the exception.
                        Element img = seriesElement.selectFirst("img");
                        if (img != null) {
                            mPrimarySeriesTitle = img.attr("alt");
                        } else {
                            Element a = seriesElement.selectFirst("a");
                            if (a != null) {
                                Node aNode = a.childNode(0);
                                if (aNode != null) {
                                    mPrimarySeriesTitle = aNode.toString();
                                }
                            }
                        }
                    }
                }

                // use the title element to determine we are in a book row.
                // but don't use it, we get title etc from the location url
                Element titleElement = row.selectFirst("h2.title");
                if (titleElement == null) {
                    // not a book
                    continue;
                }

                mPrimarySeriesBookNr = titleElement.childNode(0).toString().trim();

                // extract the site native id from the url
                try {
                    String titleUrl = titleElement.childNode(1).attr("href");
                    // https://www.stripinfo.be/reeks/strip/
                    // 336348_Hauteville_House_14_De_37ste_parallel
                    String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1)
                                              .split("_")[0];
                    long bookId = Long.parseLong(idString);
                    if (bookId > 0) {
                        bookData.putLong(DBDefinitions.KEY_EID_STRIP_INFO_BE, bookId);
                    }
                } catch (@NonNull final NumberFormatException ignore) {

                }

                bookData.putString(DBDefinitions.KEY_TITLE,
                                   cleanText(titleElement.childNode(1).childNode(0)));

                Elements tds = row.select("td");
                int i = 0;
                while (i < tds.size()) {
                    Element td = tds.get(i);
                    String label = td.childNode(0).toString();

                    switch (label) {
                        case "Scenario":
                            i += processAuthor(td, Author.TYPE_WRITER);
                            break;

                        case "Tekeningen":
                            i += processAuthor(td, Author.TYPE_ARTIST);
                            break;

                        case "Kleuren":
                            i += processAuthor(td, Author.TYPE_COLORIST);
                            break;

                        case "Cover":
                            i += processAuthor(td, Author.TYPE_COVER_ARTIST);
                            break;

                        case "Vertaling":
                            i += processAuthor(td, Author.TYPE_TRANSLATOR);
                            break;

                        case "Uitgever(s)":
                            i += processPublisher(td);
                            break;

                        case "Jaar":
                            i += processText(td, DBDefinitions.KEY_DATE_PUBLISHED, bookData);
                            break;

                        case "Pagina's":
                            i += processText(td, DBDefinitions.KEY_PAGES, bookData);
                            break;

                        case "ISBN":
                            i += processText(td, DBDefinitions.KEY_ISBN, bookData);
                            break;

                        case "Kaft":
                            i += processText(td, DBDefinitions.KEY_FORMAT, bookData);
                            break;

                        case "Taal":
                            i += processText(td, DBDefinitions.KEY_LANGUAGE, bookData);
                            String lang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
                            if (lang != null && !lang.isEmpty()) {
                                // the language is a 'DisplayName' so convert to iso first.
                                lang = LanguageUtils.getISO3FromDisplayName(context, lang);
                                bookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                            }
                            break;

                        case "Collectie":
                            i += processSeries(td);
                            break;

                        case "Oplage":
                            i += processText(td, DBDefinitions.KEY_PRINT_RUN, bookData);
                            break;

                        case "Barcode":
                            i += processText(td, StripInfoField.EAN13, bookData);
                            break;

                        case "&nbsp;":
                            i += processEmptyLabel(td, bookData);
                            break;

                        case "Cycli":
                            // not currently used. Example: Cyclus 2 nr. 1
                            // This is sub-series 2, book 1, inside a series.
                            break;

                        default:
                            Log.d(TAG, "parseDoc|unknown label=" + label);
                    }
                    i++;
                }
                // quit the for(Element row : rows)
                break;

            } catch (@NonNull final Exception e) {
                // log, abandon row, and try next row.
                Logger.error(App.getAppContext(), TAG, e, "mIsbn=" + mIsbn);
            }
        }

        // process the description
        Element item = mDoc.selectFirst("div.item");
        if (item != null) {
            Elements sections = item.select("section.c4");
            if (sections != null && !sections.isEmpty()) {
                StringBuilder content = new StringBuilder();
                for (int i = 0; i < sections.size(); i++) {
                    Element sectionElement = sections.get(i);
                    // a section usually has 'h4' tags, replace with 'b' and add a line feed 'br'
                    String tmp = H4_OPEN_PATTERN.matcher(sectionElement.html())
                                                .replaceAll(Matcher.quoteReplacement("<b>"));
                    content.append(H4_CLOSE_PATTERN.matcher(tmp)
                                                   .replaceAll(
                                                           Matcher.quoteReplacement("</b><br>")));
                    if (i < sections.size() - 1) {
                        // separate multiple sections
                        content.append("<br><br>");
                    }
                }
                if (content.length() > 0) {
                    bookData.putString(DBDefinitions.KEY_DESCRIPTION, content.toString());
                }
            }
        }

        if (mPrimarySeriesTitle != null && !mPrimarySeriesTitle.isEmpty()) {
            Series series = Series.fromString(mPrimarySeriesTitle);
            series.setNumber(mPrimarySeriesBookNr);
            mSeries.add(0, series);
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }
        if (!mPublishers.isEmpty()) {
//            bookData.putParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY, mPublishers);
            bookData.putString(DBDefinitions.KEY_PUBLISHER, mPublishers.get(0).getName());
        }

        if (fetchThumbnail) {
            Element coverElement = mDoc.selectFirst("a.stripThumb");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("data-ajax-url");
                // if the site has no image: https://www.stripinfo.be/image.php?i=0
                if (coverUrl != null && !coverUrl.isEmpty() && !coverUrl.endsWith("i=0")) {
                    fetchCover(coverUrl, bookData);
                }
            }
        }

        return bookData;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retries.
     *
     * @param context        Current context
     * @param bookData       a new Bundle()  (must be passed in so we can mock it in test)
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out
     */
    Bundle parseMultiResult(@NonNull final Context context,
                            @NonNull final Bundle bookData,
                            @SuppressWarnings("SameParameterValue") final boolean fetchThumbnail)
            throws SocketTimeoutException {

        Elements sections = mDoc.select("section.c6");
        if (sections != null) {
            for (Element section : sections) {
                Elements urls = section.select("a");
                if (urls != null) {
                    for (Element url : urls) {
                        // <a href="https://stripinfo.be/reeks/index/481_Het_narrenschip">Het narrenschip</a>
                        // <a href="https://stripinfo.be/reeks/strip/1652_Het_narrenschip_2_Pluvior_627">Pluvior 627</a>
                        String href = url.attr("href");
                        if (href.contains("/strip/")) {
                            mDoc = null;
                            return fetchByPath(context, href, fetchThumbnail);
                        }
                    }
                }
            }
        }

        return bookData;
    }

    /**
     * Fetch the cover from the url. Uses the ISBN (if any) from the bookData bundle.
     * <p>
     * <img src="https://www.stripinfo.be/image.php?i=437246&amp;s=348664" srcset="
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=440 311w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=380 269w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=310 219w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=270 191w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=250 177w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=200 142w
     * " alt="Uitvergroten" class="">
     * <p>
     * https://www.stripinfo.be/image.php?i=437246&s=348664
     *
     * @param coverUrl fully qualified url
     * @param bookData destination bundle
     */
    private void fetchCover(@NonNull final String coverUrl,
                            @NonNull final Bundle /* in/out */ bookData) {
        // do not use the isbn we searched for, use the one we found even if empty!
        String isbn = bookData.getString(DBDefinitions.KEY_ISBN, "");
        // download
        String fileSpec = ImageUtils.saveImage(coverUrl, isbn, FILENAME_SUFFIX, null);

        if (fileSpec != null) {
            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList == null) {
                imageList = new ArrayList<>();
            }
            imageList.add(fileSpec);
            bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
        }
    }

    /**
     * Process a td which is pure text.
     *
     * @param td       label td
     * @param key      for this field
     * @param bookData bundle to add to
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processText(@NonNull final Element td,
                            @NonNull final String key,
                            @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            bookData.putString(key, cleanText(dataElement.childNode(0)));
            return 1;
        }
        return 0;
    }

    private String cleanText(@NonNull final Node node) {
        String text = node.toString().trim();
        // add more rules when needed.
        if (text.contains("&")) {
            text = AMPERSAND_PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }
        return text;
    }

    /**
     * At least one element does not have an actual label.
     * We inspect the value to try and guess the type.
     * <p>
     * Currently known (2019-10-11):
     * - the color scheme of the comic.
     *
     * @param td       label td
     * @param bookData bundle to add to
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processEmptyLabel(@NonNull final Element td,
                                  @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            String text = dataElement.childNode(0).toString().trim();

            // is it a color ?
            if (COLOR_STRINGS.contains(text)) {
                bookData.putString(DBDefinitions.KEY_COLOR, text);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found an Author.
     *
     * @param td                label td
     * @param currentAuthorType of this entry
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processAuthor(@NonNull final Element td,
                              final int currentAuthorType) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Node nameNode = aas.get(i).childNode(0);
                if (nameNode != null) {
                    String name = nameNode.toString();
                    Author currentAuthor = Author.fromString(name);
                    // check if already present
                    for (Author author : mAuthors) {
                        if (author.equals(currentAuthor)) {
                            author.addType(currentAuthorType);
                            return 1;
                        }
                    }
                    // just add
                    currentAuthor.setType(currentAuthorType);
                    mAuthors.add(currentAuthor);
                }
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found a Series.
     *
     * @param td label td
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processSeries(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Node nameNode = aas.get(i).childNode(0);
                if (nameNode != null) {
                    String name = cleanText(nameNode);
                    Series currentSeries = Series.fromString(name);
                    // check if already present
                    for (Series series : mSeries) {
                        if (series.equals(currentSeries)) {
                            return 1;
                        }
                    }
                    // just add
                    mSeries.add(currentSeries);
                }
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found a Publisher.
     *
     * @param td label td
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processPublisher(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Node nameNode = aas.get(i).childNode(0);
                if (nameNode != null) {
                    String name = cleanText(nameNode);
                    Publisher currentPublisher = Publisher.fromString(name);
                    // check if already present
                    for (Publisher publisher : mPublishers) {
                        if (publisher.equals(currentPublisher)) {
                            return 1;
                        }
                    }
                    // just add
                    mPublishers.add(currentPublisher);
                }
            }
            return 1;
        }
        return 0;
    }

    /**
     * StripInfo specific field names we add to the bundle based on parsed XML data.
     */
    public static final class StripInfoField {

        /** The barcode (i.e. the EAN code) is not always an ISBN. */
        public static final String EAN13 = "__ean13";

        private StripInfoField() {
        }
    }

}
