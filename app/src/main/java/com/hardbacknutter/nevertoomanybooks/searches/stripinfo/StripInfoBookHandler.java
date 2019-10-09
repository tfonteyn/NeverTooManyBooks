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
import com.hardbacknutter.nevertoomanybooks.entities.Format;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

public class StripInfoBookHandler
        extends JsoupBase {

    public static final String FILENAME_SUFFIX = "_SI";

    /** Param 1: search criteria. */
    private static final String BOOK_SEARCH_URL = "/zoek/zoek?zoekstring=%1$s";

    private static final Pattern H4_OPEN_PATTERN = Pattern
            .compile("<h4>", Pattern.LITERAL);
    private static final Pattern H4_CLOSE_PATTERN = Pattern.compile("</h4>", Pattern.LITERAL);

    private final ArrayList<Author> mAuthors = new ArrayList<>();
    private final ArrayList<Series> mSeries = new ArrayList<>();
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();

    private String mPrimarySeriesTitle;
    private String mPrimarySeriesBookNr;

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
     * @param isbn           to search
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    public Bundle fetch(@NonNull final String isbn,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {

        String path = StripInfoManager.getBaseURL() + String.format(BOOK_SEARCH_URL, isbn);
        if (loadPage(path) == null) {
            return new Bundle();
        }

        Bundle bookData = new Bundle();
        return parseDoc(bookData, fetchThumbnail);
    }

    /**
     * Parses the downloaded {@link #mDoc}.
     *
     * @param bookData       a new Bundle()  (must be passed in so we can mock it in test)
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     */
    Bundle parseDoc(@NonNull final Bundle bookData,
                    @SuppressWarnings("SameParameterValue") final boolean fetchThumbnail) {

        Context context = App.getLocalizedAppContext();

        Elements rows = mDoc.select("div.row");

        for (Element row : rows) {

            if (mPrimarySeriesTitle == null) {
                Element seriesElement = mDoc.selectFirst("h1.c12");
                if (seriesElement != null) {
                    Element img = seriesElement.selectFirst("img");
                    mPrimarySeriesTitle = img.attr("alt");
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

            try {
                String titleUrl = titleElement.childNode(1).attr("href");
                // https://www.stripinfo.be/reeks/strip/336348_Hauteville_House_14_De_37ste_parallel
                String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1).split("_")[0];
                long book_id = Long.parseLong(idString);
                if (book_id > 0) {
                    bookData.putLong(DBDefinitions.KEY_STRIP_INFO_BE_ID, book_id);
                }
            } catch (Exception ignore) {

            }

            String title = titleElement.childNode(1).childNode(0).toString().trim();
            bookData.putString(DBDefinitions.KEY_TITLE, title);

            Elements tds = row.select("td");
            for (int i = 0; i < tds.size(); i++) {
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
                        Format.map(context, bookData);
                        break;

                    case "Taal":
                        i += processText(td, DBDefinitions.KEY_LANGUAGE, bookData);
                        String lang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
                        if (lang != null && !lang.isEmpty()) {
                            lang = LanguageUtils.getIso3fromDisplayName(lang);
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
                        i += processText(td, StripInfoField.BARCODE, bookData);
                        break;

                    case "&nbsp;":
                        i += processEmptyLabel(td, bookData);
                        break;

                    default:
                        Logger.debug(this, "parseDoc",
                                     "unknown label=" + label);
                }
            }
            // quit the for loop
            break;
        }

        // process the description
        Element item = mDoc.selectFirst("div.item");
        if (item != null) {
            Elements sections = item.select("section.c4");
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < sections.size(); i++) {
                Element sectionElement = sections.get(i);
                String tmp = H4_OPEN_PATTERN.matcher(sectionElement.html())
                                            .replaceAll(Matcher.quoteReplacement("<b>"));
                content.append(H4_CLOSE_PATTERN.matcher(tmp)
                                               .replaceAll(Matcher.quoteReplacement("</b>")));
                if (i < sections.size() - 1) {
                    content.append("<br><br>");
                }
            }
            bookData.putString(DBDefinitions.KEY_DESCRIPTION, content.toString());
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
            String coverUrl = coverElement.attr("data-ajax-url");

            fetchCover(coverUrl, bookData);
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
        String isbn = bookData.getString(DBDefinitions.KEY_ISBN, "");
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

    private int processText(@NonNull final Element td,
                            @NonNull final String key,
                            @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            String text = dataElement.childNode(0).toString().trim();
            bookData.putString(key, text);
            return 1;
        }
        return 0;
    }

    private int processEmptyLabel(@NonNull final Element td,
                                  @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            String text = dataElement.childNode(0).toString().trim();
            if ("Kleur".equals(text) || "Zwart/wit".equals(text)) {
                bookData.putString(StripInfoField.COLOR, text);
            }
            return 1;
        }
        return 0;
    }

    private int processAuthor(@NonNull final Element td,
                              final int authorType) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Element a = aas.get(i);

                Node nameNode = a.childNode(0);
                if (nameNode != null) {
                    String name = nameNode.toString();
                    Author author = Author.fromString(name);
                    author.setType(authorType);
                    mAuthors.add(author);
                }
            }
            return 1;
        }
        return 0;
    }

    private int processSeries(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Element a = aas.get(i);

                Node nameNode = a.childNode(0);
                if (nameNode != null) {
                    String name = nameNode.toString();
                    Series series = Series.fromString(name);
                    mSeries.add(series);
                }
            }
            return 1;
        }
        return 0;
    }

    private int processPublisher(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                Element a = aas.get(i);

                Node nameNode = a.childNode(0);
                if (nameNode != null) {
                    String name = nameNode.toString();
                    Publisher publisher = Publisher.fromString(name);
                    mPublishers.add(publisher);
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

        public static final String COLOR = "__color";
        public static final String BARCODE = "__barcode";
        public static final String OPLAGE = "__oplage";

        private StripInfoField() {
        }
    }

}
