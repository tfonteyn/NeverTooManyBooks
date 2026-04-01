/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.prefslib;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@FunctionalInterface
public interface OnSettingChangeListener {

    /**
     * Callback when there is a new value for a setting.
     *
     * @param setting  which was selected
     * @param newValue the new value
     *
     * @return {@code true} if it is allowed to store the new value
     *         {@code false} if the change should be ignored
     */
    boolean onChange(@NonNull Setting setting,
                     @Nullable Object newValue);
}
