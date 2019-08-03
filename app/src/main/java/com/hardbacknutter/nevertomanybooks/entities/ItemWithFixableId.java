package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.database.DAO;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An entity (item) in the database which is capable of finding itself in the database
 * without using its id.
 */
public interface ItemWithFixableId {

    /**
     * Passed a list of Objects, remove duplicates based on the
     * {@link ItemWithFixableId#stringEncoded} result.
     * (case insensitive + trimmed)
     * <p>
     * ENHANCE: Add {@link Author} aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     * ENHANCE: Add {@link Series} aliases table to allow further pruning
     * (e.g. 'Amber Series' <==> 'Amber').
     *
     * @param db   Database connection to lookup ID's
     * @param list List to clean up
     * @param <T>  ItemWithFixableId object
     *
     * @return {@code true} if the list was modified.
     */
    static <T extends ItemWithFixableId> boolean pruneList(@NonNull final Context userContext,
                                                           @NonNull final DAO db,
                                                           @NonNull final List<T> list) {
        // weeding out duplicate ID's
        Set<Long> ids = new HashSet<>();
        // weeding out duplicate uniqueNames.
        Set<String> names = new HashSet<>();
        // will be set to true if we modify the list.
        boolean modified = false;

        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            T item = it.next();
            // try to find the item.
            long itemId = item.fixId(userContext, db);

            String uniqueName = item.stringEncoded().trim().toUpperCase();

            // Series special case: same name + different number.
            // This means different series positions will have the same id+name but will have
            // different numbers; so ItemWithFixableId 'isUniqueById()' returns 'false'.
            if (ids.contains(itemId) && !item.isUniqueById() && !names.contains(uniqueName)) {
                // unique item in the list: id+name matched, but other fields might be different.
                ids.add(itemId);
                names.add(uniqueName);

            } else if (names.contains(uniqueName) || (itemId != 0 && ids.contains(itemId))) {
                it.remove();
                modified = true;

            } else {
                // unique item in the list.
                ids.add(itemId);
                names.add(uniqueName);
            }
        }

        return modified;
    }

    /**
     * Tries to find the item in the database using all its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param db the database
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull DAO db);

    /**
     * Tries to find the item in the database using all its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param userContext Current context
     * @param db          the database
     *
     * @return the item id (also set on the item).
     */
    default long fixId(@NonNull final Context userContext,
                       @NonNull final DAO db) {
        return fixId(db);
    }

    /**
     * Same as {@link #fixId(DAO)} but for items that need a Locale to fix themselves.
     *
     * @param userContext Current context
     * @param db          the database
     * @param locale      Locale for any specific overrides
     *
     * @return the item id (also set on the item).
     */
    default long fixId(@NonNull final Context userContext,
                       @NonNull final DAO db,
                       @NonNull final Locale locale) {
        return fixId(userContext, db);
    }

    /**
     * @return a unique name for this object, representing all it's data (except id).
     */
    String stringEncoded();

    /**
     * @return {@code true} if comparing ONLY by id ensures uniqueness.
     */
    boolean isUniqueById();

}
