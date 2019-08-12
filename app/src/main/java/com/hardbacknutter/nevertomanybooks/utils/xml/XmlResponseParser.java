/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils.xml;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

/**
 * Base class for parsing the output any web request that returns an XML response.
 * <b>Note:</b> this does not include general web page parsing since they often do not conform
 * to XML formatting standards.
 * <p>
 * This class is used with the {@link XmlFilter} class to call user-defined code at
 * specific points in an XML file.
 */
public class XmlResponseParser
        extends DefaultHandler {

    /** Temporary storage for inter-tag text. */
    private final StringBuilder mBuilder = new StringBuilder();
    /** Stack of parsed tags giving context to the XML parser. */
    private final ArrayList<ElementContext> mParents = new ArrayList<>();

    /**
     * Constructor. Requires a filter tree.
     *
     * @param rootFilter Filter tree to use
     */
    public XmlResponseParser(@NonNull final XmlFilter rootFilter) {
        // Build the root context and add to hierarchy.
        ElementContext tag = new ElementContext(rootFilter);
        mParents.add(tag);
    }

    /**
     * Handle a new tag.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
            Logger.debug(this, "startElement", "localName=`" + localName + '`');
        }
        // Create a new context for this new tag saving the current inter-tag text for later
        ElementContext tag = new ElementContext(uri, localName, qName, attributes,
                                                mBuilder.toString());

        // Get the current element
        ElementContext enclosingTag = mParents.get(mParents.size() - 1);

        // If there is an active filter, then see if the new tag is of any interest
        if (enclosingTag.getFilter() != null) {
            // Check for interest in new tag
            XmlFilter filter = enclosingTag.getFilter().getSubFilter(tag);
            // If new tag has a filter, store it in the new context object
            tag.setFilter(filter);
            // If we got a filter, tell it a tag is now starting.
            if (filter != null) {
                filter.processStart(tag);
            }
        }
        // Add the new tag to the context hierarchy and reset
        mParents.add(tag);
        // Reset the inter-tag text storage.
        mBuilder.setLength(0);
    }

    /**
     * Handle the end of the current tag.
     */
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {

        // Get out current context from the hierarchy and pop from stack
        ElementContext tag = mParents.remove(mParents.size() - 1);

        // Minor paranoia. Make sure name matches. Total waste of time, right?
        if (!localName.equals(tag.getLocalName())) {
            throw new IllegalStateException(
                    "End element `" + localName + "` does not match start element"
                    + " `" + tag.getLocalName() + '`');
        }

        // Save the text that appeared inside this tag (but not inside inner tags)
        tag.setBody(mBuilder.toString());

        // If there is an active filter in this context, then tell it the tag is finished.
        if (tag.getFilter() != null) {
            tag.getFilter().processEnd(tag);
        }

        // Reset the inter-tag text and append the previously saved 'pre-text'.
        mBuilder.setLength(0);
        mBuilder.append(tag.getText());
    }

    /**
     * Gather inter-tag text.
     */
    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
    }
}
