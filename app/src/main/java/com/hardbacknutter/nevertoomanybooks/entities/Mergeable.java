/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

interface Mergeable {

    long getId();

    void setId(long id);

    /**
     * Diacritic neutral version of {@link  #hashCode()} <strong>without the id</strong>.
     *
     * @return hashcode
     */
    int asciiHashCodeNoId();

    default boolean merge(@NonNull final Mergeable mergeable) {
        // if this object has no id, and the incoming has an id, then we copy the id.
        if (getId() == 0 && mergeable.getId() > 0) {
            setId(mergeable.getId());
        }
        return true;
    }
}
