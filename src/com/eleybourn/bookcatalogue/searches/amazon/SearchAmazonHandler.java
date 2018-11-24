/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Currency;

/**
 * An XML handler for the Amazon return
 * <p>
 * An example response looks like;
 * <ItemSearchResponse xmlns="http://webservices.amazon.com/AWSECommerceService/2005-10-05">
 * <OperationRequest>
 * <HTTPHeaders>
 * <Header Name="UserAgent" Value="Mozilla/5.0 (X11; U; Linux i686; en-US) AppleWebKit/533.4 (KHTML, like Gecko) Chrome/5.0.375.29 Safari/533.4">
 * </Header>
 * </HTTPHeaders>
 * <RequestId>df6342c0-c939-4b28-877a-5096da83a959</RequestId>
 * <Arguments>
 * <Argument Name="Operation" Value="ItemSearch"></Argument>
 * <Argument Name="Service" Value="AWSECommerceService"></Argument>
 * <Argument Name="Signature" Value="xHegBeDoF4U5vlC66493SULuAgQulFQSJyqHz3s2obE="></Argument>
 * <Argument Name="Keywords" Value="9780006483830"></Argument>
 * <Argument Name="Timestamp" Value="2010-05-16T01:30:12.000Z"></Argument>
 * <Argument Name="ResponseGroup" Value="Medium,Images"></Argument>
 * <Argument Name="SubscriptionId" Value="AKIAIHF2BM6OTOA23JEQ"></Argument>
 * <Argument Name="SearchIndex" Value="Books"></Argument>
 * </Arguments>
 * <RequestProcessingTime>0.0740140000000000</RequestProcessingTime>
 * </OperationRequest>
 * <Items>
 * <Request>
 * <IsValid>True</IsValid>
 * <ItemSearchRequest>
 * <Condition>New</Condition>
 * <DeliveryMethod>Ship</DeliveryMethod>
 * <Keywords>9780006483830</Keywords>
 * <MerchantId>Amazon</MerchantId>
 * <ResponseGroup>Medium</ResponseGroup>
 * <ResponseGroup>Images</ResponseGroup>
 * <SearchIndex>Books</SearchIndex>
 * </ItemSearchRequest>
 * </Request>
 * <TotalResults>1</TotalResults>
 * <TotalPages>1</TotalPages>
 * <Item>
 * <ASIN>0006483836</ASIN>
 * <DetailPageURL>http://www.amazon.com/TRIGGER-CLARKE-KUBE-MCDOWELL/dp/0006483836%3FSubscriptionId%3DAKIAIHF2BM6OTOA23JEQ%26tag%3Dws%26linkCode%3Dxm2%26camp%3D2025%26creative%3D165953%26creativeASIN%3D0006483836</DetailPageURL>
 * <SalesRank>4575972</SalesRank>
 * <SmallImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 * <Height Units="pixels">75</Height>
 * <Width Units="pixels">48</Width>
 * </SmallImage>
 * <MediumImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 * <Height Units="pixels">160</Height>
 * <Width Units="pixels">102</Width>
 * </MediumImage>
 * <LargeImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 * <Height Units="pixels">475</Height>
 * <Width Units="pixels">304</Width>
 * </LargeImage>
 * <ImageSets>
 * <ImageSet Category="primary">
 * <SwatchImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL30_.jpg</URL>
 * <Height Units="pixels">30</Height>
 * <Width Units="pixels">19</Width>
 * </SwatchImage>
 * <SmallImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 * <Height Units="pixels">75</Height>
 * <Width Units="pixels">48</Width>
 * </SmallImage>
 * <MediumImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 * <Height Units="pixels">160</Height>
 * <Width Units="pixels">102</Width>
 * </MediumImage>
 * <LargeImage>
 * <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 * <Height Units="pixels">475</Height>
 * <Width Units="pixels">304</Width>
 * </LargeImage>
 * </ImageSet>
 * </ImageSets>
 * <ItemAttributes>
 * <Author>CLARKE / KUBE-MCDOWELL</Author>
 * <Binding>Paperback</Binding>
 * <DeweyDecimalNumber>823.914</DeweyDecimalNumber>
 * <EAN>9780006483830</EAN>
 * <Edition>New Ed</Edition>
 * <Format>Import</Format>
 * <EAN>0006483836</EAN>
 * <Label>Voyager</Label>
 * <Languages>
 * <Language>
 * <Name>English</Name>
 * <Type>Original Language</Type>
 * </Language>
 * <Language>
 * <Name>English</Name>
 * <Type>Unknown</Type>
 * </Language>
 * <Language>
 * <Name>English</Name>
 * <Type>Published</Type>
 * </Language>
 * </Languages>
 * <Manufacturer>Voyager</Manufacturer>
 * <NumberOfPages>560</NumberOfPages>
 * <PackageDimensions>
 * <Height Units="hundredths-inches">140</Height>
 * <Length Units="hundredths-inches">680</Length>
 * <Weight Units="hundredths-pounds">60</Weight>
 * <Width Units="hundredths-inches">430</Width>
 * </PackageDimensions>
 * <ProductGroup>Book</ProductGroup>
 * <PublicationDate>2000-01-01</PublicationDate>
 * <Publisher>Voyager</Publisher>
 * <Studio>Voyager</Studio>
 * <Title>TRIGGER</Title>
 * </ItemAttributes>
 * <OfferSummary>
 *
 *
 * <LowestUsedPrice>
 * <Amount>1</Amount>
 * <CurrencyCode>USD</CurrencyCode>
 * <FormattedPrice>$0.01</FormattedPrice>
 * </LowestUsedPrice>
 *
 *
 * <TotalNew>0</TotalNew>
 * <TotalUsed>43</TotalUsed>
 * <TotalCollectible>0</TotalCollectible>
 * <TotalRefurbished>0</TotalRefurbished>
 * </OfferSummary>
 * </Item>
 * </Items>
 * </ItemSearchResponse>
 *
 * @author evan
 */
public class SearchAmazonHandler extends DefaultHandler {

    /** file suffix for cover files */
    private static final String FILENAME_SUFFIX = "_AM";

    /** XML tags we look for. They are mixed-case, hence we use .equalsIgnoreCase and not a switch */
//    private static final String XML_ID = "id";
//    private static final String XML_TOTAL_RESULTS = "TotalResults";
    private static final String XML_ENTRY = "Item";

    private static final String XML_AUTHOR = "Author";
    private static final String XML_TITLE = "Title";
    private static final String XML_E_ISBN = "EISBN";
    private static final String XML_EAN = "EAN";
    private static final String XML_ISBN_OLD = "EAN";
    private static final String XML_DATE_PUBLISHED = "PublicationDate";
    private static final String XML_PUBLISHER = "Publisher";
    private static final String XML_PAGES = "NumberOfPages";
    private static final String XML_THUMBNAIL = "URL";
    private static final String XML_SMALL_IMAGE = "SmallImage";
    private static final String XML_MEDIUM_IMAGE = "MediumImage";
    private static final String XML_LARGE_IMAGE = "LargeImage";
    private static final String XML_DESCRIPTION = "Content";
    private static final String XML_BINDING = "Binding";
    private static final String XML_LANGUAGE = "Language";
    private static final String XML_NAME = "Name";
    private static final String XML_LIST_PRICE = "ListPrice";
    private static final String XML_CURRENCY_CODE = "CurrencyCode";
    private static final String XML_AMOUNT = "Amount";

    /** flag if we should fetch a thumbnail */
    private static boolean mFetchThumbnail;
    /** Bundle to save results in */
    @NonNull
    private final Bundle mBookData;
    /** accumulate all authors for this book */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** XML content */
    private StringBuilder mBuilder = new StringBuilder();
    /** mCurrencyCode + mCurrencyAmount will form the list-price */
    @NonNull
    private String mCurrencyCode = "";
    @NonNull
    private String mCurrencyAmount = "";

    @NonNull
    private String mThumbnailUrl = "";
    /** max size found, -1 for no images found */
    private int mThumbnailSize = -1;

    /*
     * flags to identify if we are in the correct node
     * We need these for nodes in need of special handling (f.e. XML_LIST_PRICE)
     * or which are composite (f.e. XML_LANGUAGE)
     */

    /**
     * XML_LANGUAGE
     * * <Languages>
     * * <Language>
     * * <Name>English</Name>
     * * <Type>Original Language</Type>
     * * </Language>
     * * <Language>
     * * <Name>English</Name>
     * * <Type>Unknown</Type>
     * * </Language>
     * * <Language>
     * * <Name>English</Name>
     * * <Type>Published</Type>
     * * </Language>
     * * </Languages>
     */
    private boolean mInLanguage = false;

    /**
     * XML_LIST_PRICE
     * Not shown in the example XML, but contains two sub-elements we use:
     * * CurrencyCode
     * * Amount
     */
    private boolean mInListPrice = false;

    /**
     * XML_THUMBNAIL
     * There are multiple, we try to get the largest available
     * * SmallImage
     * * MediumImage
     * * LargeImage
     */
    private boolean mInImage = false;

    /** starting an entry */
    private boolean mInEntry = false;
    /** and finishing an entry */
    private boolean mEntryDone = false;

    /**
     * @param bookData       Bundle to save results in
     * @param fetchThumbnail true if we need to get a thumbnail
     */
    SearchAmazonHandler(final @NonNull Bundle bookData, final boolean fetchThumbnail) {
        mBookData = bookData;
        mFetchThumbnail = fetchThumbnail;
    }

    /**
     * Amount is in the lowest denomination, so for USD, cents, GBP pennies.. etc.
     * <Amount>1234</Amount>
     * <CurrencyCode>USD</CurrencyCode>
     * is $12.34
     */
    private void handleListPrice() {
        try {
            int decDigits = Currency.getInstance(mCurrencyCode).getDefaultFractionDigits();
            // move the decimal point 'digits' up
            double price = ((double) Integer.parseInt(mCurrencyAmount)) / Math.pow(10, decDigits);
            // and format with 'digits' decimal places
            BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_PRICE_LISTED, String.format("%." + decDigits + "f", price));
            BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY, mCurrencyCode);
        } catch (Exception ignore) {
        }
    }

    @Override
    @CallSuper
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        mBuilder.append(ch, start, length);
    }

    /**
     * (non-Javadoc)
     *
     * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     *
     * Start each XML element. Specifically identify when we are in the item element and set the appropriate flag.
     *
     * Also identify the biggest thumbnail available.
     */
    @Override
    @CallSuper
    public void startElement(final String uri,
                             final @NonNull String localName,
                             final String name,
                             final Attributes attributes) throws SAXException {
        super.startElement(uri, localName, name, attributes);

        if (!mEntryDone && localName.equalsIgnoreCase(XML_ENTRY)) {
            mInEntry = true;

        } else if (localName.equalsIgnoreCase(XML_LANGUAGE)) {
            mInLanguage = true;

        } else if (localName.equalsIgnoreCase(XML_LIST_PRICE)) {
            mInListPrice = true;

        } else if (localName.equalsIgnoreCase(XML_SMALL_IMAGE)) {
            if (mThumbnailSize < 1) {
                mInImage = true;
                mThumbnailSize = 1;
            }
        } else if (localName.equalsIgnoreCase(XML_MEDIUM_IMAGE)) {
            if (mThumbnailSize < 2) {
                mInImage = true;
                mThumbnailSize = 2;
            }
        } else if (localName.equalsIgnoreCase(XML_LARGE_IMAGE)) {
            if (mThumbnailSize < 3) {
                mInImage = true;
                mThumbnailSize = 3;
            }
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     *
     * Populate the results Bundle for each appropriate element.
     *
     * Also download the thumbnail and store in a tmp location
     * @see #endDocument() where we handle the thumbnail.
     */
    @Override
    @CallSuper
    public void endElement(final String uri,
                           final @NonNull String localName,
                           final String name) throws SAXException {
        super.endElement(uri, localName, name);

        if (mInImage && localName.equalsIgnoreCase(XML_THUMBNAIL)) {
            mThumbnailUrl = mBuilder.toString();
            mInImage = false;
        } else if (localName.equalsIgnoreCase(XML_LANGUAGE)) {
            mInLanguage = false;
        } else if (localName.equalsIgnoreCase(XML_LIST_PRICE)) {
            handleListPrice();
            mCurrencyCode = "";
            mCurrencyAmount = "";
            mInListPrice = false;
        } else if (localName.equalsIgnoreCase(XML_ENTRY)) {
            mEntryDone = true;
            mInEntry = false;

        } else if (mInEntry) {
            if (localName.equalsIgnoreCase(XML_AUTHOR)) {
                mAuthors.add(new Author(mBuilder.toString()));

            } else if (localName.equalsIgnoreCase(XML_TITLE)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_TITLE, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_PUBLISHER)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_PUBLISHER, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_DATE_PUBLISHED)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_DATE_PUBLISHED, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_PAGES)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_PAGES, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_DESCRIPTION)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_DESCRIPTION, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_BINDING)) {
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_FORMAT, mBuilder.toString());

            } else if (mInLanguage && localName.equalsIgnoreCase(XML_NAME)) {
                // the language is a 'DisplayName'
                BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_BOOK_LANGUAGE, LocaleUtils.getISO3Language(mBuilder.toString()));

            } else if (mInListPrice && localName.equalsIgnoreCase(XML_AMOUNT)) {
                mCurrencyAmount = mBuilder.toString();
            } else if (mInListPrice && localName.equalsIgnoreCase(XML_CURRENCY_CODE)) {
                mCurrencyCode = mBuilder.toString();

            } else if (localName.equalsIgnoreCase(XML_EAN)
                    || localName.equalsIgnoreCase(XML_E_ISBN)
                    || localName.equalsIgnoreCase(XML_ISBN_OLD)) {
                // we prefer the "longest" isbn, which theoretically should be an ISBN-13
                String tmp = mBuilder.toString();
                String test = mBookData.getString(UniqueId.KEY_BOOK_ISBN);
                if (test == null || test.length() < tmp.length()) {
                    mBookData.putString(UniqueId.KEY_BOOK_ISBN, tmp);
                }

            } else {
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    // see what we are missing.
                    Logger.info(this, "Skipping: " + localName + "->'" + mBuilder + "'");
                }
            }
        } //else if (localName.equalsIgnoreCase(XML_TOTAL_RESULTS)){
        // not used for now... int mCount = Integer.parseInt(mBuilder.toString());
        //}

        // Note:
        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    /**
     * Store the accumulated data in the results
     *
     * Choose the best thumbnail, if present.
     */
    @Override
    @CallSuper
    public void endDocument() {
        if (mFetchThumbnail && !mThumbnailUrl.isEmpty()) {
            String fileSpec = ImageUtils.saveThumbnailFromUrl(mThumbnailUrl, FILENAME_SUFFIX);
            if (fileSpec != null) {
                StringList.addOrAppend(mBookData, UniqueId.BKEY_THUMBNAIL_FILE_SPEC, fileSpec);
            }
        }

        mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
    }
}
 
