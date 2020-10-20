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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

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
     * Collect and send debug info to a support email address.
     * <p>
     * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
     *
     * @param context Current context
     */
    public static boolean sendDebugInfo(@NonNull final Context context) {

        final StringBuilder message = new StringBuilder();

        final PackageInfoWrapper info = PackageInfoWrapper.createWithSignatures(context);
        message.append("App: ").append(info.getPackageName()).append('\n')
               .append("Version: ").append(info.getVersionName())
               .append(" (")
               .append(info.getVersionCode())
               .append(", ")
               .append(BuildConfig.TIMESTAMP)
               .append(")\n");


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

               .append("Signed-By: ").append(info.getSignedBy()).append('\n')
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
            final File dbFile = AppDir.Cache.getFile(
                    context,
                    DB_EXPORT_FILE_PREFIX
                    + '-' + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
            final ArrayList<Uri> uriList = files
                    .stream()
                    .filter(file -> file.exists() && file.length() > 0)
                    .map(file -> GenericFileProvider.getUriForFile(context, file))
                    .collect(Collectors.toCollection(ArrayList::new));


            // setup the mail message
            final String subject = '[' + context.getString(R.string.app_name) + "] "
                                   + context.getString(R.string.debug_subject);
            final String[] to = context.getString(R.string.email_debug).split(";");
            final ArrayList<String> bodyText = new ArrayList<>();
            bodyText.add(message.toString());
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("plain/text")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_EMAIL, to)
                    .putExtra(Intent.EXTRA_TEXT, bodyText)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            final String chooserText = context.getString(R.string.lbl_send_mail);
            context.startActivity(Intent.createChooser(intent, chooserText));
            return true;

        } catch (@NonNull final NullPointerException | IOException e) {
            Logger.error(context, TAG, e);
            return false;
        }
    }
}
