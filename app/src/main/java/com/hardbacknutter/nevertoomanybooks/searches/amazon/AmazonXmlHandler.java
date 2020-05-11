/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import org.xml.sax.Attributes;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.xml.SearchHandler;

/**
 * An XML handler for the Amazon return.
 * <p>
 * An example response looks like;
 * <pre>
 *     {@code
 * <ItemSearchResponse xmlns="http://webservices.amazon.com/AWSECommerceService/2005-10-05">
 *   <OperationRequest>
 *     <HTTPHeaders>
 *       <Header Name="UserAgent" Value="Mozilla/5.0 (X11; U; Linux i686; en-US) AppleWebKit/533.4
 *          (KHTML, like Gecko) Chrome/5.0.375.29 Safari/533.4">
 *       </Header>
 *     </HTTPHeaders>
 *     <RequestId>df6342c0-c939-4b28-877a-5096da83a959</RequestId>
 *     <Arguments>
 *       <Argument Name="Operation" Value="ItemSearch"></Argument>
 *       <Argument Name="Service" Value="AWSECommerceService"></Argument>
 *       <Argument Name="Signature" Value="xHegBeDoF4U5vlC66493SULuAgQulFQSJyqHz3s2obE="></Argument>
 *       <Argument Name="Keywords" Value="9780006483830"></Argument>
 *       <Argument Name="Timestamp" Value="2010-05-16T01:30:12.000Z"></Argument>
 *       <Argument Name="ResponseGroup" Value="Medium,Images"></Argument>
 *       <Argument Name="SubscriptionId" Value="AKIAIHF2BM6OTOA23JEQ"></Argument>
 *       <Argument Name="SearchIndex" Value="Books"></Argument>
 *     </Arguments>
 *     <RequestProcessingTime>0.0740140000000000</RequestProcessingTime>
 *   </OperationRequest>
 *   <Items>
 *     <Request>
 *       <IsValid>True</IsValid>
 *       <ItemSearchRequest>
 *         <Condition>New</Condition>
 *         <DeliveryMethod>Ship</DeliveryMethod>
 *         <Keywords>9780006483830</Keywords>
 *         <MerchantId>Amazon</MerchantId>
 *         <ResponseGroup>Medium</ResponseGroup>
 *         <ResponseGroup>Images</ResponseGroup>
 *         <SearchIndex>Books</SearchIndex>
 *       </ItemSearchRequest>
 *     </Request>
 *     <TotalResults>1</TotalResults>
 *     <TotalPages>1</TotalPages>
 *
 *     <Item>
 *       <ASIN>0006483836</ASIN>
 *       <DetailPageURL>http://www.amazon.com/TRIGGER-CLARKE-KUBE-MCDOWELL/dp/0006483836%3F
 *       SubscriptionId%3DAKIAIHF2BM6OTOA23JEQ%26tag%3Dws%26linkCode%3Dxm2%26camp%3D2025
 *       %26creative%3D165953%26creativeASIN%3D0006483836</DetailPageURL>
 *       <SalesRank>4575972</SalesRank>
 *
 *       <SmallImage>
 *         <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 *         <Height Units="pixels">75</Height>
 *         <Width Units="pixels">48</Width>
 *       </SmallImage>
 *       <MediumImage>
 *         <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 *         <Height Units="pixels">160</Height>
 *         <Width Units="pixels">102</Width>
 *       </MediumImage>
 *       <LargeImage>
 *         <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 *         <Height Units="pixels">475</Height>
 *         <Width Units="pixels">304</Width>
 *       </LargeImage>
 *
 *       <ImageSets>
 *         <ImageSet Category="primary">
 *           <SwatchImage>
 *             <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL30_.jpg</URL>
 *             <Height Units="pixels">30</Height>
 *             <Width Units="pixels">19</Width>
 *           </SwatchImage>
 *           <SmallImage>
 *             <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL75_.jpg</URL>
 *             <Height Units="pixels">75</Height>
 *             <Width Units="pixels">48</Width>
 *           </SmallImage>
 *           <MediumImage>
 *             <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL._SL160_.jpg</URL>
 *             <Height Units="pixels">160</Height>
 *             <Width Units="pixels">102</Width>
 *           </MediumImage>
 *           <LargeImage>
 *             <URL>http://ecx.images-amazon.com/images/I/51B6YYGE3PL.jpg</URL>
 *             <Height Units="pixels">475</Height>
 *             <Width Units="pixels">304</Width>
 *           </LargeImage>
 *         </ImageSet>
 *       </ImageSets>
 *
 *       <ItemAttributes>
 *         <Author>CLARKE / KUBE-MCDOWELL</Author>
 *         <Binding>Paperback</Binding>
 *         <DeweyDecimalNumber>823.914</DeweyDecimalNumber>
 *         <EAN>9780006483830</EAN>
 *         <Edition>New Ed</Edition>
 *         <Format>Import</Format>
 *         <EAN>0006483836</EAN>
 *         <Label>Voyager</Label>
 *         <Languages>
 *           <Language>
 *             <Name>English</Name>
 *             <Type>Original Language</Type>
 *           </Language>
 *           <Language>
 *             <Name>English</Name>
 *             <Type>Unknown</Type>
 *           </Language>
 *           <Language>
 *             <Name>English</Name>
 *             <Type>Published</Type>
 *           </Language>
 *         </Languages>
 *         <Manufacturer>Voyager</Manufacturer>
 *         <NumberOfPages>560</NumberOfPages>
 *         <PackageDimensions>
 *           <Height Units="hundredths-inches">140</Height>
 *           <Length Units="hundredths-inches">680</Length>
 *           <Weight Units="hundredths-pounds">60</Weight>
 *           <Width Units="hundredths-inches">430</Width>
 *         </PackageDimensions>
 *         <ProductGroup>Book</ProductGroup>
 *         <PublicationDate>2000-01-01</PublicationDate>
 *         <Publisher>Voyager</Publisher>
 *         <Studio>Voyager</Studio>
 *         <Title>TRIGGER</Title>
 *       </ItemAttributes>
 *
 *       <OfferSummary>
 *         <LowestUsedPrice>
 *           <Amount>1</Amount>
 *           <CurrencyCode>USD</CurrencyCode>
 *           <FormattedPrice>$0.01</FormattedPrice>
 *         </LowestUsedPrice>
 *         <TotalNew>0</TotalNew>
 *         <TotalUsed>43</TotalUsed>
 *         <TotalCollectible>0</TotalCollectible>
 *         <TotalRefurbished>0</TotalRefurbished>
 *       </OfferSummary>
 *     </Item>
 *   </Items>
 * </ItemSearchResponse>
 * }
 * </pre>
 * <p>
 * An error response:
 * <pre>
 *     {@code
 *  <ItemSearchErrorResponse xmlns="http://ecs.amazonaws.com/doc/2005-10-05/">
 *      <Error>
 *          <Code>RequestThrottled</Code>
 *          <Message>
 *              AWS Access Key ID: xxxxxx. You are submitting requests too quickly.
 *              Please retry your requests at a slower rate.
 *          </Message>
 *      </Error>
 *      <RequestID>ecf67cf8-e363-4b08-a140-48fcdbebd956</RequestID>
 *   </ItemSearchErrorResponse>
 *  }
 * </pre>
 * <p>
 * IMPORTANT: error detection has in fact not been tested.
 * <p>
 * "ItemAttributes" section:
 * https://docs.aws.amazon.com/AWSECommerceService/latest/DG/CHAP_response_elements.html
 */
@SuppressWarnings("HtmlTagCanBeJavadocTag")
class AmazonXmlHandler
        extends SearchHandler {

    /** Log tag. */
    private static final String TAG = "AmazonXmlHandler";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_AM";

    /**
     * XML tags we look for.
     * They are mixed-case, hence we use .equalsIgnoreCase and not a switch
     */
    private static final String XML_ERROR = "Error";
    private static final String XML_CODE = "Code";
    @SuppressWarnings("unused")
    private static final String XML_CODE_RequestThrottled = "RequestThrottled";

    //    private static final String XML_ID = "id";
//    private static final String XML_TOTAL_RESULTS = "TotalResults";
    private static final String XML_ITEM = "Item";

    private static final String XML_AUTHOR = "Author";
    private static final String XML_TITLE = "Title";
    private static final String XML_E_ISBN = "EISBN";
    private static final String XML_EAN = "EAN";
    private static final String XML_ISBN_OLD = XML_EAN;
    private static final String XML_ASIN = "ASIN";
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

    /** flag if we should fetch a thumbnail. */
    @NonNull
    private final boolean[] mFetchThumbnail;
    @NonNull
    private final Context mLocalizedAppContext;
    /** Bundle to save results in. */
    @NonNull
    private final Bundle mBookData;

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();

    /** XML content. */
    private final StringBuilder mBuilder = new StringBuilder();
    /** mCurrencyCode + mCurrencyAmount will form the list-price. */
    @NonNull
    private String mCurrencyCode = "";
    @NonNull
    private String mCurrencyAmount = "";

    @NonNull
    private String mCoverUrl = "";
    /** max size found, -1 for no images found. */
    private int mThumbnailSize = -1;

    /*
     * flags to identify if we are in the correct node
     * We need these for nodes in need of special handling (e.g. XML_LIST_PRICE)
     * or which are composite (e.g. XML_LANGUAGE)
     */

    /**
     * XML_LANGUAGE.
     */
    private boolean mInLanguage;

    /**
     * XML_LIST_PRICE.
     * Not shown in the example XML, but contains two sub-elements we use:
     * * CurrencyCode
     * * Amount
     */
    private boolean mInListPrice;

    /**
     * XML_THUMBNAIL.
     * There are multiple, we try to get the largest available
     * * SmallImage
     * * MediumImage
     * * LargeImage
     */
    private boolean mInImage;

    /** starting an entry. */
    private boolean mInItem;
    /** and finishing an entry. */
    private boolean mEntryDone;

    /** error instead of entry. */
    private boolean mInError;
    private String mError;

    /**
     * Constructor.
     *
     * @param localizedAppContext Localised application context
     * @param fetchThumbnail      Set to {@code true} if we want to get a thumbnail
     * @param bookData            Bundle to save results in (passed in to allow mocking)
     */
    AmazonXmlHandler(@NonNull final Context localizedAppContext,
                     @NonNull final boolean[] fetchThumbnail,
                     @NonNull final Bundle bookData) {
        mLocalizedAppContext = localizedAppContext;
        mFetchThumbnail = fetchThumbnail;
        mBookData = bookData;
    }

    /**
     * Get the results.
     *
     * @return Bundle with book data
     */
    @NonNull
    @Override
    public Bundle getResult() {
        return mBookData;
    }

    @Nullable
    public String getError() {
        return mError;
    }

    /**
     * Add the value to the Bundle if not present.
     *
     * @param bundle to check
     * @param key    for data to add
     * @param value  to use
     */
    private void addIfNotPresent(@NonNull final Bundle bundle,
                                 @NonNull final String key,
                                 @NonNull final String value) {
        String test = bundle.getString(key);
        if (test == null || test.isEmpty()) {
            bundle.putString(key, value.trim());
        }
    }

    /**
     * Amount is in the lowest denomination, so for USD, cents, GBP pennies.. etc.
     * {@code
     * <Amount>1234</Amount>
     * <CurrencyCode>USD</CurrencyCode>
     * }
     * is $12.34
     */
    private void handleListPrice() {
        if (!mBookData.containsKey(DBDefinitions.KEY_PRICE_LISTED)) {
            try {
                int decDigits = java.util.Currency.getInstance(mCurrencyCode)
                                                  .getDefaultFractionDigits();
                // move the decimal point 'digits' up
                double price = Double.parseDouble(mCurrencyAmount) / Math.pow(10, decDigits);
                mBookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, price);
                mBookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, mCurrencyCode);
            } catch (@NonNull final NumberFormatException ignore) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "handleListPrice"
                               + "|mCurrencyCode=" + mCurrencyCode
                               + "|mCurrencyAmount=" + mCurrencyAmount);
                }
            }
        }
    }

    /**
     * Store the accumulated data in the results.
     */
    @Override
    @CallSuper
    public void endDocument() {
        if (mFetchThumbnail[0] && !mCoverUrl.isEmpty()) {
            String name = mBookData.getString(DBDefinitions.KEY_EID_ASIN, "");
            name += FILENAME_SUFFIX;
            String fileSpec = ImageUtils.saveImage(mLocalizedAppContext, mCoverUrl, name, null);
            if (fileSpec != null) {
                ArrayList<String> imageList =
                        mBookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0]);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                mBookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0], imageList);
            }
        }
        if (!mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mPublishers.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
        }
    }

    /**
     * Start each XML element. Specifically identify when we are in the item
     * element and set the appropriate flag.
     * <p>
     * Also identify the biggest thumbnail available.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {
        if (!mEntryDone && localName.equalsIgnoreCase(XML_ERROR)) {
            mInError = true;

        } else if (!mEntryDone && localName.equalsIgnoreCase(XML_ITEM)) {
            mInItem = true;

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
     * Populate the results Bundle for each appropriate element.
     * <p>
     * Also download the thumbnail and store in a tmp location
     *
     * @see #endDocument() where we handle the thumbnail.
     */
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {

        if (mInImage && localName.equalsIgnoreCase(XML_THUMBNAIL)) {
            mCoverUrl = mBuilder.toString();
            mInImage = false;

        } else if (localName.equalsIgnoreCase(XML_LANGUAGE)) {
            mInLanguage = false;

        } else if (localName.equalsIgnoreCase(XML_LIST_PRICE)) {
            handleListPrice();
            mCurrencyCode = "";
            mCurrencyAmount = "";
            mInListPrice = false;

        } else if (localName.equalsIgnoreCase(XML_ITEM)) {
            mEntryDone = true;
            mInItem = false;

        } else if (localName.equalsIgnoreCase(XML_ERROR)) {
            mEntryDone = true;
            mInError = false;

        } else if (mInError) {
            if (localName.equalsIgnoreCase(XML_CODE)) {
                mError = mBuilder.toString();
            }

        } else if (mInItem) {
            if (localName.equalsIgnoreCase(XML_AUTHOR)) {
                mAuthors.add(Author.from(mBuilder.toString()));

            } else if (localName.equalsIgnoreCase(XML_TITLE)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_TITLE, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_PUBLISHER)) {
                mPublishers.add(Publisher.from(mBuilder.toString()));

            } else if (localName.equalsIgnoreCase(XML_DATE_PUBLISHED)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_DATE_PUBLISHED,
                                mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_PAGES)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_PAGES,
                                mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_DESCRIPTION)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_DESCRIPTION,
                                mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_BINDING)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_FORMAT,
                                mBuilder.toString());

            } else if (mInLanguage && localName.equalsIgnoreCase(XML_NAME)) {
                // the language is a 'DisplayName' so convert to iso first.
                addIfNotPresent(mBookData, DBDefinitions.KEY_LANGUAGE,
                                LanguageUtils.getISO3FromDisplayName(mLocalizedAppContext,
                                                                     mBuilder.toString()));

            } else if (mInListPrice && localName.equalsIgnoreCase(XML_AMOUNT)) {
                mCurrencyAmount = mBuilder.toString();

            } else if (mInListPrice && localName.equalsIgnoreCase(XML_CURRENCY_CODE)) {
                mCurrencyCode = mBuilder.toString();

            } else if (localName.equalsIgnoreCase(XML_EAN)
                       || localName.equalsIgnoreCase(XML_E_ISBN)
                       || localName.equalsIgnoreCase(XML_ISBN_OLD)) {
                // we prefer the "longest" isbn, which theoretically should be an ISBN-13
                String tmp = mBuilder.toString();
                String test = mBookData.getString(DBDefinitions.KEY_ISBN);
                if (test == null || test.length() < tmp.length()) {
                    mBookData.putString(DBDefinitions.KEY_ISBN, tmp);
                }

            } else if (localName.equalsIgnoreCase(XML_ASIN)) {
                addIfNotPresent(mBookData, DBDefinitions.KEY_EID_ASIN, mBuilder.toString());

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                    // see what we are missing.
                    Log.d(TAG, "endElement|Skipping|" + localName + "->`" + mBuilder + '`');
                }
            }
        } //else if (localName.equalsIgnoreCase(XML_TOTAL_RESULTS)){
        // not used for now... int mCount = Integer.parseInt(mBuilder.toString());
        //}

        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    @Override
    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
    }
}

