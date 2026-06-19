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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;

/**
 * A data class with all values to search on.
 * Actual members used will depend on the actual search method, one of:
 * {@link SearchEngine.ByText}, {@link SearchEngine.ByExternalId}, {@link SearchEngine.ByIsbn}.
 * <p>
 * All values are 'raw', i.e. exactly as entered by the user in a form,
 * with {@link #productCode} the parsed value according to {@link #strictIsbn}.
 */
public class BookSearchCriteria {

    private static final String PK_SEARCH_STRICT_ISBN = "search.byIsbn.strict";
    /**
     * Site external id for search.
     *
     * @see SearchEngine.ByExternalId
     */
    @NonNull
    private final Map<EngineId, String> sids = new EnumMap<>(EngineId.class);
    /** Whether of not to fetch thumbnails. */
    @NonNull
    private final boolean[] fetchCovers = new boolean[DBKey.NR_OF_BOOK_COVERS];
    /** Routing purposes. */
    @Nullable
    private ScanMode scanMode;

    /** Raw code text for searches. */
    @NonNull
    private String productCodeStr = "";
    /** The parsed code. */
    @Nullable
    private ProductCode productCode;
    /**
     * {@code true} for strict ISBN parsing,
     * {@code false} for allowing other valid generic codes.
     */
    private boolean strictIsbn;

    @NonNull
    private String title = "";
    @NonNull
    private String author = "";
    @NonNull
    private String series = "";
    @NonNull
    private String seriesNr = "";
    @NonNull
    private String publisher = "";

    /**
     * Constructor.
     */
    public BookSearchCriteria() {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        for (int cIdx = 0; cIdx < fetchCovers.length; cIdx++) {
            fetchCovers[cIdx] = serviceLocator.isFieldEnabled(DBKey.COVER[cIdx]);
        }
        strictIsbn = isStrictIsbnGlobal();
    }

    /**
     * Get the global user-settings strictIsbn flag.
     *
     * @return {@code true} for strict ISBN checking,
     *         {@code false} for allowing other valid generic codes.
     */
    public static boolean isStrictIsbnGlobal() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_SEARCH_STRICT_ISBN, true);
    }

    /**
     * Set the global user-settings strictIsbn flag.
     *
     * @param strictIsbn {@code true} for strict ISBN checking,
     *                   {@code false} for allowing other valid generic codes.
     */
    public static void setStrictIsbnDefault(final boolean strictIsbn) {
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(PK_SEARCH_STRICT_ISBN, strictIsbn)
                      .apply();
    }

    @Nullable
    ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * Flags.
     *
     * @return an array with length {@link DBKey#NR_OF_BOOK_COVERS}.
     */
    @NonNull
    public boolean[] getFetchCovers() {
        return fetchCovers;
    }

    /**
     * Indicate we want images to be downloaded.
     *
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    The length MUST be {@link DBKey#NR_OF_BOOK_COVERS}.
     *
     * @throws IllegalArgumentException (debug) if the array is an incorrect length
     */
    public void setFetchCovers(@NonNull final boolean[] fetchCovers) {
        if (BuildConfig.DEBUG /* always */) {
            if (fetchCovers.length != DBKey.NR_OF_BOOK_COVERS) {
                throw new IllegalArgumentException("fetchCovers must be DBKey.NR_OF_BOOK_COVERS");
            }
        }

        System.arraycopy(fetchCovers, 0, this.fetchCovers, 0, fetchCovers.length);
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull final String title) {
        this.title = title;
    }

    @NonNull
    public String getAuthor() {
        return author;
    }

    public void setAuthor(@NonNull final String author) {
        this.author = author;
    }

    @NonNull
    public String getSeries() {
        return series;
    }

    public void setSeries(@NonNull final String series) {
        this.series = series;
    }

    @NonNull
    public String getSeriesNr() {
        return seriesNr;
    }

    public void setSeriesNr(@NonNull final String seriesNr) {
        this.seriesNr = seriesNr;
    }

    @NonNull
    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(@NonNull final String publisher) {
        this.publisher = publisher;
    }

    /**
     * Get the product-code criteria as a raw string.
     *
     * @return raw product-code text
     */
    @NonNull
    String getRawProductCode() {
        return productCodeStr;
    }

    /**
     * Set the product-code criteria as a raw string.
     *
     * @param text to search for
     */
    public void setRawProductCode(@NonNull final String text) {
        this.productCodeStr = text;
        productCode = null;
    }

    /**
     * Get the {@link ProductCode} criteria.
     *
     * @return code; can be {@code null} if none
     */
    @Nullable
    public ProductCode getProductCode() {
        if (productCodeStr.isEmpty()) {
            return null;
        }

        if (productCode == null) {
            productCode = ISBN.parse(this.productCodeStr, this.strictIsbn);
        }
        return productCode;
    }

    /**
     * Set the {@link ProductCode} criteria.
     *
     * @param productCode     to search for
     * @param scanMode will be returned with the result
     */
    public void setProductCodeFromScan(@NonNull final ProductCode productCode,
                                       @NonNull final ScanMode scanMode) {
        this.productCodeStr = productCode.asText();
        this.productCode = productCode;
        this.scanMode = scanMode;
    }

    boolean hasValidProductCode() {
        final ProductCode tmpProductCode = getProductCode();
        if (tmpProductCode == null) {
            return false;
        }

        // We MUST use the strictIsbn as set on this criteria object,
        // as the code can have come from the scanner and be theoretically less/more strict.
        if (strictIsbn) {
            return tmpProductCode.isIsbn();
        } else {
            return tmpProductCode.getType() != ProductCodeType.Invalid;
        }
    }

    /**
     * Get the strictIsbn flag for this criteria object.
     *
     * @return {@code true} for strict ISBN checking,
     *         {@code false} for allowing other valid generic codes.
     */
    boolean isStrictIsbn() {
        return strictIsbn;
    }

    /**
     * Override the strictIsbn flag for this criteria object.
     *
     * @param strictIsbn {@code true} for strict ISBN checking,
     *                   {@code false} for allowing other valid generic codes.
     */
    public void setStrictIsbn(final boolean strictIsbn) {
        this.strictIsbn = strictIsbn;
        productCode = null;
    }

    /**
     * Check if there is at least one sid.
     *
     * @return flag
     */
    boolean hasSids() {
        return !sids.isEmpty();
    }

    /**
     * Add a sid to the criteria.
     *
     * @param engineId to set the sid for
     * @param sid      to set
     */
    public void addSid(@NonNull final EngineId engineId,
                       @NonNull final String sid) {
        sids.put(engineId, sid);
    }

    /**
     * Get the sid matching the given engine.
     *
     * @param engineId to get a sid for
     *
     * @return sid
     */
    @NonNull
    public Optional<String> getSid(@NonNull final EngineId engineId) {
        final String s = sids.get(engineId);
        if (s != null && !s.isEmpty()) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    /**
     * <strong>Clear</strong>Clear the current list, and set the new given sids.
     *
     * @param sids one or more ID's
     *             The key is the engine id,
     *             The value is the SID for that engine
     */
    public void setSids(@Nullable final Map<EngineId, String> sids) {
        this.sids.clear();
        if (sids != null) {
            this.sids.putAll(sids);
        }
    }

    /**
     * Reset all criteria; empty strings, empty list.
     * The 'strictIsbn' flag is initialised from the global user-settings.
     */
    public void reset() {
        title = "";
        author = "";
        series = "";
        seriesNr = "";
        publisher = "";
        productCodeStr = "";
        strictIsbn = isStrictIsbnGlobal();
        sids.clear();
    }

    /**
     * Check if at least one value is set.
     *
     * @return flag
     */
    public boolean isEmpty() {
        return title.isEmpty()
               && author.isEmpty()
               && series.isEmpty()
               && seriesNr.isEmpty()
               && publisher.isEmpty()
               && productCodeStr.isEmpty()
               && sids.isEmpty();
    }

    /**
     * Simple concatenation of the simple text values into a single String.
     *
     * @param delimiter to use
     *
     * @return a StringJoiner ready to concat more options to
     *
     * @see SearchEngine.ByText
     */
    @NonNull
    public StringJoiner concatTextCriteria(@NonNull final String delimiter) {
        final StringJoiner words = new StringJoiner(delimiter);

        if (!title.isEmpty()) {
            words.add(title);
        }
        if (!author.isEmpty()) {
            words.add(author);
        }
        if (!series.isEmpty()) {
            words.add(series);
        }
        if (!seriesNr.isEmpty()) {
            words.add(seriesNr);
        }
        if (!publisher.isEmpty()) {
            words.add(publisher);
        }

        return words;
    }

    @Override
    @NonNull
    public String toString() {
        return "BookSearchCriteria{"
               + "title=`" + title + '`'
               + ", author=`" + author + '`'
               + ", series=`" + series + '`'
               + ", seriesNr=`" + seriesNr + '`'
               + ", publisher=`" + publisher + '`'
               + ", scanMode=" + scanMode
               + ", productCodeStr=`" + productCodeStr + '`'
               + ", productCode=" + productCode
               + ", strictIsbn=" + strictIsbn
               + ", sidSearchText=`" + sids + '`'
               + ", fetchCovers=" + Arrays.toString(fetchCovers)
               + '}';
    }
}
