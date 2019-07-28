package com.eleybourn.bookcatalogue.entities;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * An item (entity) in a database table always has an id and some user-friendly label
 * aka 'displayName'.
 */
public interface Entity {

    /**
     * @return the database row id of the entity.
     */
    long getId();

    /**
     * @return the label to use.
     */
    String getLabel();

    /**
     * Optional.
     *
     * @param context Current context for accessing resources.
     *
     * @return the label to use.
     */
    default String getLabel(@NonNull final Context context) {
        return getLabel();
    }

}
