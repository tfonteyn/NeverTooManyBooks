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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import androidx.annotation.NonNull;

/**
 * Interface supported by an editor object.
 */
public interface DataEditor<T extends DataManager> {

    /**
     * Save the contents of all Fields to the {@link DataManager}.
     *
     * @param dataManager to save the data to
     */
    void onSaveFields(@NonNull T dataManager);

    /**
     * Check for unfinished user edits.
     * <p>
     * Independent of the data stored in {@link #onSaveFields(DataManager)}, an editor
     * can have fields with data in it which are not directly linked with a {@link DataManager}.
     *
     * @return {@code true} if there are
     */
    default boolean hasUnfinishedEdits() {
        return false;
    }
}
