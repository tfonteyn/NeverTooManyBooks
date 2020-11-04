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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;

public class ImportHelperViewModel
        extends ViewModel {

    /** import configuration. */
    private ImportManager mHelper;

    @Nullable
    private ArchiveInfo mInfo;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args)
            throws InvalidArchiveException, IOException {
        mHelper = Objects.requireNonNull(
                args.getParcelable(ImportHelperDialogFragment.BKEY_IMPORT_MANAGER),
                "mHelper");
        mInfo = mHelper.getInfo(context);
    }

    @NonNull
    ImportManager getHelper() {
        return mHelper;
    }

    @Nullable
    ArchiveInfo getInfo() {
        return mInfo;
    }

    /**
     * Get the archive creation date.
     *
     * @param context Current context
     *
     * @return the date, or {@code null} if none present
     */
    @Nullable
    LocalDateTime getArchiveCreationDate(@NonNull final Context context) {
        if (mInfo == null) {
            return null;
        } else {
            return mInfo.getCreationDate(context);
        }
    }

    // TODO: split this up into one check for each entity we could import.
    boolean isBooksOnlyContainer(@NonNull final Context context) {
        final ArchiveContainer container = mHelper.getContainer(context);
        return ArchiveContainer.CsvBooks == container
               || ArchiveContainer.SqLiteDb == container;
    }
}
