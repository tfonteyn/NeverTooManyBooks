/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public interface EntityDao<T extends Entity> {

    /**
     * Get the {@link T} based on the given id.
     *
     * @param id of {@link T} to find
     *
     * @return the {@link T}
     */
    @NonNull
    Optional<T> getById(@IntRange(from = 1) long id);
}
