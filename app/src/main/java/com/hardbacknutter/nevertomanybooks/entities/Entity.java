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

import androidx.annotation.NonNull;

/**
 * An item (entity) in a database table always has an id and some user-friendly label
 * aka 'displayName'.
 */
public interface Entity {

    /**
     * Get the database row id of the entity.
     *
     * @return id
     */
    long getId();

    /**
     * Get the label to use.
     *
     * @return the label.
     */
    String getLabel();

    /**
     * Optional.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    default String getLabel(@NonNull final Context context) {
        return getLabel();
    }

}
