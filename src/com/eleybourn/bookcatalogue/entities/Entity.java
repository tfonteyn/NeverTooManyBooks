package com.eleybourn.bookcatalogue.entities;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * An item (entity) in a database table always has an id and some user-friendly label
 * aka 'displayName'
 *
 * The ID is mandatory, the label defaults to toString (which should not be used!!!! ahem)
 */
public interface Entity {

    /**
     * @return the database row id of the entity.
     */
    long getId();

    /**
     * @return the label to use.
     */
    default String getLabel() {
        return toString();
    }

    /**
     * @param context caller context
     *
     * @return the label to use.
     */
    default String getLabel(@NonNull final Context context) {
        return getLabel();
    }
}
