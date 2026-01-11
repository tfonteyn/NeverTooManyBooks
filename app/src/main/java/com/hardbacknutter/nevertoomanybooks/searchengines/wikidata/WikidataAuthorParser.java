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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Other Wikidata claims we could add later.
 * <ul>
 *     <li>P19  place of birth</li>
 *     <li>P20  place of death</li>
 *     <li>P106 occupation</li>
 *     <li>P742: pseudonym</li>
 * </ul>
 *
 * @see <a href="https://www.mediawiki.org/wiki/API:Main_page">mediawiki api</a>
 * @see <a href="https://www.wikidata.org/wiki/Wikidata:Database_reports/List_of_properties/all">
 *         claims</a>
 */
class WikidataAuthorParser {

    private static final String CHARSET = "UTF-8";

    private static final String P_IMAGE = "P18";
    private static final String P_BIRTH_DATE = "P569";
    private static final String P_DEATH_DATE = "P570";

    /**
     * Get size information for the image before download.
     * <p>
     * Param 1: filename.
     */
    private static final String IMAGE_INFO =
            "https://commons.wikimedia.org/w/api.php"
            + "?action=query"
            + "&prop=imageinfo"
            + "&iiprop=url|size"
            + "&format=json"
            + "&titles=File:%1$s";

    /**
     * Plucked out of thin air. Any size smaller, get original image, otherwise thumbnail image.
     */
    private static final int IMAGE_SIZE_THRESHOLD = 5_000_000;
    /** The default width we request for thumbnails. Arbitrary value. **/
    private static final int THUMB_WIDTH = 200;
    /**
     * Download the thumb sized image.
     * Param 1: filename.
     * Param 2: width
     */
    private static final String IMAGE_THUMB = "https://commons.wikimedia.org/w/thumb.php"
                                              + "?f=%1$s"
                                              + "&w=%2$d";

    @NonNull
    private final Locale locale;
    @NonNull
    private final WikidataSearchEngine searchEngine;
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();
    private final IdentifierDao identifierDao;

    WikidataAuthorParser(@NonNull final Context context,
                         @NonNull final WikidataSearchEngine searchEngine) {

        this.searchEngine = searchEngine;
        this.locale = searchEngine.getLocale(context);

        identifierDao = ServiceLocator.getInstance().getIdentifierDao();
    }

    @Nullable
    public Author parse(@NonNull final Context context,
                        @NonNull final String langCode,
                        @NonNull final JSONObject document,
                        @NonNull final String sid)
            throws JSONException {

        final JSONObject authorData = document.getJSONObject("entities")
                                              .getJSONObject(sid);
        // to get the name, we should use claims "P734" + "P735" which are entity-data
        // and require an extra call for each. Instead, we'll use the "labels"
        // and rely on our Author name splitting as usual.
        final JSONObject labels = authorData.getJSONObject("labels");
        JSONObject label = labels.optJSONObject(langCode);
        if (label == null) {
            // fallback to English
            label = labels.optJSONObject("en");
            // paranoia
            if (label == null) {
                return null;
            }
        }

        final Author author = Author.from(label.getString("value"));
        author.setIdentifierValue(Identifier.SID_WIKIDATA, sid);

        final JSONObject claims = authorData.getJSONObject("claims");

        parseDate(claims, P_BIRTH_DATE).map(PartialDate::getIsoString)
                                       .ifPresent(author::setBirthDate);
        parseDate(claims, P_DEATH_DATE).map(PartialDate::getIsoString)
                                       .ifPresent(author::setDeathDate);

        identifierDao.getAll()
                     .stream()
                     .filter(identifier -> identifier.getWikidataClaimAuthorId().isPresent())
                     .map(identifier -> parseSid(claims,
                                                 identifier.getWikidataClaimAuthorId().get(),
                                                 identifier.getKey()))
                     .flatMap(Optional::stream)
                     .forEach(v -> author.setIdentifierValue(v.getKey(), v.getSid()));

        parseImage(context, claims).ifPresent(url -> {
            try {
                searchEngine.saveImage(context, url, null, sid, 0, null)
                            .ifPresent(fileSpec -> {
                                author.setTmpPictureFileSpec(fileSpec);
                                final Author realAuthor = author.getRealAuthor();
                                if (realAuthor != null) {
                                    realAuthor.setImageUuid(fileSpec);
                                }
                            });
            } catch (@NonNull final StorageException ignore) {
                // ignore
            }
        });

        return author;
    }

    @NonNull
    private Optional<PartialDate> parseDate(@NonNull final JSONObject claims,
                                            @NonNull final String pkey)
            throws JSONException {

        final JSONArray p = claims.optJSONArray(pkey);
        if (p == null || p.isEmpty()) {
            return Optional.empty();
        }
        final String time = p.getJSONObject(0)
                             .getJSONObject("mainsnak")
                             .getJSONObject("datavalue")
                             .getJSONObject("value")
                             .getString("time");

        return partialDateParser.parse(time);
    }

    @NonNull
    private Optional<Identifier.Value> parseSid(@NonNull final JSONObject claims,
                                                @NonNull final String pkey,
                                                @NonNull final String sidKey)
            throws JSONException {

        final JSONArray p = claims.optJSONArray(pkey);
        if (p == null || p.isEmpty()) {
            return Optional.empty();
        }

        String sid = p.getJSONObject(0)
                      .getJSONObject("mainsnak")
                      .getJSONObject("datavalue")
                      .getString("value");

        if (Identifier.SID_BNF.equals(sidKey)) {
            // wiki stores it without "cb" prefix, we need it with.
            sid = "cb" + sid;
        }
        return Optional.of(new Identifier.Value(sidKey, sid));
    }

    @NonNull
    private Optional<String> parseImage(@NonNull final Context context,
                                        @NonNull final JSONObject claims)
            throws JSONException {

        final JSONArray p = claims.optJSONArray(P_IMAGE);
        if (p == null || p.isEmpty()) {
            return Optional.empty();
        }
        try {
            final String filename = p.getJSONObject(0)
                                     .getJSONObject("mainsnak")
                                     .getJSONObject("datavalue")
                                     .getString("value");
            // they use... sit down... SPACES in the filenames....
            final String encodeFilename = URLEncoder.encode(filename, CHARSET);

            final String url = String.format(IMAGE_INFO, encodeFilename);
            final FutureHttp<String> httpGet = searchEngine.createGetDocumentRequest(context);
            final String response = httpGet.getAsString(url, (con, s) -> s);
            final JSONObject document = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                final JSONObject pages = document.getJSONObject("query")
                                                 .getJSONObject("pages");
                final Iterator<String> keys = pages.keys();
                if (keys.hasNext()) {
                    final String key = keys.next();
                    final JSONObject imageInfo = pages.getJSONObject(key)
                                                      .getJSONArray("imageinfo")
                                                      .getJSONObject(0);

                    final int bytes = imageInfo.getInt("size");
                    if (bytes < IMAGE_SIZE_THRESHOLD) {
                        return Optional.of(imageInfo.getString("url"));
                    } else {
                        return Optional.of(String.format(locale, IMAGE_THUMB,
                                                         encodeFilename, THUMB_WIDTH));
                    }
                }
            }
        } catch (@NonNull final StorageException | IOException ignore) {
            // ignore
        }

        return Optional.empty();
    }
}
