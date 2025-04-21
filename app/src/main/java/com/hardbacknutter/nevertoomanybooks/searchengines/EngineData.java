/*
 * @Copyright 2018-2025 HardBackNutter
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
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

@SuppressWarnings("CheckStyle")
public class EngineData {

    /** The preference key / generic string identifier for this engine. */
    @NonNull
    final String key;

    /** The user displayable name for this engine. */
    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    final int labelResId;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    final List<Integer> infoResIdList;

    /** Default url. */
    @NonNull
    final String defaultSearchUrl;

    @NonNull
    final Locale defaultLocale;

    private boolean multipleCoverSizes;

    @Nullable
    private String identifierKey;

    /**
     * Constructor.
     *
     * @param key              The preference key / generic string identifier for this engine.
     * @param labelResId       The user displayable name for this engine.
     * @param infoResIdList    A list of informational string resources about this site
     * @param defaultSearchUrl for the site
     * @param defaultLocale    for the site
     */
    public EngineData(@NonNull final String key,
                      @StringRes final int labelResId,
                      @NonNull final List<Integer> infoResIdList,
                      @NonNull final String defaultSearchUrl,
                      @NonNull final Locale defaultLocale) {
        this.key = key;
        this.labelResId = labelResId;
        this.infoResIdList = infoResIdList;
        this.defaultSearchUrl = defaultSearchUrl;
        this.defaultLocale = defaultLocale;
    }

    @Nullable
    String getIdentifierKey() {
        return identifierKey;
    }

    /**
     * Set the {@link Identifier} for the website specific identifier for a book.
     *
     * @param identifierKey key
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EngineData setIdentifierKey(@NonNull final String identifierKey) {
        this.identifierKey = identifierKey;
        return this;
    }

    boolean hasMultipleCoverSizes() {
        return multipleCoverSizes;
    }

    @NonNull
    public EngineData setMultipleCoverSizes(final boolean supports) {
        this.multipleCoverSizes = supports;
        return this;
    }
}
