/*
 * @Copyright 2019 HardBackNutter
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
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

public final class DebugReport {

    /** files with these prefixes will be bundled in the report. */
    private static final String[] FILE_PREFIXES = new String[]{
            "DbUpgrade", "DbExport", "error.log", "export.csv"};

    private DebugReport() {
    }

    /**
     * Return the MD5 hash of the public key that signed this app, or a useful
     * text message if an error or other problem occurred.
     */
    @SuppressLint("PackageManagerGetSignatures")
    public static String signedBy(@NonNull final Context context) {
        StringBuilder signedBy = new StringBuilder();

        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo;
            if (Build.VERSION.SDK_INT >= 28) {
                appInfo = manager.getPackageInfo(context.getPackageName(),
                                                 PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                // PackageManagerGetSignatures
                appInfo = manager.getPackageInfo(context.getPackageName(),
                                                 PackageManager.GET_SIGNATURES);
            }

            // Each sig is a PK of the signer:
            //  https://groups.google.com/forum/?fromgroups=#!topic/android-developers/fPtdt6zDzns
            for (Signature sig : appInfo.signatures) {
                if (sig != null) {
                    final MessageDigest sha1 = MessageDigest.getInstance("MD5");
                    final byte[] publicKey = sha1.digest(sig.toByteArray());
                    // Turn the hex bytes into a more traditional MD5 string representation.
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

                    // Append as needed (theoretically could have more than one sig */
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
     */
    public static boolean sendDebugInfo(@NonNull final Context context) {

        // Collect all info
        StringBuilder message = new StringBuilder();

        // Get app info
        PackageInfo packageInfo = App.getPackageInfo(0);
        if (packageInfo != null) {
            message.append("App: ").append(packageInfo.packageName).append('\n')
                   .append("Version: ").append(packageInfo.versionName)
                   .append(" (");
            if (Build.VERSION.SDK_INT >= 28) {
                message.append(packageInfo.getLongVersionCode());
            } else {
                message.append(packageInfo.versionCode);
            }
            message.append(")\n");
        } else {
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

               //  urls
               .append("Search sites URL:\n")
               .append(AmazonManager.getBaseURL()).append('\n')
               .append(GoodreadsManager.getBaseURL()).append('\n')
               .append(GoogleBooksManager.getBaseURL()).append('\n')
               .append(IsfdbManager.getBaseURL()).append('\n')
               .append(KbNlManager.getBaseURL()).append('\n')
               .append(LibraryThingManager.getBaseURL()).append('\n')
               .append(OpenLibraryManager.getBaseURL()).append('\n');

        // Scanners installed
        try {
            message.append("Pref. Scanner: ")
                   .append(App.getListPreference(Prefs.pk_scanner_preferred, -1))
                   .append('\n');

            for (String scannerAction : ScannerManager.ALL_ACTIONS) {
                message.append("Scanner [").append(scannerAction).append("]:\n");
                Intent scannerIntent = new Intent(scannerAction, null);
                List<ResolveInfo> resolved =
                        context.getPackageManager().queryIntentActivities(scannerIntent, 0);

                if (!resolved.isEmpty()) {
                    for (ResolveInfo r : resolved) {
                        message.append("    ");
                        // Could be activity or service...
                        if (r.activityInfo != null) {
                            message.append(r.activityInfo.packageName);
                        } else if (r.serviceInfo != null) {
                            message.append(r.serviceInfo.packageName);
                        } else {
                            message.append("UNKNOWN");
                        }
                        message.append(" (priority ").append(r.priority)
                               .append(", preference ").append(r.preferredOrder)
                               .append(", match ").append(r.match)
                               .append(", default=").append(r.isDefault)
                               .append(")\n");
                    }
                } else {
                    message.append("    No packages found\n");
                }
            }
        } catch (@NonNull final RuntimeException e) {
            // Don't lose the other debug info if collecting scanner data dies for some reason
            message.append("Scanner failure: ").append(e.getLocalizedMessage()).append('\n');
        }
        message.append('\n');

        // Tracker history
        message.append("\nHistory:\n").append(Tracker.getEventsInfo()).append('\n');

        // User input
        message.append("Details:\n\n")
               .append(context.getString(R.string.debug_body)).append("\n\n");

        if (BuildConfig.DEBUG /* always */) {
            Logger.debug(DebugReport.class, "sendDebugInfo", message);
        }

        try {
            // Copy the database from the internal protected area to the cache area.
            File dbFile = new File(StorageUtils.getCacheDir(context), "DbExport-tmp.db");
            dbFile.deleteOnExit();
            StorageUtils.copyFile(DBHelper.getDatabasePath(context), dbFile);

            // Find all files of interest to send in the cache and log dirs
            List<String> files = collectFiles(StorageUtils.getCacheDir(context),
                                              Logger.getLogDir(context));

            // Build the attachment list
            ArrayList<Uri> uriList = new ArrayList<>();
            for (String fileSpec : files) {
                File file = new File(fileSpec);
                if (file.exists() && file.length() > 0) {
                    Uri uri = FileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY,
                                                         file);
                    uriList.add(uri);
                }
            }

            // setup the mail message
            String subject = '[' + context.getString(R.string.app_name) + "] "
                             + context.getString(R.string.debug_subject);
            String[] to = context.getString(R.string.email_debug).split(";");
            ArrayList<String> bodyText = new ArrayList<>();
            bodyText.add(message.toString());
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                                    .setType("plain/text")
                                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                                    .putExtra(Intent.EXTRA_EMAIL, to)
                                    .putExtra(Intent.EXTRA_TEXT, bodyText)
                                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            String chooserText = context.getString(R.string.title_send_mail);
            context.startActivity(Intent.createChooser(intent, chooserText));
            return true;

        } catch (@NonNull final NullPointerException | IOException e) {
            Logger.error(context, DebugReport.class, e);
            return false;
        }
    }

    /**
     * Collect applicable files for the list of directories.
     * Does <strong>not</strong> visit sub directories.
     *
     * @param dirs to walk
     *
     * @return list with absolute path names
     */
    private static List<String> collectFiles(@NonNull final File... dirs) {
        List<String> files = new ArrayList<>();
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                //noinspection ConstantConditions
                for (String name : dir.list()) {
                    for (String prefix : FILE_PREFIXES) {
                        if (name.startsWith(prefix)) {
                            files.add(new File(name).getAbsolutePath());
                            break;
                        }
                    }
                }
            }
        }
        return files;
    }
}
