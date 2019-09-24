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
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * An entity (item) in the database which is capable of finding itself in the database
 * without using its id.
 */
public interface ItemWithFixableId {

    /**
     * Passed a list of Objects, remove duplicates.
     * <p>
     * ENHANCE: Add {@link Author} aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     * ENHANCE: Add {@link Series} aliases table to allow further pruning
     * (e.g. 'Amber Series' <==> 'Amber').
     *
     * <strong>Note:</strong> if the db is null, then context and fallbackLocale are ignored.
     * If the db is non-null, then context and fallbackLocale must be valid.
     *
     * @param <T>            ItemWithFixableId object
     * @param list           List to clean up
     * @param context        Current context
     * @param db             Database Access; can be {@code null} in which case we use the id
     *                       from the item directly. If set, then we call {@link #fixId} first.
     * @param fallbackLocale Locale to use if the item has none set.
     *
     * @return {@code true} if the list was modified.
     */
    static <T extends ItemWithFixableId> boolean pruneList(@NonNull final List<T> list,
                                                           @Nullable final Context context,
                                                           @Nullable final DAO db,
                                                           @Nullable final Locale fallbackLocale) {
        // weeding out duplicate ID's
        Set<Long> ids = new HashSet<>();
        // weeding out duplicate hash codes
        Set<Integer> hashCodes = new HashSet<>();
        // will be set to true if we modify the list.
        boolean modified = false;

        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            T item = it.next();
            long itemId;
            // try to find the item.
            if (db != null) {
                //noinspection ConstantConditions
                itemId = item.fixId(context, db, item.getLocale(fallbackLocale));
            } else {
                itemId = item.getId();
            }

            Integer hashCode = item.hashCode();
            if (ids.contains(itemId) && !item.isUniqueById() && !hashCodes.contains(hashCode)) {
                // The base object(id) is unique, but other "list-significant" fields are different.
                ids.add(itemId);
                hashCodes.add(hashCode);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.PRUNE_LIST) {
                    Logger.debug(ItemWithFixableId.class, "pruneList",
                                 "if-1", "item=" + item);
                }

            } else if (hashCodes.contains(hashCode) || (itemId != 0 && ids.contains(itemId))) {
                // Fully unique item already in the list, so remove.
                it.remove();
                modified = true;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.PRUNE_LIST) {
                    Logger.debug(ItemWithFixableId.class, "pruneList",
                                 "if-2", "item=" + item);
                }

            } else {
                // Fully unique item and new to the list, add it.
                ids.add(itemId);
                hashCodes.add(hashCode);
            }
        }

        return modified;
    }

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

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param context Current context
     * @param db      Database Access
     * @param locale  Locale to use
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull Context context,
               @NonNull DAO db,
               @NonNull Locale locale);

    long getId();

    /**
     * Get the flag as used in {@link #pruneList}.
     *
     * @return {@code true} if comparing ONLY by id ensures uniqueness.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isUniqueById();
}
