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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

class WikidataBookParser {

    // json value element
    private static final String VALUE = "value";

    /** Splitter for the sparql GROUP_CONCAT columns. */
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\|");

    // Last updated: 2026-06-21
    // Keys are always uppercase, no need to change case
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("P179", Identifier.SID_WIKIDATA),

            Map.entry("P8619", Identifier.SID_BEDETHEQUE),
            Map.entry("P227", Identifier.SID_DNB),
            Map.entry("P10318", Identifier.SID_DOUBAN),
            Map.entry("P6947", Identifier.SID_GOODREADS),
            Map.entry("P1235", Identifier.SID_ISFDB),
            Map.entry("P13137", Identifier.SID_ISFDB_PUB_SERIES),
            Map.entry("P9088", Identifier.SID_KBR),
            Map.entry("P10419", Identifier.SID_LAST_DODO_NL),
            Map.entry("P244", Identifier.SID_LCCN),
            Map.entry("P8513", Identifier.SID_LIBRARY_THING),
            Map.entry("P5792", Identifier.SID_NOOSFERE),
            Map.entry("P243", Identifier.SID_OCLC),
            Map.entry("P214", Identifier.SID_VIAF)
    );

    @NonNull
    private final JSONObject document;
    @NonNull
    private final Book book;

    WikidataBookParser(@NonNull final JSONObject document,
                              @NonNull final ProductCode searchedCode,
                              @NonNull final Book book) {
        this.document = document;
        this.book = book;

        // we don't get it from the site, just add it
        book.setRawProductCode(searchedCode.asText());
    }

    void parse()
            throws JSONException {

        // We expect one result
        final JSONObject results = document.optJSONObject("results");
        if (results == null) {
            return;
        }
        final JSONArray bindings = results.optJSONArray("bindings");
        if (bindings == null) {
            return;
        }
        final JSONObject item = bindings.optJSONObject(0);
        if (item == null) {
            return;
        }

        final List<Identifier.Value> ivs = new ArrayList<>();

        JSONObject o;

        // The title is mandatory, otherwise throw!
        o = item.getJSONObject("title");
        book.setTitle(o.getString(VALUE));
        // and the item itself, being the SID, otherwise throw!
        o = item.getJSONObject("item");

        final String sid = getQ(o.getString(VALUE));
        // paranoia...
        if (sid != null && !sid.isEmpty()) {
            ivs.add(new Identifier.Value(Identifier.SID_WIKIDATA, sid));
        }

        // all else is optional.
        o = item.optJSONObject("langCode", null);
        if (o != null) {
            book.setLanguage(o.optString(VALUE, null));
        }
        o = item.optJSONObject("newsPaperFormatLabel", null);
        if (o != null) {
            book.setFormat(o.optString(VALUE, null));
        }
        o = item.optJSONObject("publisherLabel", null);
        if (o != null) {
            final String s = o.optString(VALUE, null);
            if (s != null && !s.isBlank()) {
                book.add(Publisher.from(s));
            }
        }

        // Always create a Series with the same title.
        // As far as we know, there is no volume number available
        // as Wikidata is returning the ISSN series data only.
        final Series series = Series.from(book.getTitle());
        final PublicationFrequency frequency = parsePubAmountAndUnit(item);
        series.setPublicationFrequency(frequency);
        series.setIdentifierValue(Identifier.SID_ISSN, book.getRawProductCode());
        book.add(series);

        o = item.optJSONObject("currentEditors", null);
        if (o != null) {
            parseEditors(o, book);
        }

        o = item.optJSONObject("identifiers", null);
        if (o != null) {
            parseIdentifiers(o, ivs);
        }

        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }

        // If didn't get an editor, use the publisher if we have one.
        SearchEngineUtils.ensurePeriodicalEditor(book);
    }

    private void parseEditors(@NonNull final JSONObject root,
                              @NonNull final Book book) {
        final String s = root.optString(VALUE, null);
        if (s == null || s.isBlank()) {
            return;
        }
        for (final String entry : SPLIT_PATTERN.split(s)) {
            final Author author = Author.from(entry);
            author.setRole(AuthorRole.EDITOR);
            book.add(author);
        }
    }

    /**
     * Parse the identifiers. We only add/map them when we recognise them,
     * because wikidata can contains a huge amount of them; most of which are not useful for us.
     *
     * @param root to parse
     * @param ivs  to update
     */
    private void parseIdentifiers(@NonNull final JSONObject root,
                                  @NonNull final List<Identifier.Value> ivs) {
        final String s = root.optString(VALUE, null);
        if (s == null || s.isBlank()) {
            return;
        }
        // Avoid duplicates overwriting
        final Set<String> keys = new HashSet<>();

        for (final String entry : SPLIT_PATTERN.split(s)) {
            final String[] id = entry.split(":");
            // paranoia
            if (id.length == 2) {
                // Filter on listed keys only.
                final String key = IDENTIFIER_MAPPING.get(id[0]);
                if (key != null && !keys.contains(key)) {
                    keys.add(key);
                    ivs.add(new Identifier.Value(key, id[1]));
                }
            }
        }
    }

    @NonNull
    private PublicationFrequency parsePubAmountAndUnit(@NonNull final JSONObject item) {
        final JSONObject pubUnit = item.optJSONObject("pubUnitQ", null);
        if (pubUnit == null) {
            return new PublicationFrequency(PublicationFrequency.Type.Unknown, 0, false);
        }

        final String q = getQ(pubUnit.optString(VALUE));
        if (q == null) {
            return new PublicationFrequency(PublicationFrequency.Type.Unknown, 0, false);
        }

        // Default to 1 if pubAmount should be missing
        int amount = 1;
        boolean isFractional = false;

        final JSONObject pubAmount = item.optJSONObject("pubAmount", null);
        if (pubAmount != null && "literal".equals(pubAmount.optString("type"))) {
            final float f = pubAmount.optFloat(VALUE, 0);
            if (f > 0.0f && f < 1.0f) {
                // e.g., 0.5 days means twice a day.
                // We flip it to get 2, and flag it as being fractional
                amount = (int) (1 / f);
                isFractional = true;
            } else if (f >= 1.0f) {
                amount = (int) f;
            }
        }

        switch (q) {
            case "Q573": {
                // Day
                // If it was a flipped fraction (e.g., 0.5), it means "2 times a day"
                return new PublicationFrequency(PublicationFrequency.Type.Daily,
                                                amount, isFractional);
            }
            case "Q23387": {
                // Week
                return new PublicationFrequency(PublicationFrequency.Type.Weekly,
                                                amount, isFractional);
            }
            case "Q5151": {
                // Month
                return new PublicationFrequency(PublicationFrequency.Type.Monthly,
                                                amount, isFractional);
            }
            case "Q577": {
                // Year
                return new PublicationFrequency(PublicationFrequency.Type.Yearly,
                                                amount, isFractional);
            }
            case "Q2993680": {
                // Fortnight (Every 2 Weeks)
                return new PublicationFrequency(PublicationFrequency.Type.Weekly,
                                                amount * 2, false);
            }
            case "Q1643308": {
                // Quarter (Every 3 Months)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly,
                                                amount * 3, false);
            }
            case "Q3955006": {
                // Semester / Half-year (Every 6 Months)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly,
                                                amount * 6, false);
            }
            default: {
                return new PublicationFrequency(PublicationFrequency.Type.Unknown,
                                                0, false);
            }
        }
    }

    @Nullable
    private String getQ(@Nullable final String url) {
        if (url == null || !url.contains("/Q")) {
            return null;
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
