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
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DAO;

/**
 * An entity (item) in the database which is capable of finding itself in the database
 * without using its id.
 * <p>
 * TODO: it seems the "isUniqueById" and the "uniqueHashCode" (formerly a unique 'name' was used)
 * is overly complicated.
 */
public interface ItemWithFixableId {

    /**
     * Convenience method for {@link #pruneList(DAO, List, Context, Locale)}.
     * Should be used when the items in the list don't use a Locale.
     *
     * @param db   Database Access
     * @param list List to clean up
     * @param <T>  ItemWithFixableId object
     *
     * @return {@code true} if the list was modified.
     */
    static <T extends ItemWithFixableId> boolean pruneList(@NonNull final DAO db,
                                                           @NonNull final List<T> list) {
        return pruneList(db, list, null, null);
    }

    /**
     * Passed a list of Objects, remove duplicates based on the
     * {@link ItemWithFixableId#uniqueHashCode()} result.
     * <p>
     * ENHANCE: Add {@link Author} aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     * ENHANCE: Add {@link Series} aliases table to allow further pruning
     * (e.g. 'Amber Series' <==> 'Amber').
     *
     * <b>Note:</b> the context and the fallbackLocale must both be null, or both be non-null.
     *
     * @param db             Database Access
     * @param list           List to clean up
     * @param context        Current context, can be set to {@code null}
     *                       if <strong>the item does not support locales</strong>
     * @param fallbackLocale Locale to use if the item has none set, can be set to {@code null}
     *                       if <strong>the item does not support locales</strong>
     * @param <T>            ItemWithFixableId object
     *
     * @return {@code true} if the list was modified.
     */
    static <T extends ItemWithFixableId> boolean pruneList(@NonNull final DAO db,
                                                           @NonNull final List<T> list,
                                                           @Nullable final Context context,
                                                           @Nullable final Locale fallbackLocale) {
        // weeding out duplicate ID's
        Set<Long> ids = new HashSet<>();
        // weeding out duplicate unique hash codes.
        Set<Integer> hashCodes = new HashSet<>();
        // will be set to true if we modify the list.
        boolean modified = false;

        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            T item = it.next();

            // try to find the item.
            long itemId;
            if (fallbackLocale != null) {
                Objects.requireNonNull(context);
                itemId = item.fixId(db, context, item.getLocale(fallbackLocale));
            } else {
                itemId = item.fixId(db);
            }

            Integer uniqueHashCode = item.uniqueHashCode();
            if (ids.contains(itemId)
                && !item.isUniqueById() && !hashCodes.contains(uniqueHashCode)) {
                // The base object IS unique, but other "list-significant" fields are different.
                ids.add(itemId);
                hashCodes.add(uniqueHashCode);

            } else if (hashCodes.contains(uniqueHashCode)
                       || (itemId != 0 && ids.contains(itemId))) {
                // Fully unique item already in the list, so remove.
                it.remove();
                modified = true;

            } else {
                // Fully unique item and new to the list, add it.
                ids.add(itemId);
                hashCodes.add(uniqueHashCode);
            }
        }

        return modified;
    }

    /**
     * Get the locale of this item.
     *
     * @param fallbackLocale Locale to use if the item has none set
     *
     * @return the item Locale
     */
    @NonNull
    Locale getLocale(@NonNull Locale fallbackLocale);

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param db      Database Access
     * @param context Current context
     * @param locale  Locale to use
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull DAO db,
               @NonNull Context context,
               @NonNull Locale locale);

    /**
     * Convenience method for items that don't need the locale.
     * Allows code to omit an unneeded Locale lookup just to call fixId.
     *
     * @param db Database Access
     *
     * @return the item id (also set on the item).
     */
    default long fixId(@NonNull DAO db) {
        throw new IllegalStateException("not implemented");
    }

    /**
     * Get the flag as used in {@link #pruneList}.
     *
     * @return {@code true} if comparing ONLY by id ensures uniqueness.
     */
    boolean isUniqueById();

    /**
     * The {@link Object#hashCode} and {@link Object#equals} our object define,
     * look at the fields which define the object itself (e.g. Author: Family & given name).
     * It does not include user-defined attributes, or linked-table attributes.
     * Examples:
     * - the isComplete flag in Authors and Series.
     * - the number of a book in a Series.
     * <p>
     * In contrast, this method <strong>MUST</strong> return a hash code
     * covering <strong>ALL FIELDS</strong>.
     * <p>
     * By default, this method assumes {@link #isUniqueById()} to return true and hence
     * returns the normal {@link Object#hashCode()}.
     * I.o.w. implement this method when the your object's isUniqueById() returns {@code false}.
     *
     * @return hash
     */
    default int uniqueHashCode() {
        if (!isUniqueById()) {
            throw new IllegalStateException("uniqueHashCode must be implemented");
        }
        return hashCode();
    }
}
