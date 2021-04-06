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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.org.json.JSONException;

public final class ExMsg {

    private ExMsg() {
    }

    /**
     * Map an Exception to a user readable error message.
     * <p>
     * Dev Note: the return value should preferable fit on a single line
     *
     * @param context Current context
     * @param tag     the tag from the caller object
     * @param e       Throwable to process
     *
     * @return user-friendly error message for the given site
     */
    @Nullable
    public static String map(@NonNull final Context context,
                             @NonNull final String tag,
                             @Nullable final Throwable e) {
        if (e == null) {
            return null;
        }

        String msg = getMsg(context, tag, e);
        if (msg != null) {
            return msg;
        }

        if (e instanceof IOException) {
            // Handle encapsulated exceptions, but don't handle pure IOException
            // The latter must be handled by the caller.
            msg = getMsg(context, tag, e.getCause());
        }

        return msg;
    }

    //FIXME: review some of these error message and improve them
    @Nullable
    private static String getMsg(@NonNull final Context context,
                                 @NonNull final String tag,
                                 @Nullable final Throwable e) {
        String msg = null;

        // One of ours ? use the embedded localised message
        if (e instanceof LocalizedException) {
            msg = ((LocalizedException) e).getLocalizedMessage(context);

        } else if (e instanceof FileNotFoundException) {
            msg = context.getString(R.string.httpErrorFile);

        } else if (e instanceof JSONException) {
            msg = context.getString(R.string.error_unknown_long);

        } else if (e instanceof android.database.SQLException
                   || e instanceof java.sql.SQLException) {
            msg = context.getString(R.string.error_unknown_long);

        } else if (e instanceof DaoWriteException) {
            msg = context.getString(R.string.error_storage_not_writable);

        } else if (e instanceof SocketTimeoutException) {
            msg = context.getString(R.string.httpErrorTimeout);

        } else if (e instanceof MalformedURLException) {
            msg = context.getString(R.string.error_search_failed_network);

        } else if (e instanceof UnknownHostException) {
            msg = context.getString(R.string.error_search_failed_network);

        } else if (e instanceof CertificateEncodingException) {
            msg = context.getString(R.string.error_certificate_invalid);

        } else if (e instanceof CertificateException) {
            // There was something wrong with certificates/key on OUR end
            msg = context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof SSLException) {
            // TODO: give user detailed message
            // There was something wrong with certificates/key on the REMOTE end
            msg = context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof ErrnoException) {
            final int errno = ((ErrnoException) e).errno;
            // write failed: ENOSPC (No space left on device)
            if (errno == OsConstants.ENOSPC) {
                msg = context.getString(R.string.error_storage_no_space_left);
            } else {
                msg = Os.strerror(errno);
                // write to logfile for future reporting enhancements.
                Logger.warn(tag, "errno=" + errno);
            }
        }

        return msg;
    }

    @NonNull
    public static String ioExFallbackMsg(@NonNull final Context context,
                                         @Nullable final Exception e,
                                         @NonNull final String fallbackMsg) {
        String msg = null;
        // generic storage related IOException message
        if (e instanceof IOException) {
            if (BuildConfig.DEBUG /* always */) {
                // in debug mode show the raw exception
                msg = context.getString(R.string.error_unknown)
                      + "\n\n" + e.getLocalizedMessage();
            } else {
                msg = StandardDialogs.createBadError(context, fallbackMsg);
            }
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                // in debug mode show the raw exception
                msg = context.getString(R.string.error_unknown);
                if (e != null) {
                    msg += "\n\n" + e.getLocalizedMessage();
                }
            } else {
                // when not in debug, ask for feedback
                msg = StandardDialogs.createBadError(context, R.string.error_unknown);
            }
        }

        return msg;
    }
}
