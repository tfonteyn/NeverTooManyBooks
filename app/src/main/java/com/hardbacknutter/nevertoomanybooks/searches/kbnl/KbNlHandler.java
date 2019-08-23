/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.kbnl;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

class KbNlHandler
        extends DefaultHandler {

    private static final String XML_LABEL = "psi:labelledLabel";
    private static final String XML_DATA = "psi:labelledData";
    private static final String XML_LINE = "psi:line";
    private static final String XML_TEXT = "psi:text";

    @NonNull
    private final Bundle mBookData;
    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();

    /** XML content. */
    private final StringBuilder mBuilder = new StringBuilder();
    private final List<String> mCurrentData = new ArrayList<>();
    private boolean inLabel;
    private boolean inData;
    private boolean inLine;
    private boolean inText;
    private String mCurrentLabel;

    /**
     * Constructor.
     *
     * @param bookData bundle to populate.
     */
    KbNlHandler(@NonNull final Bundle bookData) {
        mBookData = bookData;
    }

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes attributes) {
        switch (qName) {
            case XML_LABEL:
                mCurrentLabel = null;
                inLabel = true;
                break;

            case XML_DATA:
                mCurrentData.clear();
                inData = true;
                break;

            case XML_LINE:
                inLine = true;
                break;

            case XML_TEXT:
                mBuilder.setLength(0);
                inText = true;
                break;

            default:
                break;
        }
    }

    @Override
    public void endElement(final String uri,
                           final String localName,
                           final String qName) {
        switch (qName) {
            case XML_LABEL:
                inLabel = false;
                break;

            case XML_DATA:
                if (mCurrentLabel != null && !mCurrentLabel.isEmpty()) {
                    processEntry(mCurrentLabel, mCurrentData);
                }
                inData = false;
                break;

            case XML_LINE:
                inLine = false;
                break;

            case XML_TEXT:
                if (inLabel) {
                    mCurrentLabel = mBuilder.toString().split(":")[0].trim();

                } else if (inLine) {
                    mCurrentData.add(mBuilder.toString().trim());
                }
                inText = false;
                break;

            default:
                break;
        }
    }

    @Override
    public void endDocument() {
        if (!mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }

        // As kb.nl is dutch, we're going to assume that all books are in Dutch.
        if (!mBookData.isEmpty() && !mBookData.containsKey(DBDefinitions.KEY_LANGUAGE)) {
            mBookData.putString(DBDefinitions.KEY_LANGUAGE, "nld");
        }
    }

    @Override
    public void characters(final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
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
    private String digits(@Nullable final String s,
                          final boolean isIsbn) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c) || (isIsbn && Character.toUpperCase(c) == 'X')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Labels for both Dutch (default) and English are listed.
     * <p>
     * Note that "Colorist" is also used in Dutch.
     */
    private void processEntry(@NonNull final String currentLabel,
                              @NonNull final List<String> currentData) {
        switch (currentLabel) {
            case "Title":
            case "Titel":
                processTitle(currentData);
                break;

            case "Author":
            case "Auteur":
                processAuthor(currentData, Author.TYPE_PRIMARY);
                break;
            case "Collaborator":
            case "Medewerker":
                processAuthor(currentData, Author.TYPE_CONTRIBUTOR);
                break;
            case "Artist":
            case "Kunstenaar":
                processAuthor(currentData, Author.TYPE_ARTIST_PRIMARY);
                break;

            case "Colorist":
                processAuthor(currentData, Author.TYPE_COLORIST);
                break;
            case "Translator":
            case "Vertaler":
                processAuthor(currentData, Author.TYPE_TRANSLATOR);
                break;

            case "Series":
            case "Reeks":
                processSeries(currentData);
                break;

            case "Part(s)":
            case "Deel / delen":
                // This label can appear without there being a "Series" label.
                // In that case, the book title is presumed to also be the series title.
                processSeriesNumber(currentData);
                break;

            case "Publisher":
            case "Uitgever":
                processPublisher(currentData);
                break;

            case "Year":
            case "Jaar":
                processDatePublished(currentData);
                break;

            case "Extent":
            case "Omvang":
                processPages(currentData);
                break;

            case "ISBN":
                processIsbn(currentData);
                break;

            default:
                Logger.warnWithStackTrace(this, "currentLabel=" + currentLabel);
                break;
        }
    }

    /**
     * <pre>{@code
     *  <psi:labelledData>
     *    <psi:line>
     *      <psi:text>De</psi:text>
     *      <psi:text href="CLK?IKT=4&amp;TRM=Foundation">Foundation</psi:text>
     *      <psi:text>/ Isaac Asimov ; [vert. uit het Engels door Jack Kröner]</psi:text>
     *    </psi:line>
     *  </psi:labelledData>
     *
     *  <psi:labelledData>
     *    <psi:line>
     *      <psi:text href="CLK?IKT=12&amp;TRM=422449059&amp;REC=*">
     *          De buitengewone reis /  Silvio Camboni
     *      </psi:text>
     *    </psi:line>
     *  </psi:labelledData>
     * }</pre>
     */
    private void processTitle(@NonNull final List<String> currentData) {
        StringBuilder sbTitle = new StringBuilder();
        for (String name : currentData) {
            sbTitle.append(name).append(" ");
        }
        String cleanedTitle = sbTitle.toString().split("/")[0].trim();
        mBookData.putString(DBDefinitions.KEY_TITLE, cleanedTitle);
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="REL?PPN=068561504">Isaak Judovič Ozimov (1920-1992)</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processAuthor(@NonNull final List<String> currentData,
                               final int type) {
        for (String name : currentData) {
            String cleanedString = name.split("\\(")[0].trim();
            Author author = Author.fromString(cleanedString);
            author.setType(type);
            mAuthors.add(author);
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=12&amp;TRM=841288933&amp;REC=*">Foundation-trilogie</psi:text>
     *     <psi:text>;</psi:text>
     *     <psi:text href="CLK?IKT=12&amp;TRM=841288933&amp;REC=*">dl. 1</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processSeries(@NonNull final List<String> currentData) {
        Series series = new Series(currentData.get(0));
        if (currentData.size() > 2) {
            String number = Series.cleanupSeriesNumber(currentData.get(2));
            series.setNumber(number);
        }
        mSeries.add(series);
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>Deel 1</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processSeriesNumber(@NonNull final List<String> currentData) {
        String number = Series.cleanupSeriesNumber(currentData.get(0));
        String title = mBookData.getString(DBDefinitions.KEY_TITLE);
        // should never happen, but paranoia...
        if (title != null) {
            Series series = new Series(title);
            series.setNumber(number);
            mSeries.add(series);
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text mark="highlight">90-229-5335-1</psi:text>
     *     <psi:text>(geb.)</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text mark="highlight">978-94-6373145-4</psi:text>
     *     <psi:text> (paperback)</psi:text>
     *   </psi:line>
     *   <psi:line>
     *     <psi:text>&#xA0;</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processIsbn(@NonNull final List<String> currentData) {
        if (!mBookData.containsKey(DBDefinitions.KEY_ISBN)) {
            mBookData.putString(DBDefinitions.KEY_ISBN, digits(currentData.get(0), true));
            if (currentData.size() > 1) {
                if (!mBookData.containsKey(DBDefinitions.KEY_FORMAT)) {
                    String format = currentData.get(1).trim();
                    if (format.startsWith("(")) {
                        format = format.substring(1, format.length() - 1);
                    }
                    if (!format.isEmpty()) {
                        mBookData.putString(DBDefinitions.KEY_FORMAT, format);
                    }
                }
            }
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Breda">Breda</psi:text>
     *     <psi:text> : </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Dark">Dark</psi:text>
     *     <psi:text> </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Dragon">Dragon</psi:text>
     *     <psi:text> </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Books">Books</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Utrecht">Utrecht</psi:text>
     *     <psi:text>[</psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=etc.">etc.</psi:text>
     *     <psi:text>] :</psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Bruna">Bruna</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processPublisher(@NonNull final List<String> currentData) {
        if (!mBookData.containsKey(DBDefinitions.KEY_PUBLISHER)) {
            StringBuilder sbPublisher = new StringBuilder();
            for (String name : currentData) {
                if (!name.isEmpty()) {
                    sbPublisher.append(name).append(" ");
                }
            }
            String publisherName = sbPublisher.toString();
            if (publisherName.contains(":")) {
                publisherName = publisherName.split(":")[1].trim();
            }
            mBookData.putString(DBDefinitions.KEY_PUBLISHER, publisherName);
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>1983</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     psi:text>[2019]</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processDatePublished(@NonNull final List<String> currentData) {
        if (!mBookData.containsKey(DBDefinitions.KEY_DATE_PUBLISHED)) {
            String year = currentData.get(0);
            if (year.startsWith("[")) {
                year = year.substring(1, year.length() - 1);
            }

            if (!year.isEmpty()) {
                mBookData.putString(DBDefinitions.KEY_DATE_PUBLISHED, year);
            }
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>48 pagina's</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>156 p</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processPages(@NonNull final List<String> currentData) {
        if (!mBookData.containsKey(DBDefinitions.KEY_PAGES)) {
            try {
                String cleanedString = currentData.get(0).split(" ")[0];
                int pages = Integer.parseInt(cleanedString);
                mBookData.putString(DBDefinitions.KEY_PAGES, String.valueOf(pages));
            } catch (@NonNull final NumberFormatException e) {
                // use source
                mBookData.putString(DBDefinitions.KEY_PAGES, currentData.get(0));
            }
        }
    }

}
