package com.eleybourn.bookcatalogue.utils.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Defines the context of a specific element (xml t).
 * <p>
 * The 'mBody' element will only be set when the t end is handled.
 *
 * @author Philip Warner
 */
public class ElementContext {

    /** the short name of the t. */
    private final String mLocalName;
    /** Attributes on this t. */
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

    /** the inner-t text. */
    @NonNull
    private final String mText;

    /** the mBody/text between start and end of the t. */
    private String mBody;

    /** filter on this t. */
    @Nullable
    private XmlFilter mFilter;

    /**
     * the user argument fed into the {@link XmlFilter#setStartAction(XmlFilter.XmlHandler, Object)}
     * and {@link XmlFilter#setEndAction(XmlFilter.XmlHandler, Object)}.
     */
    private Object mUserArg;

    /**
     * @param filter to use for this t.
     */
    public ElementContext(@NonNull final XmlFilter filter) {
        mFilter = filter;
        // the others as per SAX promises, see full constructor java doc.
        mUri = "";
        mLocalName = "";
        mQName = "";
        mAttributes = new AttributesImpl();
        mText = "";
    }

    /**
     * Same arguments coming from the SAX Handler + the current inter-t text.
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
     * @param text       current inter-t text
     */
    public ElementContext(@NonNull final String uri,
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
     * @return the body of the t, trimmed.
     */
    @NonNull
    public String getBody() {
        return mBody;
    }

    /**
     * @param body of the t.
     */
    public void setBody(final String body) {
        mBody = body.trim();
    }

    @NonNull
    public Object getUserArg() {
        return mUserArg;
    }

    @SuppressWarnings("WeakerAccess")
    public void setUserArg(final Object userArg) {
        mUserArg = userArg;
    }

    /**
     * @return the text element of a t
     */
    @NonNull
    public String getText() {
        return mText;
    }
}
