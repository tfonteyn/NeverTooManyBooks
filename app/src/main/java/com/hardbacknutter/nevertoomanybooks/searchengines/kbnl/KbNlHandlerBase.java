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
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.entities.BookData;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


abstract class KbNlHandlerBase
        extends DefaultHandler {
//        extends XmlDumpParser {

    /** Special key into the data bundle for the KBNL 'SHW' url part is this was a list-page. */
    protected static final String BKEY_SHOW_URL = "kbnl_href";
    /** Special key into the data bundle for the KBNL 'db' number of this request. */
    static final String BKEY_DB_VERSION = "kbnl_DB";
    /** Special key into the data bundle for the KBNL 'set' number of this request. */
    static final String BKEY_SET_NUMBER = "kbnl_SET";
    private static final String PSI_SESSION_VAR = "psi:sessionVar";
    private static final String PSI_LABEL = "psi:labelledLabel";
    private static final String PSI_DATA = "psi:labelledData";
    private static final String PSI_LINE = "psi:line";
    private static final String PSI_TEXT = "psi:text";
    /** XML tags. */
    private static final String PSI_RECORD = "psi:record";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s{2,}");
    /** The final output will be written to this bundle as passed in to the constructor. */
    @NonNull
    protected final BookData data;

    /** XML content of a single element. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder builder = new StringBuilder();
    /** content of labelledData found. */
    private final List<String> currentData = new ArrayList<>();
    /** The current labelledLabel. */
    @Nullable
    private String currentLabel;
    @Nullable
    private String currentSessionVar;

    private boolean inSessionVar;
    private boolean inRecord;
    private boolean inLabel;
    private boolean inData;
    private boolean inLine;
    private boolean inText;
    /** Are we parsing a list of records {@code true}; or a detail page {@code false}. */
    private boolean isList;

    KbNlHandlerBase(@NonNull final BookData data) {
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

        data.clearData();
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
            case PSI_SESSION_VAR:
                inSessionVar = true;
                currentSessionVar = attributes.getValue("name");
                break;

            case PSI_RECORD:
                inRecord = true;
                // a list-page record will have a ppn attribute
                final String ppn = attributes.getValue("ppn");
                isList = ppn != null && !ppn.isEmpty();
                break;

            case PSI_LABEL:
                inLabel = true;
                currentLabel = null;
                break;

            case PSI_DATA:
                inData = true;
                currentData.clear();
                break;

            case PSI_LINE:
                inLine = true;
                break;

            case PSI_TEXT:
                inText = true;
                // if in list-page mode, store the first reference found.
                if (isList && inRecord && inLine && !data.contains(BKEY_SHOW_URL)) {
                    data.putString(BKEY_SHOW_URL, attributes.getValue("href"));
                }
                break;

            default:
                break;
        }

        builder.setLength(0);
    }

    @Override
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);

        switch (qName) {
            case PSI_SESSION_VAR:
                // sanity check, should not be null at this point
                if (currentSessionVar != null) {
                    switch (currentSessionVar) {
                        case "DB":
                            data.putString(BKEY_DB_VERSION, builder.toString());
                            break;
                        case "SET":
                            data.putString(BKEY_SET_NUMBER, builder.toString());
                            break;
                        default:
                            break;
                    }
                }
                inSessionVar = false;
                break;

            case PSI_RECORD:
                inRecord = false;
                break;

            case PSI_LABEL:
                inLabel = false;
                break;

            case PSI_DATA:
                if (!isList && currentLabel != null && !currentLabel.isEmpty()) {
                    processEntry(currentLabel, currentData);
                }
                inData = false;
                break;

            case PSI_LINE:
                inLine = false;
                break;

            case PSI_TEXT:
                if (!isList) {
                    if (inLabel && inLine && inText) {
                        currentLabel = builder.toString().split(":")[0].strip();
                    } else if (inData && inLine) {
                        // reduce whitespace; this also removes cr/lf
                        final String s = WHITESPACE_PATTERN.matcher(builder.toString())
                                                           .replaceAll(" ")
                                                           .strip();
                        if (!s.isBlank()) {
                            currentData.add(s);
                        }
                    }
                }
                inText = false;
                break;

            default:
                break;
        }

        builder.setLength(0);
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
