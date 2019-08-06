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
package com.hardbacknutter.nevertomanybooks.utils;

import androidx.core.content.FileProvider;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * This does not really need to be a class as after all we only really want the AUTHORITY string.
 * But let's keep an open mind to future enhancements (if any).
 */
public class GenericFileProvider
        extends FileProvider {

    /**
     * Matches the Manifest entry.
     * <p>
     * android:authorities="${packageName}.GenericFileProvider"
     */
    public static final String AUTHORITY =
            App.getAppContext().getPackageName() + ".GenericFileProvider";
}
