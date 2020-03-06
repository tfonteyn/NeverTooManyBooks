/*
 * @Copyright 2020 HardBackNutter
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
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DAO;

/**
 * An entity (item) in the database which is capable of finding itself in the database
 * without using its id.
 */
public interface ItemWithFixableId
        extends Parcelable {

    String TAG = "ItemWithFixableId";

    /**
     * Passed a list of Objects, remove duplicates.
     * <p>
     * ENHANCE: Add {@link Author} aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     * ENHANCE: Add {@link Series} aliases table to allow further pruning
     * (e.g. 'Amber Series' <==> 'Amber').
     *
     * @param <T>            ItemWithFixableId object
     * @param list           List to clean up
     * @param context        Current context
     * @param db             Database Access
     * @param fallbackLocale Locale to use if the item has none set.
     * @param isBatchMode    set to {@code true} to force the use of the fallbackLocale,
     *                       instead of taking a round trip to the database to try and guess
     *                       the locale. Should be used for example during an import.
     *
     * @return {@code true} if the list was modified.
     */
    static <T extends ItemWithFixableId> boolean pruneList(@NonNull final List<T> list,
                                                           @NonNull final Context context,
                                                           @NonNull final DAO db,
                                                           @NonNull final Locale fallbackLocale,
                                                           final boolean isBatchMode) {
        // weeding out duplicate ID's
        Collection<Long> ids = new HashSet<>();
        // weeding out duplicate hash codes
        Collection<Integer> hashCodes = new HashSet<>();
        // will be set to true if we modify the list.
        boolean modified = false;

        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            T item = it.next();
            long itemId;

            Locale itemLocale;
            if (isBatchMode) {
                itemLocale = fallbackLocale;
            } else {
                itemLocale = item.getLocale(context, db, fallbackLocale);
            }

            // try to find the item.
            itemId = item.fixId(context, db, itemLocale);

            Integer hashCode = item.hashCode();

            if (ids.contains(itemId) && !item.isUniqueById() && !hashCodes.contains(hashCode)) {
                // The base object(id) is unique, but other "list-significant" fields are not.
                ids.add(itemId);
                hashCodes.add(hashCode);

            } else if (hashCodes.contains(hashCode) || (itemId != 0 && ids.contains(itemId))) {
                // Fully unique item already in the list, so remove.
                it.remove();
                modified = true;

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
     * <p>
     * The default implementation just returns the fallbackLocale itself.
     *
     * @param context        Current context
     * @param db             Database Access
     * @param fallbackLocale Locale to use if the item does not have a Locale of its own.
     *
     * @return the item Locale, or the fallbackLocale.
     */
    @NonNull
    default Locale getLocale(@NonNull final Context context,
                             @NonNull final DAO db,
                             @NonNull final Locale fallbackLocale) {
        return fallbackLocale;
    }

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

    /**
     * Get the flag as used in {@link #pruneList}.
     *
     * @return {@code true} if comparing ONLY by id ensures uniqueness.
     */
    boolean isUniqueById();
}
