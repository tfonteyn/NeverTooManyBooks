/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;

public final class DebugReport {

    /** Log tag. */
    private static final String TAG = "DebugReport";

    /**
     * Prefix for the filename of a database backup.
     * Created in {@link AppDir#Cache}.
     */
    private static final String DB_EXPORT_FILE_PREFIX = "DBDebug";

    private DebugReport() {
    }

    /**
     * Return the SHA256 hash of the public key that signed this app, or a useful
     * text message if an error or other problem occurred.
     *
     * <pre>
     *     {@code
     *     keytool -list -keystore myKeyStore.jks -storepass myPassword -v
     *      ...
     *      Certificate fingerprints:
     *          ...
     *          SHA256: D4:98:1C:F7:...    <= this one
     *     }
     * </pre>
     *
     * @param context Current context
     */
    @SuppressLint("PackageManagerGetSignatures")
    public static String signedBy(@NonNull final Context context) {
        StringBuilder signedBy = new StringBuilder();

        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 28) {
                info = context.getPackageManager()
                              .getPackageInfo(context.getPackageName(),
                                              PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                // PackageManagerGetSignatures
                info = context.getPackageManager()
                              .getPackageInfo(context.getPackageName(),
                                              PackageManager.GET_SIGNATURES);
            }

            // concat the signature chain.
            for (Signature sig : info.signatures) {
                if (sig != null) {
                    final MessageDigest md = MessageDigest.getInstance("SHA256");
                    final byte[] publicKey = md.digest(sig.toByteArray());
                    // Turn the hex bytes into a more traditional string representation.
                    final StringBuilder hexString = new StringBuilder();
                    boolean first = true;
                    for (byte aPublicKey : publicKey) {
                        if (first) {
                            first = false;
                        } else {
                            hexString.append(':');
                        }
                        String byteString = Integer.toHexString(0xFF & aPublicKey);
                        if (byteString.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(byteString);
                    }
                    String fingerprint = hexString.toString();

                    if (signedBy.length() == 0) {
                        signedBy.append(fingerprint);
                    } else {
                        signedBy.append('/').append(fingerprint);
                    }
                }
            }
        } catch (@NonNull final PackageManager.NameNotFoundException
                | NoSuchAlgorithmException
                | RuntimeException e) {
            return e.getLocalizedMessage();
        }
        return signedBy.toString();
    }

    /**
     * Collect and send debug info to a support email address.
     * <p>
     * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
     *
     * @param context Current context
     */
    public static boolean sendDebugInfo(@NonNull final Context context) {

        final StringBuilder message = new StringBuilder();

        try {
            PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            message.append("App: ").append(info.packageName).append('\n')
                   .append("Version: ").append(info.versionName)
                   .append(" (");
            if (Build.VERSION.SDK_INT >= 28) {
                message.append(info.getLongVersionCode());
            } else {
                message.append(info.versionCode);
            }
            message.append(")\n");

        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            message.append("PackageInfo == null?");
        }

        message.append("SDK: ")
               /* */.append(Build.VERSION.RELEASE)
               /* */.append(" (").append(Build.VERSION.SDK_INT).append(' ')
               /* */.append(Build.TAGS).append(")\n")
               .append("Model: ").append(Build.MODEL).append('\n')
               .append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
               .append("Device: ").append(Build.DEVICE).append('\n')
               .append("Product: ").append(Build.PRODUCT).append('\n')
               .append("Brand: ").append(Build.BRAND).append('\n')
               .append("ID: ").append(Build.ID).append('\n')

               .append("Signed-By: ").append(signedBy(context)).append('\n')
               .append("\nSearch sites URL:\n")
               .append(SearchSites.getSiteUrls(context))
               .append("\nScanner info:\n")
               .append(ScannerManager.collectDebugInfo(context))
               .append("\nDetails:\n\n")
               .append(context.getString(R.string.debug_body))
               .append("\n\n");

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "sendDebugInfo|" + message);
        }

        try {
            // Copy the database from the internal protected area to the cache area.
            final File dbFile = AppDir.Cache.getFile(context, DB_EXPORT_FILE_PREFIX
                                                              + '-'
                                                              + DateUtils.utcSqlDate(new Date())
                                                              + ".db");
            dbFile.deleteOnExit();
            FileUtils.copy(DBHelper.getDatabasePath(context), dbFile);

            // Find all files of interest to send
            final Collection<File> files = new ArrayList<>();

            files.addAll(AppDir.Log.collectFiles(context, file ->
                    file.getName().startsWith(Logger.ERROR_LOG_FILE)));

            files.addAll(AppDir.Upgrades.collectFiles(context, file ->
                    file.getName().startsWith(DBHelper.DB_UPGRADE_FILE_PREFIX)));

            files.addAll(AppDir.Cache.collectFiles(context, file ->
                    file.getName().startsWith(DB_EXPORT_FILE_PREFIX)));

            // Build the attachment list
            final ArrayList<Uri> uriList = new ArrayList<>();
            for (File file : files) {
                if (file.exists() && file.length() > 0) {
                    Uri uri = GenericFileProvider.getUriForFile(context, file);
                    uriList.add(uri);
                }
            }

            // setup the mail message
            final String subject = '[' + context.getString(R.string.app_name) + "] "
                                   + context.getString(R.string.debug_subject);
            final String[] to = context.getString(R.string.email_debug).split(";");
            ArrayList<String> bodyText = new ArrayList<>();
            bodyText.add(message.toString());
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("plain/text")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_EMAIL, to)
                    .putExtra(Intent.EXTRA_TEXT, bodyText)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            final String chooserText = context.getString(R.string.title_send_mail);
            context.startActivity(Intent.createChooser(intent, chooserText));
            return true;

        } catch (@NonNull final NullPointerException | IOException e) {
            Logger.error(context, TAG, e);
            return false;
        }
    }
}
