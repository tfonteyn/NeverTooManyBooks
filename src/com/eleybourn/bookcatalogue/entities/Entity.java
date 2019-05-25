package com.eleybourn.bookcatalogue.entities;

import android.content.res.Resources;

import androidx.annotation.NonNull;

/**
 * An item (entity) in a database table always has an id and some user-friendly label
 * aka 'displayName'
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
     * @param resources for locale specific strings
     *
     * @return the label to use.
     */
    default String getLabel(@NonNull final Resources resources) {
        return getLabel();
    }
}
