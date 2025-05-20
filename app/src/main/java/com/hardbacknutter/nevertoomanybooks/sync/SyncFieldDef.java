/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync;

import androidx.annotation.NonNull;

/**
 * Quick and dirty value class to define the variables needed to construct a {@link SyncField}.
 */
public class SyncFieldDef {
    @NonNull
    public final SyncField.Type type;
    @NonNull
    public final String fieldKey;
    @NonNull
    public final String enabledKey;

    /**
     * Constructor.
     * Type.OTHER.
     *
     * @param fieldKey to add
     *                 also used as preference key to check user-enabled state
     */
    public SyncFieldDef(@NonNull final String fieldKey) {
        this.type = SyncField.Type.OTHER;
        this.fieldKey = fieldKey;
        this.enabledKey = fieldKey;
    }

    /**
     * Constructor.
     *
     * @param type     of field
     * @param fieldKey to add
     *                 also used as preference key to check user-enabled state
     */
    public SyncFieldDef(@NonNull final SyncField.Type type,
                        @NonNull final String fieldKey) {
        this.type = type;
        this.fieldKey = fieldKey;
        this.enabledKey = fieldKey;
    }

    /**
     * Constructor.
     *
     * @param type       of field
     * @param fieldKey   to add
     * @param enabledKey preference key to check user-enabled state
     */
    public SyncFieldDef(@NonNull final SyncField.Type type,
                        @NonNull final String fieldKey,
                        @NonNull final String enabledKey) {
        this.type = type;
        this.fieldKey = fieldKey;
        this.enabledKey = enabledKey;
    }
}
