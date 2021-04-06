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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.ActivityNotFoundException;
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
               .append("\nDetails:\n\n")
               .append(context.getString(R.string.debug_body))
               .append("\n\n");

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "sendDebugInfo|" + message);
        }

        try {
            // Copy the database from the internal protected area to the cache area.
            final String fileName =
                    DB_EXPORT_FILE_PREFIX
                    + '-' + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + ".db";
            final File dbFile = new File(AppDir.Cache.getDir(), fileName);
            dbFile.deleteOnExit();
            FileUtils.copy(DBHelper.getDatabasePath(context), dbFile);

            // Find all files of interest to send
            final Collection<File> files = new ArrayList<>();

            files.addAll(AppDir.Log.collectFiles(null));
            files.addAll(AppDir.Upgrades.collectFiles(null));

            files.addAll(AppDir.Cache.collectFiles(file ->
                                                           file.getName()
                                                               .startsWith(DB_EXPORT_FILE_PREFIX)));

            // Build the attachment list
            final ArrayList<Uri> uriList = files
                    .stream()
                    .filter(file -> file.exists() && file.length() > 0)
                    .map(file -> GenericFileProvider.createUri(context, file))
                    .collect(Collectors.toCollection(ArrayList::new));


            // setup the mail message
            final String[] to = BuildConfig.EMAIL_DEBUG_REPORT.split(";");
            final String subject = "[" + context.getString(R.string.app_name) + "] "
                                   + context.getString(R.string.debug_subject);
            final ArrayList<String> report = new ArrayList<>();
            report.add(message.toString());

            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, to)
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, report)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            context.startActivity(intent);
            return true;

        } catch (@NonNull final NullPointerException | ActivityNotFoundException | IOException e) {
            Logger.error(TAG, e);
            return false;
        }
    }
}
