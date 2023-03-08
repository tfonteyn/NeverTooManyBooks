/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Defines the context of a specific element (xml tag).
 * <p>
 * The 'mBody' element will only be set when the tag end is handled.
 */
public class ElementContext {

    /** the short name of the tag. */
    @NonNull
    private final String localName;
    /** Attributes on this tag. */
    @NonNull
    private final Attributes attributes;
    /** the inner-tag text. */
    @NonNull
    private final String text;
    /** the body/text between start and end of the tag. */
    private String body;
    /** filter on this tag. */
    @Nullable
    private XmlFilter filter;

    /**
     * @param filter to use for this tag.
     */
    ElementContext(@NonNull final XmlFilter filter) {
        this.filter = filter;

        localName = "";
        attributes = new AttributesImpl();
        text = "";
    }

    /**
     * Same arguments coming from the SAX Handler + the current inter-tag text.
     *
     * @param localName  The local name (without prefix), or the
     *                   empty string if Namespace processing is not being
     *                   performed.
     * @param attributes The Attributes attached to the element.  If
     *                   there are no Attributes, it shall be an empty
     *                   Attributes object.
     * @param text       current inter-tag text
     */
    ElementContext(@NonNull final String localName,
                   @NonNull final Attributes attributes,
                   @NonNull final String text) {
        this.localName = localName;
        this.attributes = attributes;
        this.text = text;
    }

    @Nullable
    public XmlFilter getFilter() {
        return filter;
    }

    public void setFilter(@Nullable final XmlFilter filter) {
        this.filter = filter;
    }

    @NonNull
    String getLocalName() {
        return localName;
    }

    @NonNull
    public Attributes getAttributes() {
        return attributes;
    }

    /**
     * @return the body of the tag, trimmed.
     */
    @NonNull
    public String getBody() {
        return body;
    }

    /**
     * @param body of the tag.
     */
    public void setBody(@NonNull final String body) {
        this.body = body.trim();
    }

    /**
     * @return the text element of a tag
     */
    @NonNull
    public String getText() {
        return text;
    }
}
