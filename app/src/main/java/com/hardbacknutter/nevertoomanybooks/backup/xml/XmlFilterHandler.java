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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class XmlFilterHandler
        extends DefaultHandler {

    /** Temporary storage for inter-tag text. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    /** Stack of parsed tags giving context to the XML parser. */
    private final List<ElementContext> parents = new ArrayList<>();

    /**
     * Constructor. Requires a filter tree.
     *
     * @param rootFilter Filter tree to use
     */
    XmlFilterHandler(@NonNull final XmlFilter rootFilter) {
        // Build the root context and add to hierarchy.
        final ElementContext tag = new ElementContext(rootFilter);
        parents.add(tag);
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
        // Create a new context for this new tag saving the current inter-tag text for later
        final ElementContext tag = new ElementContext(localName, attributes,
                                                      builder.toString());

        // Get the current element
        final ElementContext enclosingTag = parents.get(parents.size() - 1);

        // If there is an active filter, then see if the new tag is of any interest
        if (enclosingTag.getFilter() != null) {
            // Check for interest in new tag
            final XmlFilter filter = enclosingTag.getFilter().getSubFilter(tag);
            // If new tag has a filter, store it in the new context object
            tag.setFilter(filter);
            // If we got a filter, tell it a tag is now starting.
            if (filter != null) {
                filter.processStart(tag);
            }
        }
        // Add the new tag to the context hierarchy and reset
        parents.add(tag);
        // Reset the inter-tag text storage.
        builder.setLength(0);
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
        final ElementContext tag = parents.remove(parents.size() - 1);

        // Minor paranoia. Make sure name matches. Total waste of time, right?
        if (!localName.equals(tag.getLocalName())) {
            throw new IllegalStateException(
                    "End element `" + localName + "` does not match start element"
                    + " `" + tag.getLocalName() + '`');
        }

        // Save the text that appeared inside this tag (but not inside inner tags)
        tag.setBody(builder.toString());

        // If there is an active filter in this context, then tell it the tag is finished.
        if (tag.getFilter() != null) {
            tag.getFilter().processEnd(tag);
        }

        // Reset the inter-tag text and append the previously saved 'pre-text'.
        builder.setLength(0);
        builder.append(tag.getText());
    }

    /**
     * Gather inter-tag text.
     */
    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length) {
        builder.append(ch, start, length);
    }
}
