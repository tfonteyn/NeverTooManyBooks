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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.Optional;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.UpgradeFailedException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpUnauthorizedException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

public final class ExMsg {

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
        if (e instanceof IOException) {
            return getMsg(context, (IOException) e);

        } else if (e instanceof SearchException) {
            return ((SearchException) e).getUserMessage(context);

        } else if (e instanceof ValidatorException) {
            // The ValidatorException expects a localized message, so just use it
            return e.getLocalizedMessage();

        } else if (e instanceof UpgradeFailedException) {
            // The UpgradeFailedException expects a localized message, so just use it
            return e.getLocalizedMessage();

        } else if (e instanceof CoverStorageException) {
            return context.getString(R.string.error_storage_not_accessible);

        } else if (e instanceof DaoWriteException) {
            return context.getString(R.string.error_storage_not_writable);


        } else if (e instanceof DataReaderException) {
            return ((DataReaderException) e).getUserMessage(context);

        } else if (e instanceof DataWriterException) {
            // Typically (but not enforced) the wrapped exception will be a JSONException
            return map(context, e.getCause())
                    // TODO: give user detailed message
                    .orElse(context.getString(R.string.error_export_failed));

        } else if (e instanceof CredentialsException) {
            final CredentialsException ce = (CredentialsException) e;
            return context.getString(R.string.error_site_authentication_failed,
                                     context.getString(ce.getSiteResId()));

        } else if (e instanceof com.hardbacknutter.org.json.JSONException) {
            //TODO: a JSONException is very generic, we'd need to look at the actual text.
            return context.getString(R.string.error_unexpected_long,
                                     context.getString(R.string.pt_maintenance));

        } else if (e instanceof android.database.SQLException
                   || e instanceof java.sql.SQLException) {
            return context.getString(R.string.error_unexpected_long,
                                     context.getString(R.string.pt_maintenance));

        } else if (e instanceof java.security.cert.CertificateEncodingException) {
            return context.getString(R.string.error_certificate_invalid);

        } else if (e instanceof java.security.cert.CertificateException) {
            // There was something wrong with certificates/key on OUR end
            return context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof java.lang.StackOverflowError) {
            // This is BAD.... but we've only ever seen this in the emulator ... flw
            // ^^^ 2022-04-06
            // TODO: give user detailed message
            return context.getString(R.string.error_unexpected_long,
                                     context.getString(R.string.pt_maintenance));

        } else if (e instanceof android.system.ErrnoException) {
            return mapErnoException(context, (android.system.ErrnoException) e);
        }
        return null;
    }

    /**
     * All IOException's.
     *
     * @param context Current context
     * @param e       to map
     *
     * @return message, or {@code null} if none matching
     */
    @Nullable
    private static String getMsg(@NonNull final Context context,
                                 @NonNull final IOException e) {
        if (e instanceof NetworkUnavailableException) {
            return context.getString(R.string.error_network_please_connect);
        } else if (e instanceof NetworkException) {
            return context.getString(R.string.error_network_failed_try_again);
        } else if (e instanceof HttpNotFoundException) {
            final HttpNotFoundException he = (HttpNotFoundException) e;
            final String msg = he.getUserMessage();
            if (msg != null) {
                return msg;
            }
            if (he.getSiteResId() != 0) {
                return context.getString(R.string.error_network_site_access_failed,
                                         context.getString(he.getSiteResId()));
            } else {
                return context.getString(R.string.httpErrorFileNotFound);
            }
        } else if (e instanceof HttpUnauthorizedException) {
            final HttpUnauthorizedException he = (HttpUnauthorizedException) e;
            if (he.getSiteResId() != 0) {
                return context.getString(R.string.error_site_authorization_failed,
                                         context.getString(he.getSiteResId()));
            } else {
                return context.getString(R.string.error_authorization_failed);
            }
        } else if (e instanceof HttpStatusException) {
            final HttpStatusException he = (HttpStatusException) e;
            if (he.getSiteResId() != 0) {
                return context.getString(R.string.error_network_site_access_failed,
                                         context.getString(he.getSiteResId()))
                       + " (" + he.getStatusCode() + ")";
            } else {
                return context.getString(R.string.httpError) + " (" + he.getStatusCode() + ")";
            }
        } else if (e instanceof java.io.FileNotFoundException) {
            return context.getString(R.string.httpErrorFile);

        } else if (e instanceof java.util.zip.ZipException) {
            //TODO: a ZipException is very generic, we'd need to look at the actual text.
            return context.getString(R.string.error_import_archive_invalid);

        } else if (e instanceof java.io.EOFException) {
            return context.getString(R.string.error_network_failed_try_again);

        } else if (e instanceof java.net.SocketTimeoutException) {
            return context.getString(R.string.httpErrorTimeout);

        } else if (e instanceof java.net.MalformedURLException
                   || e instanceof java.net.UnknownHostException) {
            return context.getString(R.string.error_unknown_host, e.getMessage())
                   + '\n' + context.getString(R.string.error_search_failed_network);

        } else if (e instanceof javax.net.ssl.SSLException) {
            // TODO: give user detailed message
            // There was something wrong with certificates/key on the REMOTE end
            return context.getString(R.string.httpErrorFailedSslHandshake);

        } else if (e.getCause() instanceof android.system.ErrnoException) {
            return mapErnoException(context, (android.system.ErrnoException) e.getCause());
        }

        return null;
    }

    @NonNull
    private static String mapErnoException(@NonNull final Context context,
                                           @NonNull final android.system.ErrnoException e) {
        final int errno = e.errno;
        // write failed: ENOSPC (No space left on device)
        if (errno == OsConstants.ENOSPC) {
            return context.getString(R.string.error_storage_no_space_left);
        } else {
            // write to logfile for future reporting enhancements.
            LoggerFactory.getLogger().w("android.system.ErrnoException",
                                        "errno=" + errno);
            return Os.strerror(errno);
        }
    }

    public static void dumpSSLException(@NonNull final HttpsURLConnection request,
                                        @NonNull final SSLException e) {
        final Logger logger = LoggerFactory.getLogger();
        try {
            logger.w("dumpSSLException", request.getURL().toString());
            final Certificate[] serverCertificates = request.getServerCertificates();
            if (serverCertificates != null && serverCertificates.length > 0) {
                for (final Certificate c : serverCertificates) {
                    logger.w("dumpSSLException", c.toString());
                }
            }
        } catch (@NonNull final Exception ex) {
            logger.e("dumpSSLException", ex);
        }
    }
}
