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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Basically a wrapper for {@link LocaleUtils#reorderTitle}.
 * <p>
 * Encapsulates getting the title and whether to reorder at all.
 */
public interface ItemWithTitle {

    @NonNull
    String getTitle();

    /**
     * Reformat titles for <strong>use as the OrderBy column</strong>.
     *
     * @param userContext Current context, should be an actual user context,
     *                    and not the ApplicationContext.
     * @param titleLocale Locale to use
     *
     * @return reordered title / original title
     */
    default String reorderTitleForSorting(@NonNull final Context userContext,
                                          @NonNull final Locale titleLocale) {

        if (PreferenceManager.getDefaultSharedPreferences(userContext)
                             .getBoolean(Prefs.pk_reformat_titles_sort, true)) {

            return LocaleUtils.reorderTitle(userContext, getTitle(), titleLocale);
        } else {
            return getTitle();
        }
    }
}
