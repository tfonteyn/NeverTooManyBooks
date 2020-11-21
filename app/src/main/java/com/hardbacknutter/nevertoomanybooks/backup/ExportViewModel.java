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
package com.hardbacknutter.nevertoomanybooks.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;

public class ExportViewModel
        extends ViewModel {

    /** The export configuration. */
    @Nullable
    private ExportHelper mExportHelper;

    @NonNull
    ExportHelper getExportHelper() {
        if (mExportHelper == null) {
            mExportHelper = new ExportHelper(ExportHelper.Options.ENTITIES);
        }
        return mExportHelper;
    }
}
