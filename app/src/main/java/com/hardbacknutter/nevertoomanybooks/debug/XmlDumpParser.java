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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.annotation.SuppressLint;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * DEBUG only.
 */
@SuppressWarnings("RedundantThrows")
@SuppressLint("LogConditional")
public class XmlDumpParser
        extends DefaultHandler {

    /** Log tag. */
    private static final String TAG = "XmlDumpParser";

    private boolean namespaceBegin;

    private String currentNamespace;

    private String currentNamespaceUri;

    @SuppressWarnings("FieldCanBeLocal")
    private Locator locator;

    @CallSuper
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
    }

    @CallSuper
    public void startDocument()
            throws SAXException {
        Logger.d(TAG, "startDocument", "<?xml version=\"1.0\"?>");
    }

    @CallSuper
    public void endDocument()
            throws SAXException {
        Logger.d(TAG, "endDocument", "");
    }

    @CallSuper
    public void startPrefixMapping(final String prefix,
                                   final String uri)
            throws SAXException {
        namespaceBegin = true;
        currentNamespace = prefix;
        currentNamespaceUri = uri;
    }

    @CallSuper
    public void startElement(@NonNull final String namespaceURI,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes)
            throws SAXException {

        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(qName);
        if (namespaceBegin) {
            sb.append(" xmlns:").append(currentNamespace).append("=\"")
              .append(currentNamespaceUri).append("\"");
            namespaceBegin = false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ").append(attributes.getQName(i)).append("=\"")
              .append(attributes.getValue(i)).append("\"");
        }
        sb.append(">");
        Logger.d(TAG, "startElement", sb.toString());
    }

    @CallSuper
    public void endElement(final String namespaceURI,
                           final String localName,
                           final String qName)
            throws SAXException {
        Logger.d(TAG, "endElement", "</" + qName + ">");
    }

    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length)
            throws SAXException {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Logger.d(TAG, "characters", sb.toString());
    }

    @CallSuper
    public void ignorableWhitespace(final char[] ch,
                                    final int start,
                                    final int length)
            throws SAXException {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Logger.d(TAG, "ignorableWhitespace", sb.toString());
    }

    @CallSuper
    public void processingInstruction(final String target,
                                      final String data)
            throws SAXException {
        Logger.d(TAG, "processingInstruction", "<?" + target + " " + data + "?>");
    }

    @CallSuper
    public void skippedEntity(final String name)
            throws SAXException {
        Logger.d(TAG, "skippedEntity", "&" + name + ";");
    }
}
