/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Interface implemented for custom data access
 *
 * @author pjw
 */
public interface DataAccessor {
    /**
     * Get the specified {@link Datum} from the passed {@link DataManager} or {@link Bundle}
     */
    @NonNull
    Object get(final @NonNull DataManager data,
               final @NonNull Datum datum,
               final @NonNull Bundle rawData);

    /**
     * Set the specified {@link Datum} in the passed Bundle
     */
    void set(final @NonNull DataManager data,
             final @NonNull Datum datum,
             final @NonNull Bundle rawData,
             final @NonNull Object value);

    /**
     *  Check if the specified {@link Datum} is present in the passed Bundle
     */
    boolean isPresent(final @NonNull DataManager data,
                      final @NonNull Datum datum,
                      final @NonNull Bundle rawData);
}