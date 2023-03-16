/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

public class ConnectionValidatorTask
        extends MTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "ConnectionValidatorTask";

    @StringRes
    private final int siteResId;

    @Nullable
    private ConnectionValidator server;

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    ConnectionValidatorTask(@StringRes final int siteResId) {
        super(R.id.TASK_ID_VALIDATE_CONNECTION, TAG + ":" + siteResId);

        this.siteResId = siteResId;
    }

    /**
     * Start this task, connecting to the remote site.
     */
    public void connect() {
        execute();
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (server != null) {
                server.cancel();
            }
        }
    }

    /**
     * Make a test connection.
     *
     * @return {@code true} on success
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws IOException          on generic/other IO failures
     */
    @Nullable
    @Override
    protected Boolean doWork()
            throws IOException,
                   StorageException,
                   CertificateException,
                   CredentialsException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


        server = ConnectionValidatorFactory.create(context, siteResId);
        return server.validateConnection();
    }
}
