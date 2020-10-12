/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Utility class encapsulating the Preferred Styles Menu logic. */
public final class PreferredStylesMenu {

    private PreferredStylesMenu() {
    }

    /**
     * Get the UUIDs of the preferred styles from user preferences.
     *
     * @param context Current context
     *
     * @return set of UUIDs
     */
    @NonNull
    private static Set<String> get(@NonNull final Context context) {
        final Set<String> uuidSet = new LinkedHashSet<>();
        final String itemsStr = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(BooklistStyle.PREF_BL_PREFERRED_STYLES, null);

        if (itemsStr != null && !itemsStr.isEmpty()) {
            final String[] entries = itemsStr.split(",");
            for (final String entry : entries) {
                if (entry != null && !entry.isEmpty()) {
                    uuidSet.add(entry);
                }
            }
        }
        return uuidSet;
    }

    /**
     * Internal single-point of writing the preferred styles menu order.
     *
     * @param context Current context
     * @param uuidSet a set of style UUIDs
     */
    private static void set(@NonNull final Context context,
                            @NonNull final Collection<String> uuidSet) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                    TextUtils.join(",", uuidSet))
                         .apply();
    }

    /**
     * Save the preferred style menu list.
     * <p>
     * This list contains the ID's for user-defined *AND* system-styles.
     *
     * @param context Current context
     * @param styles  full list of preferred styles to save 'in order'
     */
    public static void save(@NonNull final Context context,
                            @NonNull final Collection<BooklistStyle> styles) {

        final Collection<String> list = styles
                .stream()
                .filter(style -> style.isPreferred(context))
                .map(BooklistStyle::getUuid)
                .collect(Collectors.toList());

        set(context, list);
    }

    /**
     * Add a style (its uuid) to the menu list of preferred styles.
     *
     * @param context Current context
     * @param style   to add.
     */
    public static void add(@NonNull final Context context,
                           @NonNull final BooklistStyle style) {
        final Set<String> list = get(context);
        list.add(style.getUuid());
        set(context, list);
    }

    /**
     * Filter the specified styles so it contains only the preferred styles.
     * If none were preferred, returns the incoming list.
     *
     * @param context   Current context
     * @param allStyles a list of styles
     *
     * @return ordered list.
     */
    @NonNull
    static Map<String, BooklistStyle> filterPreferredStyles(
            @NonNull final Context context,
            @NonNull final Map<String, BooklistStyle> allStyles) {

        final Map<String, BooklistStyle> resultingStyles = new LinkedHashMap<>();

        // first check the saved and ordered list
        for (final String uuid : get(context)) {
            final BooklistStyle style = allStyles.get(uuid);
            if (style != null) {
                // catch mismatches in any imported bad-data.
                style.setPreferred(true);
                // and add to results
                resultingStyles.put(uuid, style);
            }
        }
        // now check for styles marked preferred, but not in the menu list,
        // again to catch mismatches in any imported bad-data.
        for (final BooklistStyle style : allStyles.values()) {
            if (style.isPreferred(context) && !resultingStyles.containsKey(style.getUuid())) {
                resultingStyles.put(style.getUuid(), style);
            }
        }

        // Return the ones we found.
        if (!resultingStyles.isEmpty()) {
            return resultingStyles;
        } else {
            // If none found, return what we were given.
            return allStyles;
        }
    }
}
