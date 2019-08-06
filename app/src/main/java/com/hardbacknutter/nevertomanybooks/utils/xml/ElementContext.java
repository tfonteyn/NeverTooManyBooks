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

    /** not used for now. */
    @SuppressWarnings("unused")
    @NonNull
    private final String mUri;

    /** not used for now. */
    @SuppressWarnings("unused")
    @NonNull
    private final String mQName;

    /** the inner-tag text. */
    @NonNull
    private final String mText;

    /** the mBody/text between start and end of the tag. */
    private String mBody;

    /** filter on this tag. */
    @Nullable
    private XmlFilter mFilter;

    /**
     * the user argument fed into the {@link XmlFilter#setStartAction(XmlFilter.XmlHandler, Object)}
     * and {@link XmlFilter#setEndAction(XmlFilter.XmlHandler, Object)}.
     */
    private Object mUserArg;

    /**
     * @param filter to use for this tag.
     */
    ElementContext(@NonNull final XmlFilter filter) {
        mFilter = filter;
        // the others as per SAX promises, see full constructor java doc.
        mUri = "";
        mLocalName = "";
        mQName = "";
        mAttributes = new AttributesImpl();
        mText = "";
    }

    /**
     * Same arguments coming from the SAX Handler + the current inter-tag text.
     *
     * @param uri        The Namespace URI, or the empty string if the
     *                   element has no Namespace URI or if Namespace
     *                   processing is not being performed.
     * @param localName  The local name (without prefix), or the
     *                   empty string if Namespace processing is not being
     *                   performed.
     * @param qName      The qualified name (with prefix), or the
     *                   empty string if qualified names are not available.
     * @param attributes The mAttributes attached to the element.  If
     *                   there are no mAttributes, it shall be an empty
     *                   Attributes object.
     * @param text       current inter-tag text
     */
    ElementContext(@NonNull final String uri,
                   @NonNull final String localName,
                   @NonNull final String qName,
                   @NonNull final Attributes attributes,
                   @NonNull final String text) {
        mUri = uri;
        mLocalName = localName;
        mQName = qName;
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

    @NonNull
    public Object getUserArg() {
        return mUserArg;
    }

    @SuppressWarnings("WeakerAccess")
    public void setUserArg(@Nullable final Object userArg) {
        mUserArg = userArg;
    }

    /**
     * @return the text element of a tag
     */
    @NonNull
    public String getText() {
        return mText;
    }
}
