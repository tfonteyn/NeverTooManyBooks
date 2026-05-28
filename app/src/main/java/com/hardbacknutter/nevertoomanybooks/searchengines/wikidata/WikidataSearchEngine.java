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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;


public class WikidataSearchEngine
        extends SearchEngineBase {

    private static final String SITE_URL = "https://www.wikidata.org";
    private static final String BOOK_URL = "https://www.wikidata.org/wiki/%s";
    private static final String AUTHOR_URL = "https://www.wikidata.org/wiki/%s";
    private static final String SERIES_URL = "https://www.wikidata.org/wiki/%s";

    private static final String PREFERENCE_KEY = "wikidata";

    // Claim numbers for book identifiers.
    //    private static final Map<String, String> SIDS = Map.ofEntries(
    //            Map.entry("P5749", Identifier.SID_ASIN),
    //            Map.entry("P5199", Identifier.SID_BRITISH_LIBRARY),
    //            Map.entry("P10386", Identifier.SID_DATABAZE_KNIH),
    //            Map.entry("P1292", Identifier.SID_DNB),
    //            Map.entry("P6442", Identifier.SID_DOUBAN),
    //            Map.entry("P7439", Identifier.SID_FANTLAB),
    //            Map.entry("P8383", Identifier.SID_GOODREADS),
    //            Map.entry("P675", Identifier.SID_GOOGLE),
    //            Map.entry("P1234", Identifier.SID_ISFDB),
    //            Map.entry("P9088", Identifier.SID_KBR),
    //            Map.entry("P1144", Identifier.SID_LCCN),
    //            Map.entry("P1085", Identifier.SID_LIBRARY_THING),
    //            Map.entry("P2191", Identifier.SID_NILF),
    //            Map.entry("P5571", Identifier.SID_NOOSFERE),
    //            Map.entry("P10832", Identifier.SID_OCLC),
    //            Map.entry("P648", Identifier.SID_OPEN_LIBRARY),
    //            Map.entry("P6373", Identifier.SID_PORBASE)
    //    );
    //
    //    private static final String SEARCH_BY_ISBN =
    //            "https://www.wikidata.org/wiki/Special:Search?search=ISBN+%1$s";

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public WikidataSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    /**
     * Called during startup to initialise the immutable/default engine configuration.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @return {@link EngineId.Builder}
     */
    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_wikidata,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    "https://www.wikidata.org",
                                    Locale.US)
                .setIdentifierKey(Identifier.SID_WIKIDATA);
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_wikidata);
        return Set.of(
                Identifier.createBook(
                        Identifier.SID_WIKIDATA,
                        Identifier.Type.Text,
                        name,
                        SITE_URL,
                        BOOK_URL),
                Identifier.createAuthor(
                        Identifier.SID_WIKIDATA,
                        Identifier.Type.Text,
                        name,
                        SITE_URL,
                        AUTHOR_URL,
                        null),
                Identifier.createSeries(
                        Identifier.SID_WIKIDATA,
                        Identifier.Type.Text,
                        name,
                        SITE_URL,
                        SERIES_URL)
        );
    }
}
