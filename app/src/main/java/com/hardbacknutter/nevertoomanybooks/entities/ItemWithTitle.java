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
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public interface ItemWithTitle {

    /**
     * Cache for the pv_reformat_titles_prefixes strings.
     * <p>
     * Design-wise this is not very clean. We're in an interface after all.
     * Setting the VisibleForTesting is also bogus, but it's better then leaving it completely
     * public. At least this way Studio will warn us about illegal usage.
     * Suggestions welcome.
     */
    @VisibleForTesting
    Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();

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
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param userContext    Current context, should be an actual user context,
     *                       and not the ApplicationContext.
     * @param fallbackLocale Locale to use if the item has none set
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context userContext,
                                   @NonNull final Locale fallbackLocale) {

        Locale locale = getLocale(fallbackLocale);

        // Getting the string is slow, so we cache it for every Locale.
        String orderPatter = LOCALE_PREFIX_MAP.get(locale);
        if (orderPatter == null) {
            // the resources bundle in the language that the book (item) is written in.
            Resources localeResources = App.getLocalizedResources(userContext, locale);
            orderPatter = localeResources.getString(R.string.pv_reformat_titles_prefixes);
            LOCALE_PREFIX_MAP.put(locale, orderPatter);
        }

        StringBuilder newTitle = new StringBuilder();
        String[] titleWords = getTitle().split(" ");
        if (titleWords[0].matches(orderPatter)) {
            for (int i = 1; i < titleWords.length; i++) {
                if (i != 1) {
                    newTitle.append(' ');
                }
                newTitle.append(titleWords[i]);
            }
            newTitle.append(", ").append(titleWords[0]);
            return newTitle.toString();
        }
        return getTitle();
    }

    /**
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param userContext    Current context, should be an actual user context,
     *                       and not the ApplicationContext.
     * @param isInsert       should be {@code true} for an insert, {@code false} for an update
     * @param fallbackLocale Locale to use if the item has none set
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context userContext,
                                   final boolean isInsert,
                                   @NonNull final Locale fallbackLocale) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(userContext);
        boolean whenInserting = prefs.getBoolean(Prefs.pk_reformat_titles_on_insert, true);
        boolean whenUpdating = prefs.getBoolean(Prefs.pk_reformat_titles_on_update, true);

        if ((isInsert && whenInserting) || (!isInsert && whenUpdating)) {
            return preprocessTitle(userContext, fallbackLocale);
        } else {
            return getTitle();
        }
    }
}
