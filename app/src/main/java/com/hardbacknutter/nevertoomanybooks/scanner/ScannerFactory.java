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
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Support for creating scanner objects on the fly without knowing which ones are available.
 */
public interface ScannerFactory {

    /**
     * Check if this scanner is available.
     *
     * @param context Current context
     *
     * @return {@code true} if this scanner is available.
     */
    boolean isAvailable(@NonNull Context context);

    /**
     * Get a new scanner of the related type.
     *
     * @param context Current context
     *
     * @return scanner
     */
    @NonNull
    Scanner getScanner(@NonNull Context context);

    /**
     * Get a resource id that can be used in menus.
     *
     * @return resource id
     */
    @IdRes
    int getMenuId();

    /**
     * Get the market url.
     *
     * @return the market url, or {@code null} if not applicable.
     */
    @Nullable
    default String getMarketUrl() {
        return null;
    }
}
