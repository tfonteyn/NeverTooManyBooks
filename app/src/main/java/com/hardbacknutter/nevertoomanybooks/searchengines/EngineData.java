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
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Locale;

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
    final String defaultUrl;

    @NonNull
    final Locale defaultLocale;

    /**
     * Constructor.
     *
     * @param key           The preference key / generic string identifier for this engine.
     * @param labelResId    The user displayable name for this engine.
     * @param infoResIdList A list of informational string resources about this site
     * @param defaultUrl    for the site
     * @param defaultLocale for the site
     */
    public EngineData(@NonNull final String key,
                      @StringRes final int labelResId,
                      @NonNull final List<Integer> infoResIdList,
                      @NonNull final String defaultUrl,
                      @NonNull final Locale defaultLocale) {
        this.key = key;
        this.labelResId = labelResId;
        this.infoResIdList = infoResIdList;
        this.defaultUrl = defaultUrl;
        this.defaultLocale = defaultLocale;
    }
}
