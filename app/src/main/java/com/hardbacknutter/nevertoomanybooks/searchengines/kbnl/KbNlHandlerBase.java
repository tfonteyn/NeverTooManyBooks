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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;

import com.hardbacknutter.nevertoomanybooks.debug.XmlDumpParser;

abstract class KbNlHandlerBase
        extends XmlDumpParser {

    /** XML tags. */
    private static final String XML_LABEL = "psi:labelledLabel";
    private static final String XML_DATA = "psi:labelledData";
    private static final String XML_LINE = "psi:line";
    private static final String XML_TEXT = "psi:text";

    /** XML content. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    private final List<String> currentData = new ArrayList<>();
    private boolean inLabel;
    private boolean inData;
    private boolean inLine;
    private boolean inText;
    @Nullable
    private String currentLabel;

    protected abstract void processEntry(@NonNull String currentLabel,
                                         @NonNull List<String> currentData);

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
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {
        super.startElement(uri, localName, qName, attributes);

        switch (qName) {
            case XML_LABEL:
                currentLabel = null;
                inLabel = true;
                break;

            case XML_DATA:
                currentData.clear();
                inData = true;
                break;

            case XML_LINE:
                inLine = true;
                break;

            case XML_TEXT:
                builder.setLength(0);
                inText = true;
                break;

            default:
                break;
        }
    }

    @Override
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {
        super.endElement(uri, localName, qName);

        switch (qName) {
            case XML_LABEL:
                inLabel = false;
                break;

            case XML_DATA:
                if (currentLabel != null && !currentLabel.isEmpty()) {
                    processEntry(currentLabel, currentData);
                }
                inData = false;
                break;

            case XML_LINE:
                inLine = false;
                break;

            case XML_TEXT:
                if (inLabel) {
                    currentLabel = builder.toString().split(":")[0].trim();

                } else if (inLine) {
                    currentData.add(builder.toString().trim());
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
                           final int length) {
        super.characters(ch, start, length);

        builder.append(ch, start, length);
    }
}
