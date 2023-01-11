/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

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

    @NonNull
    private final IsfdbSearchEngine searchEngine;
    @NonNull
    private final boolean[] fetchCovers;
    @NonNull
    private final Locale locale;
    /** XML content. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    @NonNull
    private final List<Book> bookList = new ArrayList<>();
    private int maxBooks;
    private boolean inPublication;
    private Book publicationData;
    private boolean inAuthors;
    private boolean inCoverArtists;
    private boolean inExternalIds;
    private boolean inExternalId;

    @Nullable
    private String externalIdType;
    @Nullable
    private String externalId;

    IsfdbPublicationListHandler(@NonNull final IsfdbSearchEngine isfdbApiSearchEngine,
                                @NonNull final boolean[] fetchCovers,
                                final int maxBooks,
                                @NonNull final Locale locale) {
        searchEngine = isfdbApiSearchEngine;

        this.fetchCovers = fetchCovers;
        this.maxBooks = maxBooks;
        this.locale = locale;
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
                publicationData = new Book();
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
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        if (XML_RECORDS.equals(qName)) {
            // Top level number of Publication records
            final String tmpString = builder.toString().trim();
            try {
                final int n = Integer.parseInt(tmpString);
                if (n < maxBooks) {
                    maxBooks = n;
                }
            } catch (@NonNull final NumberFormatException e) {
                throw new SAXException(new EOFException());
            }

        } else if (XML_PUBLICATION.equals(qName)) {
            // ISFDB does not provide the books language in xml
            //ENHANCE: the site is adding language to the data. For now, default to English
            publicationData.putString(DBKey.LANGUAGE, "eng");

            inPublication = false;
            bookList.add(publicationData);
            if (bookList.size() == maxBooks) {
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
                    addIfNotPresent(DBKey.SID_ISFDB, builder.toString());
                    break;
                }
                case XML_TITLE: {
                    addIfNotPresent(DBKey.TITLE, builder.toString());
                    break;
                }
                case XML_AUTHOR: {
                    if (inAuthors) {
                        final Author author = Author.from(builder.toString());
                        publicationData.add(author);
                    }
                    break;
                }

                case XML_YEAR: {
                    addIfNotPresent(DBKey.BOOK_PUBLICATION__DATE, builder.toString());
                    break;
                }
                case XML_ISBN: {
                    addIfNotPresent(DBKey.BOOK_ISBN, ISBN.cleanText(builder.toString()));
                    break;
                }
                case XML_CATALOG: {
                    // use the ISBN if we have one, otherwise the catalog id
                    addIfNotPresent(DBKey.BOOK_ISBN, builder.toString());
                    break;
                }
                case XML_PUBLISHER: {
                    final Publisher publisher = Publisher.from(builder.toString());
                    publicationData.add(publisher);
                    break;
                }
                case XML_PUB_SERIES: {
                    final Series series = Series.from(builder.toString());
                    publicationData.add(series);
                    break;
                }
                case XML_PUB_SERIES_NUM: {
                    final String tmpString = builder.toString().trim();
                    // assume that if we get here, then we added a "PubSeries" as last one.
                    final List<Series> seriesList = publicationData.getSeries();
                    seriesList.get(seriesList.size() - 1).setNumber(tmpString);
                    break;
                }
                case XML_PRICE: {
                    final String tmpString = builder.toString().trim();
                    final Money money = new Money(locale, tmpString);
                    if (money.getCurrencyCode() != null) {
                        publicationData.putDouble(DBKey.PRICE_LISTED, money.doubleValue());
                        addIfNotPresent(DBKey.PRICE_LISTED_CURRENCY, money.getCurrencyCode());
                    } else {
                        addIfNotPresent(DBKey.PRICE_LISTED, tmpString);
                    }
                    break;
                }
                case XML_PAGES: {
                    addIfNotPresent(DBKey.PAGE_COUNT, builder.toString());
                    break;
                }
                case XML_BINDING: {
                    addIfNotPresent(DBKey.FORMAT, builder.toString());
                    break;
                }
                case XML_TYPE: {
                    final String tmpString = builder.toString().trim();
                    addIfNotPresent(IsfdbSearchEngine.SiteField.BOOK_TYPE, tmpString);
                    final Book.ContentType type = IsfdbSearchEngine.TYPE_MAP.get(tmpString);
                    if (type != null) {
                        publicationData.putLong(DBKey.TOC_TYPE__BITMASK, type.getId());
                    }
                    break;
                }
                case XML_TAG: {
                    addIfNotPresent(IsfdbSearchEngine.SiteField.BOOK_TAG, builder.toString());
                    break;
                }
                case XML_IMAGE: {
                    if (fetchCovers[0]) {
                        final String tmpString = builder.toString().trim();
                        try {
                            final String isbn = publicationData.getString(DBKey.BOOK_ISBN);
                            final String fileSpec =
                                    searchEngine.saveImage(tmpString, isbn, 0, null);
                            if (fileSpec != null) {
                                final ArrayList<String> list = new ArrayList<>();
                                list.add(fileSpec);
                                publicationData.putStringArrayList(
                                        SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);

                            }
                        } catch (@NonNull final StorageException e) {
                            throw new SAXException(e);
                        }
                    }
                    break;
                }
                case XML_ARTIST: {
                    if (inCoverArtists) {
                        final Author author = Author.from(builder.toString());
                        author.setType(Author.TYPE_COVER_ARTIST);
                        publicationData.add(author);
                    }
                    break;
                }
                case XML_NOTE: {
                    // can contain html tags!
                    addIfNotPresent(DBKey.DESCRIPTION,
                                    ParseUtils.cleanText(builder.toString()));
                    break;
                }
                case XML_ID_TYPE: {
                    if (inExternalId) {
                        externalIdType = builder.toString();
                    }
                    break;
                }
                case XML_ID_VALUE: {
                    if (inExternalId) {
                        externalId = builder.toString();
                    }
                    break;
                }
                case XML_EXTERNAL_ID: {
                    if (inExternalIds) {
                        if (externalIdType != null && externalId != null) {
                            //NEWTHINGS: adding a new search engine: optional: add external id DOM
                            switch (externalIdType) {
                                // the case labels are the codes used by the ISFDB website
                                case "1":
                                    addIfNotPresent(DBKey.SID_ASIN, externalId);
                                    break;
                                case "8":
                                    addIfNotPresent(DBKey.SID_GOODREADS_BOOK, externalId);
                                    break;
                                case "10":
                                    addIfNotPresent(DBKey.SID_LCCN, externalId);
                                    break;
                                case "12":
                                    addIfNotPresent(DBKey.SID_OCLC, externalId);
                                    break;
                                case "13":
                                    addIfNotPresent(DBKey.SID_OPEN_LIBRARY, externalId);
                                    break;
                                default:
                                    break;
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
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        builder.setLength(0);
    }

    /**
     * Add the value to the Bundle if not present or empty.
     *
     * @param key   to use
     * @param value to store
     */
    private void addIfNotPresent(@NonNull final String key,
                                 @NonNull final String value) {
        final String test = publicationData.getString(key, null);
        if (test == null || test.isEmpty()) {
            publicationData.putString(key, value.trim());
        }
    }
}
