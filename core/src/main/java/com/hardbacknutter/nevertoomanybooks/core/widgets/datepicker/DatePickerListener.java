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
package com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker;

import androidx.annotation.NonNull;

@FunctionalInterface
public interface DatePickerListener {

    long NO_SELECTION = -1L;

    /**
     * Callback handler.
     * <p>
     * The resulting date can be reconstructed with for example
     *
     * <pre>
     *     {@code
     *          Instant.ofEpochMilli(selections[i])
     *                 .atZone(ZoneId.systemDefault())
     *                 .format(DateTimeFormatter.ISO_LOCAL_DATE)
     *     }
     * </pre>
     * Instant.ofEpochMilli(selections[0])
     *
     * @param fieldIds   the field(s) this dialog was bound to
     * @param selections the selected date(s)
     */
    void onResult(@NonNull int[] fieldIds,
                  @NonNull long[] selections);
}
