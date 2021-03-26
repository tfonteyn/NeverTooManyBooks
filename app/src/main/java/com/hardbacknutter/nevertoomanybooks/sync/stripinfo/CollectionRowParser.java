/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.os.Bundle;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;

public class CollectionRowParser
        extends CollectionBaseParser {

    /**
     * Constructor.
     *
     * @param externalId   the book id from the web site
     * @param collectionId the user specific book id from the web site
     */
    CollectionRowParser(final long externalId,
                        final long collectionId) {
        super(externalId, collectionId);

        mIdOwned = "bezit-" + mCollectionId;
        mIdRead = "gelezen-" + mCollectionId;
        mIdWanted = "wishlist-" + mCollectionId;

        mIdLocation = "locatie-" + mCollectionId;
        mIdNotes = "opmerking-" + mCollectionId;
        mIdDateAcquired = "aankoopdatum-" + mCollectionId;
        mIdRating = "score-" + mCollectionId;
        mIdEdition = "druk-" + mCollectionId;
        mIdPricePaid = "prijs-" + mCollectionId;
        mIdAmount = "aantal-" + mCollectionId;
    }

    public void parse(@NonNull final Element root,
                      @NonNull final Bundle destBundle) {
        // The collection page only provides a link to the front cover.
        // The back cover (identifier) can only be read from the main book page.
        // IMPORTANT: we *always* parse for the cover, but *ONLY* store the url
        // in a private key. The caller should determine if the cover is actually wanted
        // (and download it) when the collection data is being processed!
        final Element coverElement = root.selectFirst("figure.stripThumbInnerWrapper > img");
        if (coverElement != null) {
            final String src = coverElement.attr("src");
            if (!src.isEmpty()) {
                destBundle.putString(StripInfoSearchEngine.SiteField.FRONT_COVER_URL, src);
            }
        }

        parseFlags(root, destBundle);
        parseDetails(root, destBundle);
    }
}
