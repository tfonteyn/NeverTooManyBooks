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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.EditBookTocFragment;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;

abstract class AbstractBase
        extends JsoupBase {

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors.
     * <p>
     * Original code in {@link EditBookTocFragment} had:
     * {@code CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";}
     * <p>
     * Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * So that became:
     * {@code private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";}
     * <p>
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final Pattern CLEANUP_TITLE_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

    /** connect-timeout. Default is 5_000. */
    private static final int CONNECT_TIMEOUT = 30_000;
    /** read-timeout. Default is 10_000. */
    private static final int READ_TIMEOUT = 60_000;
    private static final Pattern CR_PATTERN = Pattern.compile("\n", Pattern.LITERAL);


    /**
     * Constructor.
     */
    AbstractBase() {
        super();
        initSite();
    }

    /**
     * Constructor for mocking.
     *
     * @param doc the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    AbstractBase(@NonNull final Document doc) {
        super(doc);
        initSite();
    }

    /**
     * Set some site specific parameters.
     */
    private void initSite() {
        setConnectTimeout(CONNECT_TIMEOUT);
        setReadTimeout(READ_TIMEOUT);
        setCharSetName(IsfdbManager.CHARSET_DECODE_PAGE);
    }

    @NonNull
    String cleanUpName(@NonNull final String s) {
        String tmp = CR_PATTERN.matcher(s.trim())
                               .replaceAll(Matcher.quoteReplacement(" "));
        return CLEANUP_TITLE_PATTERN.matcher(tmp)
                                    .replaceAll("")
                                    .trim();
    }

    /**
     * A url ends with 'last'123.  Strip and return the '123' part.
     *
     * @param url  to handle
     * @param last character to look for as last-index
     *
     * @return the number
     */
    long stripNumber(@NonNull final String url,
                     final char last) {
        int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return 0;
        }

        return Long.parseLong(url.substring(index));
    }

    String stripString(@NonNull final String url,
                       @SuppressWarnings("SameParameterValue") final char last) {
        int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return "";
        }

        return url.substring(index);
    }
}
