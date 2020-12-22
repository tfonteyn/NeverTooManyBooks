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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultIntent;

public class ImportViewModel
        extends ViewModel
        implements ResultIntent {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    /** The import configuration. */
    @Nullable
    private ImportHelper mImportHelper;

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return mImportHelper != null;
    }

    @NonNull
    ImportHelper createImportHelper(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws IOException, InvalidArchiveException {
        mImportHelper = new ImportHelper(context, uri);
        return mImportHelper;
    }

    @NonNull
    ImportHelper getImportHelper() {
        return Objects.requireNonNull(mImportHelper);
    }

    @NonNull
    Intent onImportFinished(@NonNull final ImportResults result) {
        mResultIntent.putExtra(ImportResults.BKEY_IMPORT_RESULTS, result);
        return mResultIntent;
    }
}
