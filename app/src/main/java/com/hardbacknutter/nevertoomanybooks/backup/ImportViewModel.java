/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultIntent;

public class ImportViewModel
        extends ViewModel
        implements ResultIntent {

    private static final String TAG = "ImportViewModel";
    public static final String BKEY_URL = TAG + ":url";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    /** The import configuration. */
    @Nullable
    private ImportHelper mImportHelper;

    private boolean mInitWasCalled;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (!mInitWasCalled) {
            mInitWasCalled = true;
            if (args != null) {
                final String url = args.getString(BKEY_URL);
                if (url != null) {
                    mImportHelper = ImportHelper.withRemoteServer(Uri.parse(url),
                                                                  ArchiveEncoding.CalibreCS);
                }
            }
        }
    }

    @NonNull
    ImportHelper createImportHelper(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws InvalidArchiveException, FileNotFoundException {

        mImportHelper = ImportHelper.withFile(context, uri);
        return mImportHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return mImportHelper != null;
    }

    @NonNull
    public ImportHelper getImportHelper() {
        return Objects.requireNonNull(mImportHelper);
    }

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @NonNull
    Intent onImportFinished(@NonNull final ImportResults result) {
        mResultIntent.putExtra(ImportResults.BKEY_IMPORT_RESULTS, result);
        return mResultIntent;
    }
}
