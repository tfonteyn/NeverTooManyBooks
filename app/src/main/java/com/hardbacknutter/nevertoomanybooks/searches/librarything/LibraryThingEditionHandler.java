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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser Handler to collect the edition data.
 * <p>
 * http://www.librarything.com/api/thingISBN/{ISBN}
 * <p>
 * Typical request output:
 * <pre>
 *     {@code
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <idlist>
 *     <isbn>0380014300</isbn>
 *     <isbn>0839824270</isbn>
 *     <isbn>0722194390</isbn>
 *     <isbn>0783884257</isbn>
 *     ...etc...
 *     <isbn>2207301907</isbn>
 *   </idlist>
 *   }
 * </pre>
 */
class LibraryThingEditionHandler
        extends DefaultHandler {

    /** isbn tag in an editions xml response. */
    private static final String XML_ISBN = "isbn";

    private final StringBuilder mBuilder = new StringBuilder();
    /** List of ISBN numbers for all found editions. */
    private final List<String> mEditions = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param isbn the original isbn
     */
    LibraryThingEditionHandler(@NonNull final String isbn) {
        mEditions.add(isbn);
    }

    /**
     * Get the results.
     *
     * @return the list with ISBN numbers.
     */
    @NonNull
    public List<String> getResult() {
        return mEditions;
    }

    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {

        if (localName.equalsIgnoreCase(XML_ISBN)) {
            mEditions.add(mBuilder.toString());
        }

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
