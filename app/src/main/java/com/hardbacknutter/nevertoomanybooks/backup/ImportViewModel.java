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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultDataModel;

public class ImportViewModel
        extends ViewModel
        implements ActivityResultDataModel {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultData = new Intent();

    @Nullable
    private ImportHelper mImportHelper;

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultData;
    }

    @NonNull
    ImportHelper createImportManager(@NonNull final Uri uri) {
        mImportHelper = new ImportHelper(uri);
        return mImportHelper;
    }

    @NonNull
    ImportHelper getImportHelper() {
        return Objects.requireNonNull(mImportHelper);
    }

    @NonNull
    Intent onImportFinished() {
        Objects.requireNonNull(mImportHelper);
        mResultData.putExtra(ImportResults.BKEY_IMPORT_RESULTS, mImportHelper.getOptions());
        return mResultData;
    }
}
