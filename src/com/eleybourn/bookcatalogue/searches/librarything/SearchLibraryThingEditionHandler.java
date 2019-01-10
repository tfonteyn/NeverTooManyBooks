package com.eleybourn.bookcatalogue.searches.librarything;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.List;

/**
 * Parser Handler to collect the edition data.
 * <p>
 * http://www.librarything.com/api/thingISBN/<ISBN>
 * <p>
 * Typical request output:
 * <pre>
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <idlist>
 *     <isbn>0380014300</isbn>
 *     <isbn>0839824270</isbn>
 *     <isbn>0722194390</isbn>
 *     <isbn>0783884257</isbn>
 *     ...etc...
 *     <isbn>2207301907</isbn>
 *   </idlist>
 * </pre>
 *
 * @author Philip Warner
 */
class SearchLibraryThingEditionHandler
        extends DefaultHandler {

    /** isbn tag in an editions xml response. */
    private static final String XML_ISBN = "isbn";

    private final StringBuilder mBuilder = new StringBuilder();
    @NonNull
    private final List<String> mEditions;

    /**
     * Constructor.
     *
     * @param editions the bundle to which we'll write the results
     */
    SearchLibraryThingEditionHandler(@NonNull final List<String> /* out */ editions) {
        mEditions = editions;
    }

    @Override
    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length)
            throws SAXException {
        super.characters(ch, start, length);
        mBuilder.append(ch, start, length);
    }

    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);

        if (localName.equalsIgnoreCase(XML_ISBN)) {
            // Add the isbn
            String isbn = mBuilder.toString();
            mEditions.add(isbn);
        }
        // Note:
        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

}
