/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookParserHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parse one or more "Publication" records.
 *
 * <pre>
 *     {@code
 *
 * <?xml version="1.0" encoding="iso-8859-1" ?>
 * <ISFDB>
 *  <Records>1</Records>
 *  <Publications>
 *      <Publication>
 *          <Record>425189</Record>
 *          <Title>Triplanetary</Title>
 *          <Authors>
 *              <Author>E. E. &apos;Doc&apos; Smith</Author>
 *          </Authors>
 *          <Year>1971-02-15</Year>
 *          <Isbn>0491001576</Isbn>
 *          <Publisher>W. H. Allen</Publisher>
 *          <Price>£1.75</Price>
 *          <Pages>254</Pages>
 *          <Binding>hc</Binding>
 *          <Type>NOVEL</Type>
 *          <Tag>TRPLNTRLBK1971</Tag>
 *          <Image>http://www.isfdb.org/wiki/images/4/46/TRPLNTRLBK1971.jpg</Image>
 *          <CoverArtists>
 *              <Artist>Ken Reilly</Artist>
 *          </CoverArtists>
 *          <Note>Data from OCLC.
 *              Publication date from Amazon.co.uk
 *          </Note>
 *          <External_IDs>
 *              <External_ID>
 *                  <IDtype>12</IDtype>
 *                  <IDtypeName>OCLC/WorldCat</IDtypeName>
 *                  <IDvalue>16190406</IDvalue>
 *              </External_ID>
 *          </External_IDs>
 *      </Publication>
 *  </Publications>
 *  </ISFDB>
 *     }
 * </pre>
 */
class IsfdbPublicationListHandler
        extends DefaultHandler {

    private static final String XML_PUBLICATION = "Publication";
    private static final String XML_AUTHORS = "Authors";
    private static final String XML_COVER_ARTISTS = "CoverArtists";
    private static final String XML_EXTERNAL_IDS = "External_IDs";
    private static final String XML_EXTERNAL_ID = "External_ID";
    private static final String XML_RECORDS = "Records";
    private static final String XML_RECORD = "Record";
    private static final String XML_TITLE = "Title";
    private static final String XML_AUTHOR = "Author";
    private static final String XML_YEAR = "Year";
    private static final String XML_ISBN = "Isbn";
    private static final String XML_CATALOG = "Catalog";
    private static final String XML_PUBLISHER = "Publisher";
    private static final String XML_PUB_SERIES = "PubSeries";
    private static final String XML_PUB_SERIES_NUM = "PubSeriesNum";
    private static final String XML_PRICE = "Price";
    private static final String XML_PAGES = "Pages";
    private static final String XML_BINDING = "Binding";
    private static final String XML_TYPE = "Type";
    private static final String XML_TAG = "Tag";
    private static final String XML_IMAGE = "Image";
    private static final String XML_ARTIST = "Artist";
    private static final String XML_NOTE = "Note";
    private static final String XML_ID_TYPE = "IDtype";
    private static final String XML_ID_VALUE = "IDvalue";

    /**
     * Key: the codes used by the ISFDB website in table "identifier_types".
     */
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("1", Identifier.SID_ASIN),
            Map.entry("2", Identifier.SID_BRITISH_LIBRARY),
            Map.entry("3", "bnb"),
            Map.entry("4", Identifier.SID_BNF),
            // 5 COPAC (defunct)
            Map.entry("6", Identifier.SID_DNB),
            Map.entry("7", Identifier.SID_FANTLAB),
            Map.entry("8", Identifier.SID_GOODREADS),
            Map.entry("9", "jpno"),

            Map.entry("10", Identifier.SID_LCCN),
            Map.entry("11", "ndl"),
            Map.entry("12", Identifier.SID_OCLC),
            Map.entry("13", Identifier.SID_OPEN_LIBRARY),
            Map.entry("14", "sfbg"),
            Map.entry("15", Identifier.SID_BARNES_AND_NOBLE),
            Map.entry("16", Identifier.SID_KBNL),
            Map.entry("17", Identifier.SID_AUDIBLE),
            Map.entry("18", Identifier.SID_TERCERA_FUNDACION),
            Map.entry("19", Identifier.SID_KBR),

            Map.entry("25", Identifier.SID_NILF),
            Map.entry("26", Identifier.SID_NOOSFERE),
            Map.entry("27", "sf-leihbuch"),
            Map.entry("28", "nla"),
            Map.entry("29", Identifier.SID_PORBASE),

            Map.entry("30", Identifier.SID_LIBRIS),
            Map.entry("31", Identifier.SID_LIBRIS_XL),
            Map.entry("32", "biblioman"),
            Map.entry("33", "cobiss.bg"),
            Map.entry("34", "cobiss.sr"),
            Map.entry("35", "fmi")
    );
    @NonNull
    private final Context context;
    @NonNull
    private final BookParserHelper bookParserHelper;
    @NonNull
    private final IsfdbSearchEngine searchEngine;
    @NonNull
    private final boolean[] fetchCovers = new boolean[DBKey.NR_OF_BOOK_COVERS];
    /** XML content. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    /** The resulting list of books collected by this class. */
    @NonNull
    private final List<Book> bookList = new ArrayList<>();
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();
    private final Locale siteLocale;
    private int maxRecords;
    private boolean inPublication;
    /** The current book we're parsing data for. Will be added to the {@link #bookList}. */
    private Book book;
    private boolean inAuthors;
    private boolean inCoverArtists;
    private boolean inExternalIds;
    private boolean inExternalId;

    @Nullable
    private String externalIdType;
    @Nullable
    private String externalId;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchEngine to use
     * @param fetchCovers  Set array indexes to {@code true} to fetch a cover for that index.
     *                     Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param maxRecords   the maximum number of "Publication" records to fetch
     */
    IsfdbPublicationListHandler(@NonNull final Context context,
                                @NonNull final IsfdbSearchEngine searchEngine,
                                @NonNull final boolean[] fetchCovers,
                                final int maxRecords) {
        this.context = context;
        this.searchEngine = searchEngine;
        this.siteLocale = searchEngine.getLocale(context);
        this.bookParserHelper = searchEngine.getParserHelper();

        System.arraycopy(fetchCovers, 0, this.fetchCovers, 0, fetchCovers.length);
        this.maxRecords = maxRecords;
    }

    @NonNull
    public List<Book> getResult() {
        return bookList;
    }

    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length) {
        builder.append(ch, start, length);
    }

    /**
     * Start each XML element. Specifically identify when we are in the item
     * element and set the appropriate flag.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {
        switch (qName) {
            case XML_PUBLICATION:
                inPublication = true;
                book = new Book();
                break;

            case XML_AUTHORS:
                inAuthors = true;
                break;
            case XML_COVER_ARTISTS:
                inCoverArtists = true;
                break;
            case XML_EXTERNAL_IDS:
                inExternalIds = true;
                break;
            case XML_EXTERNAL_ID:
                inExternalId = true;
                break;

            default:
                break;
        }
    }

    /**
     * Populate the results Bundle for each appropriate element.
     *
     * @throws SAXException with potentially embedded
     *                      {@link EOFException}: NOT AN ERROR but means parsing is done here.
     *                      {@link StorageException} as an error
     */
    @SuppressWarnings("NewExceptionWithoutArguments")
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        if (XML_RECORDS.equals(qName)) {
            // Top level number of Publication records
            final String tmpString = builder.toString().strip();
            try {
                final int n = Integer.parseInt(tmpString);
                if (n < maxRecords) {
                    maxRecords = n;
                }
            } catch (@NonNull final NumberFormatException e) {
                throw new SAXException(new EOFException());
            }

        } else if (XML_PUBLICATION.equals(qName)) {
            // ISFDB does not provide the books language in xml
            //ENHANCE: the "title" page has the language, but that's an extra GET call.
            // For now, default to English
            book.setLanguage("eng");

            inPublication = false;
            bookList.add(book);
            if (bookList.size() == maxRecords) {
                // we're done
                throw new SAXException(new EOFException());
            }

        } else if (inPublication) {
            switch (qName) {
                case XML_AUTHORS: {
                    inAuthors = false;
                    break;
                }
                case XML_COVER_ARTISTS: {
                    inCoverArtists = false;
                    break;
                }
                case XML_EXTERNAL_IDS: {
                    inExternalIds = false;
                    break;
                }

                case XML_RECORD: {
                    book.setIdentifierValue(Identifier.SID_ISFDB, builder.toString().strip());
                    break;
                }
                case XML_TITLE: {
                    book.setTitle(builder.toString().strip());
                    break;
                }
                case XML_AUTHOR: {
                    if (inAuthors) {
                        final String s = SearchEngineUtils.cleanName(builder.toString());
                        bookParserHelper.addAuthor(Author.from(s), AuthorRole.UNKNOWN, book, false);
                    }
                    break;
                }
                case XML_YEAR: {
                    if (!book.contains(DBKey.PUBLICATION_DATE)) {
                        final String dateStr = builder.toString().strip();
                        // Ignore the locale, the format is always iso
                        partialDateParser.parse(dateStr)
                                         .ifPresent(book::setPublicationDate);
                    }
                    break;
                }
                case XML_ISBN: {
                    book.setRawProductCode(ISBN.cleanText(builder.toString().strip()));
                    break;
                }
                case XML_CATALOG: {
                    // keep the ISBN if we have one, otherwise add the catalog id
                    if (!book.contains(DBKey.ISBN)) {
                        book.setRawProductCode(builder.toString().strip());
                    }
                    break;
                }
                case XML_PUBLISHER: {
                    final String s = SearchEngineUtils.cleanName(builder.toString());
                    final Publisher publisher = Publisher.from(s);
                    book.add(publisher);
                    break;
                }
                case XML_PUB_SERIES: {
                    final String s = SearchEngineUtils.cleanName(builder.toString());
                    final Series series = Series.from(s);
                    book.add(series);
                    break;
                }
                case XML_PUB_SERIES_NUM: {
                    // assume that if we get here, then we added a "PubSeries" as last one.
                    final List<Series> seriesList = book.getSeries();
                    seriesList.get(seriesList.size() - 1).setNumber(builder.toString().strip());
                    break;
                }
                case XML_PRICE: {
                    final String priceStr = builder.toString().strip();
                    bookParserHelper.addPriceListed(context, siteLocale, priceStr, null, book);
                    break;
                }
                case XML_PAGES: {
                    book.setPages(builder.toString().strip());
                    break;
                }
                case XML_BINDING: {
                    book.setFormat(builder.toString().strip());
                    break;
                }
                case XML_TYPE: {
                    final String tmpString = builder.toString().strip();
                    book.putString(IsfdbSearchEngine.SiteField.BOOK_TYPE, tmpString);

                    final Book.ContentType type = IsfdbSearchEngine.TYPE_MAP.get(tmpString);
                    if (type != null) {
                        book.setContentType(type);
                    }
                    break;
                }
                case XML_TAG: {
                    book.putString(IsfdbSearchEngine.SiteField.BOOK_TAG,
                                   builder.toString().strip());
                    break;
                }
                case XML_IMAGE: {
                    if (fetchCovers[0]) {
                        String imageUrl = builder.toString().strip();
                        // Sanity check
                        if (imageUrl.startsWith("http:")) {
                            imageUrl = "https:" + imageUrl.substring(5);
                        }
                        try {
                            searchEngine.saveImage(context, imageUrl, null,
                                                   book.getRawProductCode(), 0, null)
                                        .ifPresent(fileSpec -> CoverFileSpecArray
                                                .setFileSpec(book, 0, fileSpec));

                        } catch (@NonNull final StorageException e) {
                            throw new SAXException(e);
                        }
                    }
                    break;
                }
                case XML_ARTIST: {
                    if (inCoverArtists) {
                        final String s = SearchEngineUtils.cleanName(builder.toString());
                        bookParserHelper.addAuthor(Author.from(s), AuthorRole.COVER_ARTIST,
                                                   book, false);
                    }
                    break;
                }
                case XML_NOTE: {
                    // can contain html tags!
                    book.setDescription(SearchEngineUtils.cleanText(builder.toString()));
                    break;
                }
                case XML_ID_TYPE: {
                    if (inExternalId) {
                        externalIdType = builder.toString().strip();
                    }
                    break;
                }
                case XML_ID_VALUE: {
                    if (inExternalId) {
                        externalId = builder.toString().strip();
                    }
                    break;
                }
                case XML_EXTERNAL_ID: {
                    if (inExternalIds) {
                        if (externalIdType != null && externalId != null) {
                            final String key = IDENTIFIER_MAPPING.get(externalIdType);
                            if (key != null) {
                                book.setIdentifierValue(key, externalId);
                            }
                        }
                        inExternalId = false;
                        externalIdType = null;
                        externalId = null;
                    }
                    break;
                }
                default:
                    break;
            }
        }

        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But let's not be pedantic for now.
        builder.setLength(0);
    }
}
