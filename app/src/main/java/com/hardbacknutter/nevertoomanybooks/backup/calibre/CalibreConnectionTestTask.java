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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

public class CalibreConnectionTestTask
        extends VMTask<Boolean> {

    private static final String TAG = "CalibreConnectionTest";

    public void start() {
        execute(R.id.TASK_ID_VALIDATE_CONNECTION);
    }

    /**
     * Make a test connection.
     *
     * @param context a localised application context
     *
     * @return {@code true} on success
     *
     * @throws IOException on failures
     */
    @Nullable
    @Override
    protected Boolean doWork(@NonNull final Context context)
            throws GeneralParsingException,
                   IOException,
                   CertificateException, KeyManagementException {

        try {
            final CalibreContentServer server = new CalibreContentServer(context);
            server.loadLibraries();
        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e);
            throw e;
        }
        return true;
    }
}
