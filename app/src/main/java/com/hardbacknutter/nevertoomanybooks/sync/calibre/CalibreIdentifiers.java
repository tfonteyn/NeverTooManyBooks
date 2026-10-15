/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public final class CalibreIdentifiers {
    /**
     * Calibre treats the ISBN as just another identifier.
     * "isbn_10", "isbn_13" are also used, in particular by the ISFDB plugin for Calibre
     * We're only using them if "isbn" is not present at all.
     */
    public static final String IDENTIFIER_ISBN = "isbn";
    private static final String AMAZON = "amazon";

    /**
     * Key's that map 1:1 are not listed.
     *
     * @see #convertWhenEncoding(String)
     */
    private static final Map<String, String> IDENTIFIER_MAPPING_WRITER = Map.ofEntries(
            // It's not clear why calibre prefers 'amazon' above 'asin'
            // but heck, just convert it.
            Map.entry(Identifier.SID_ASIN, AMAZON)
    );

    /**
     * Key's that map 1:1 are not listed.
     * Note that "amazon*", "isbn*" are handled as special cases.
     * <p>
     * This list only maps <strong>known</strong> keys
     * from the predefined list at app install time.
     * <p>
     * Other keys we've seen now and then:
     * "epl"
     * "kobo"
     *
     * @see #convertWhenDecoding(String, String, Book, List)
     */
    private static final Map<String, String> IDENTIFIER_MAPPING_READER = Map.ofEntries(
            // mobi is obsolete so we always map it to pure 'asin'
            Map.entry("mobi-asin", Identifier.SID_ASIN),
            // Calibre typically uses 'uri' but sometimes we see 'url'
            Map.entry("url", Identifier.SID_URI)
    );

    private CalibreIdentifiers() {
    }

    /**
     * Convert the calibre identifier (the given calKey) into ours.
     *
     * @param calKey the key of the Calibre identifier
     * @param sid    the value of the Calibre identifier
     * @param book   may be updated with product-code (isbn)
     * @param ivs    to update with the converted keys
     */
    public static void convertWhenDecoding(@NonNull final String calKey,
                                           @NonNull final String sid,
                                           @NonNull final Book book,
                                           @NonNull final List<Identifier.Value> ivs) {
        if (IDENTIFIER_ISBN.equals(calKey)) {
            // The pure "isbn" key always wins.
            book.setRawProductCode(sid);
            // Done, we never add the "isbn" key to the identifiers.
            return;
        }

        if (calKey.length() > 4 && calKey.startsWith(IDENTIFIER_ISBN)) {
            // "isbn_10" and "isbn_13" MAY set the product-code,
            // but only if "isbn" has not done so already.
            if (!book.hasProductCode()) {
                book.setRawProductCode(sid);
            }
            // Drop through!
            // We want to add the "isbn_10"/"isbn_13" keys to the identifiers.
        }

        if (calKey.length() >= 6 && calKey.startsWith(AMAZON)) {
            // Other than strict "amazon", there are variants
            // for local sites; e.g. "amazon_nl", "amazon_fr",...
            // The actual ASIN is always the same,
            // so just use the first one found.
            if (book.getIdentifierValue(Identifier.SID_ASIN).isEmpty()) {
                ivs.add(new Identifier.Value(Identifier.SID_ASIN, sid));
            }
            return;
        }

        // Map the calKey to our key, or if not found,
        // just use the calKey itself
        final String key = IDENTIFIER_MAPPING_READER.getOrDefault(calKey, calKey);
        //noinspection DataFlowIssue
        ivs.add(new Identifier.Value(key, sid));
    }

    /**
     * Convert our keys into Calibre keys.
     *
     * @param key our key
     *
     * @return the converted key, or when none found our key
     */
    @Nullable
    public static String convertWhenEncoding(@NonNull final String key) {
        return IDENTIFIER_MAPPING_WRITER.getOrDefault(key, key);
    }
}
