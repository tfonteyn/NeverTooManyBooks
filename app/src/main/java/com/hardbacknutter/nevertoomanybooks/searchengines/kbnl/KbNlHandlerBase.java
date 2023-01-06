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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


abstract class KbNlHandlerBase
        extends DefaultHandler {

    protected static final String SHOW_URL = "href";
    private static final String XML_LABEL = "psi:labelledLabel";
    private static final String XML_DATA = "psi:labelledData";
    private static final String XML_LINE = "psi:line";
    private static final String XML_TEXT = "psi:text";
    /** XML tags. */
    private static final String XML_RECORD = "psi:record";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s{2,}");
    /** The final output will be written to this bundle as passed in to the constructor. */
    @NonNull
    protected final Bundle data;

    /** XML content of a single element. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    /** content of labelledData found. */
    private final List<String> currentData = new ArrayList<>();
    /** The current labelledLabel. */
    @Nullable
    private String currentLabel;
    private boolean inRecord;
    private boolean inLabel;
    private boolean inData;
    private boolean inLine;
    private boolean inText;
    /** Are we parsing a list of records {@code true}; or a detail page {@code false}. */
    private boolean isList;

    KbNlHandlerBase(@NonNull final Bundle data) {
        this.data = data;
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @Nullable
    String digits(@Nullable final CharSequence s,
                  final boolean isIsbn) {
        if (s == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end;
            // Doesn't really matter, we'll check for being a valid ISBN later anyhow.
            if (Character.isDigit(c) || isIsbn && (c == 'X' || c == 'x')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void startDocument()
            throws SAXException {
        super.startDocument();

        data.clear();
        builder.setLength(0);
        currentData.clear();
        currentLabel = null;

        inRecord = false;
        inLabel = false;
        inData = false;
        inLine = false;
        inText = false;

        isList = false;
    }

    @Override
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes)
            throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        switch (qName) {
            case XML_RECORD:
                inRecord = true;
                // a list-page record will have a ppn attribute
                final String ppn = attributes.getValue("ppn");
                isList = ppn != null && !ppn.isEmpty();
                break;

            case XML_LABEL:
                inLabel = true;
                currentLabel = null;
                break;

            case XML_DATA:
                inData = true;
                currentData.clear();
                break;

            case XML_LINE:
                inLine = true;
                break;

            case XML_TEXT:
                inText = true;
                // if in list-page mode, store the first reference found.
                if (isList && inRecord && inLine && !data.containsKey(SHOW_URL)) {
                    data.putString(SHOW_URL, attributes.getValue("href"));
                }
                builder.setLength(0);
                break;

            default:
                break;
        }
    }

    @Override
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);

        switch (qName) {
            case XML_RECORD:
                inRecord = false;
                break;

            case XML_LABEL:
                inLabel = false;
                break;

            case XML_DATA:
                if (!isList && currentLabel != null && !currentLabel.isEmpty()) {
                    processEntry(currentLabel, currentData);
                }
                inData = false;
                break;

            case XML_LINE:
                inLine = false;
                break;

            case XML_TEXT:
                if (!isList) {
                    if (inLabel && inLine && inText) {
                        currentLabel = builder.toString().split(":")[0].trim();
                    } else if (inData && inLine) {
                        // reduce whitespace; this also removed cr/lf
                        final String s = WHITESPACE_PATTERN.matcher(builder.toString())
                                                           .replaceAll(" ");
                        currentData.add(s);
                    }
                }
                inText = false;
                break;

            default:
                break;
        }
    }

    @Override
    public void characters(final char[] ch,
                           final int start,
                           final int length)
            throws SAXException {
        super.characters(ch, start, length);

        builder.append(ch, start, length);
    }

    protected abstract void processEntry(@NonNull String currentLabel,
                                         @NonNull List<String> currentData);
}
