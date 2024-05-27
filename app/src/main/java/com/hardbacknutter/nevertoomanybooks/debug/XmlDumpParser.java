/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.annotation.Nullable;

import com.hardbacknutter.util.logger.LoggerFactory;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * DEBUG only.
 * <p>
 * When needed replace "extends DefaultHandler" on a class that needs debugging
 * with "extends XmlDumpParser" (and obviously make sure all methods call the super
 * as indicated by @CallSuper
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
    public void setDocumentLocator(@NonNull final Locator locator) {
        this.locator = locator;
    }

    @CallSuper
    public void startDocument()
            throws SAXException {
        LoggerFactory.getLogger()
                      .d(TAG, "startDocument", "<?xml version=\"1.0\"?>");
    }

    @CallSuper
    public void endDocument()
            throws SAXException {
        LoggerFactory.getLogger().d(TAG, "endDocument", "");
    }

    @CallSuper
    public void startPrefixMapping(@NonNull final String prefix,
                                   @NonNull final String uri)
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
        LoggerFactory.getLogger().d(TAG, "startElement", sb.toString());
    }

    @CallSuper
    public void endElement(@NonNull final String namespaceURI,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        LoggerFactory.getLogger().d(TAG, "endElement", "</" + qName + ">");
    }

    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length)
            throws SAXException {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        LoggerFactory.getLogger().d(TAG, "characters", sb.toString());
    }

    @CallSuper
    public void ignorableWhitespace(@NonNull final char[] ch,
                                    final int start,
                                    final int length)
            throws SAXException {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        LoggerFactory.getLogger().d(TAG, "ignorableWhitespace", sb.toString());
    }

    @CallSuper
    public void processingInstruction(@NonNull final String target,
                                      @Nullable final String data)
            throws SAXException {
        LoggerFactory.getLogger()
                     .d(TAG, "processingInstruction", "<?" + target + " " + data + "?>");
    }

    @CallSuper
    public void skippedEntity(@NonNull final String name)
            throws SAXException {
        LoggerFactory.getLogger().d(TAG, "skippedEntity", "&" + name + ";");
    }
}
