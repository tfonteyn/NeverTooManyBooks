/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

public interface ItemWithTitle {

    /**
     * Get the locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     *
     * @return the item Locale
     */
    @NonNull
    Locale getLocale();

    @NonNull
    String getTitle();

    /**
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param userContext Current context, should be an actual user context,
     *                    and not the ApplicationContext.
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context userContext) {
        StringBuilder newTitle = new StringBuilder();
        String[] titleWords = getTitle().split(" ");

        // the resources bundle in the language that the book (item) is written in.
        Resources localeResources = LocaleUtils.getLocalizedResources(userContext, getLocale());
        String orderPatter = localeResources.getString(R.string.pv_reformat_titles_prefixes);

        try {
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
        } catch (@NonNull final RuntimeException ignore) {
            //do nothing. Title stays the same
        }
        return getTitle();
    }

    /**
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param userContext Current context, should be an actual user context,
     *                    and not the ApplicationContext.
     * @param isInsert    should be {@code true} for an insert, {@code false} for an update
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context userContext,
                                   final boolean isInsert) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(userContext);
        boolean whenInserting = prefs.getBoolean(Prefs.pk_reformat_titles_on_insert, true);
        boolean whenUpdating = prefs.getBoolean(Prefs.pk_reformat_titles_on_update, true);

        if ((isInsert && whenInserting) || (!isInsert && whenUpdating)) {
            return preprocessTitle(userContext);
        } else {
            return getTitle();
        }
    }
}
