/*
 * @Copyright 2018-2021 HardBackNutter
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
    private final String mLocalName;
    /** Attributes on this tag. */
    @NonNull
    private final Attributes mAttributes;
    /** the inner-tag text. */
    @NonNull
    private final String mText;
    /** the body/text between start and end of the tag. */
    private String mBody;
    /** filter on this tag. */
    @Nullable
    private XmlFilter mFilter;

    /**
     * @param filter to use for this tag.
     */
    public ElementContext(@NonNull final XmlFilter filter) {
        mFilter = filter;

        mLocalName = "";
        mAttributes = new AttributesImpl();
        mText = "";
    }

    /**
     * Same arguments coming from the SAX Handler + the current inter-tag text.
     *
     * @param localName  The local name (without prefix), or the
     *                   empty string if Namespace processing is not being
     *                   performed.
     * @param attributes The mAttributes attached to the element.  If
     *                   there are no mAttributes, it shall be an empty
     *                   Attributes object.
     * @param text       current inter-tag text
     */
    public ElementContext(@NonNull final String localName,
                          @NonNull final Attributes attributes,
                          @NonNull final String text) {
        mLocalName = localName;
        mAttributes = attributes;
        mText = text;
    }

    @Nullable
    public XmlFilter getFilter() {
        return mFilter;
    }

    public void setFilter(@Nullable final XmlFilter filter) {
        mFilter = filter;
    }

    @NonNull
    public String getLocalName() {
        return mLocalName;
    }

    @NonNull
    public Attributes getAttributes() {
        return mAttributes;
    }

    /**
     * @return the body of the tag, trimmed.
     */
    @NonNull
    public String getBody() {
        return mBody;
    }

    /**
     * @param body of the tag.
     */
    public void setBody(@NonNull final String body) {
        mBody = body.trim();
    }

    /**
     * @return the text element of a tag
     */
    @NonNull
    public String getText() {
        return mText;
    }
}
