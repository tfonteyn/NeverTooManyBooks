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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public interface ItemWithTitle {

    /**
     * Get the Locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     *
     * @param fallbackLocale Locale to use if the item does not have a Locale of its own.
     *
     * @return the item Locale, or the fallbackLocale.
     */
    @NonNull
    Locale getLocale(@NonNull Locale fallbackLocale);

    @NonNull
    String getTitle();

    /**
     * Move "The, A, An" etc... to the end of the string for use as the OrderBy column.
     *
     * @param userContext    Current context, should be an actual user context,
     *                       and not the ApplicationContext.
     * @param fallbackLocale Locale to use if the item has none set
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context userContext,
                                   @NonNull final Locale fallbackLocale) {

        if (PreferenceManager.getDefaultSharedPreferences(userContext)
                             .getBoolean(Prefs.pk_reorder_titles_for_sorting, true)) {

            // will try locales in order as passed here.
            return LocaleUtils.reorderTitle(userContext, getTitle(),
                                            getLocale(fallbackLocale),
                                            App.getSystemLocale(),
                                            Locale.ENGLISH);
        }
        return getTitle();
    }
}
