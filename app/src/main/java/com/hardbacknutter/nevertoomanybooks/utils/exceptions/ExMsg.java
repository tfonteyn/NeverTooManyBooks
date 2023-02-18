/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

public final class ExMsg {

    private static final String TAG = "ExMsg";

    private ExMsg() {
    }

    /**
     * Map an Exception to a user readable error message.
     * <p>
     * Dev Note: the return value should preferable fit on a single line
     *
     * @param context Current context
     * @param e       Throwable to process
     *
     * @return user-friendly error message for the given Throwable
     */
    @NonNull
    public static Optional<String> map(@NonNull final Context context,
                                       @Nullable final Throwable e) {
        if (e != null) {
            String msg = getMsg(context, e);

            if (msg == null && e.getCause() != null) {
                // Handle encapsulated exceptions
                msg = getMsg(context, e.getCause());
            }

            if (msg != null) {
                return Optional.of(msg);
            }
        }
        return Optional.empty();
    }

    @Nullable
    private static String getMsg(@NonNull final Context context,
                                 @NonNull final Throwable e) {
        String msg = null;

        // Use the embedded localised message if possible
        if (e instanceof LocalizedException) {
            msg = ((LocalizedException) e).getUserMessage(context);

        } else if (e instanceof com.hardbacknutter.org.json.JSONException) {
            // we're supposed to catch all JSONException!
            Log.e(TAG, "Please log a bug if you see this message: ", e);
            msg = context.getString(R.string.error_unknown_long,
                                    context.getString(R.string.pt_maintenance));

        } else if (e instanceof java.io.FileNotFoundException) {
            msg = context.getString(R.string.httpErrorFile);

        } else if (e instanceof java.util.zip.ZipException) {
            //TODO: a ZipException is very generic, we'd need to look at the actual text.
            msg = context.getString(R.string.error_import_archive_invalid);

        } else if (e instanceof android.database.SQLException
                   || e instanceof java.sql.SQLException) {
            msg = context.getString(R.string.error_unknown_long,
                                    context.getString(R.string.pt_maintenance));

        } else if (e instanceof java.io.EOFException) {
            msg = context.getString(R.string.error_network_failed_try_again);

        } else if (e instanceof java.net.SocketTimeoutException) {
            msg = context.getString(R.string.httpErrorTimeout);

        } else if (e instanceof java.net.MalformedURLException
                   || e instanceof java.net.UnknownHostException) {
            msg = context.getString(R.string.error_unknown_host, e.getMessage())
                  + '\n' + context.getString(R.string.error_search_failed_network);

        } else if (e instanceof java.security.cert.CertificateEncodingException) {
            msg = context.getString(R.string.error_certificate_invalid);

        } else if (e instanceof java.security.cert.CertificateException) {
            // There was something wrong with certificates/key on OUR end
            msg = context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof javax.net.ssl.SSLException) {
            // TODO: give user detailed message
            // There was something wrong with certificates/key on the REMOTE end
            msg = context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof StackOverflowError) {
            // This is BAD.... but we've only ever seen this in the emulator ... flw
            // ^^^ 2022-04-06
            // TODO: give user detailed message
            msg = context.getString(R.string.error_unknown_long,
                                    context.getString(R.string.pt_maintenance));

        } else if (e instanceof android.system.ErrnoException) {
            final int errno = ((android.system.ErrnoException) e).errno;
            // write failed: ENOSPC (No space left on device)
            if (errno == OsConstants.ENOSPC) {
                msg = context.getString(R.string.error_storage_no_space_left);
            } else {
                // write to logfile for future reporting enhancements.
                ServiceLocator.getInstance().getLogger().warn(TAG, "errno=" + errno);
                msg = Os.strerror(errno);
            }
        }

        return msg;
    }
}
