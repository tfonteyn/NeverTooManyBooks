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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public final class DebugReport {

    /** Log tag. */
    private static final String TAG = "DebugReport";

    /** Prefix for the filename of a database backup. */
    private static final String DB_FILE_PREFIX = "NTMBDebug";

    private DebugReport() {
    }

    /**
     * Collect and send debug info using email.
     * <p>
     * ENHANCE: need a better way to send the db file... mail accounts limit the size of attachments
     *
     * @param context Current context
     */
    public static boolean sendDebugInfo(@NonNull final Context context) {

        final StringBuilder message = new StringBuilder();

        final PackageInfoWrapper info = PackageInfoWrapper.createWithSignatures(context);
        message.append("App: ").append(info.getPackageName()).append('\n')
               .append("Version: ").append(info.getVersionName())
               /* */.append(" (")
               /* */.append(info.getVersionCode())
               /* */.append(", ")
               /* */.append(BuildConfig.TIMESTAMP)
               /* */.append(")\n")
               .append("SDK: ")
               /* */.append(Build.VERSION.RELEASE)
               /* */.append(" (").append(Build.VERSION.SDK_INT).append(' ')
               /* */.append(Build.TAGS).append(")\n")
               .append("Model: ").append(Build.MODEL).append('\n')
               .append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
               .append("Device: ").append(Build.DEVICE).append('\n')
               .append("Product: ").append(Build.PRODUCT).append('\n')
               .append("Brand: ").append(Build.BRAND).append('\n')
               .append("ID: ").append(Build.ID).append('\n')
               .append("Signed-By: ").append(info.getSignedBy()).append('\n');

        Logger.warn(TAG, "sendDebugInfo|" + message);
        logPreferences(context);

        // last part; the user should (hopefully) add their comment to the email
        message.append("\nDetails:\n\n")
               .append(context.getString(R.string.debug_body))
               .append("\n\n");

        // Find all files of interest to send
        final Collection<File> files = new ArrayList<>();

        // Note we deliberately do NOT create a zip file with all files.
        // This can make the email larger, but is friendlier for the user
        // to remove files they do not want to send.
        try {
            // Copy the database from the internal protected area to the cache dir
            // so we can create a valid Uri for it.
            final String fileName =
                    DB_FILE_PREFIX
                    // User local zone - it's for THEIR reference.
                    + '-' + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + ".db";
            final File dbFile = new File(context.getCacheDir(), fileName);
            dbFile.deleteOnExit();
            FileUtils.copy(DBHelper.getDatabasePath(context), dbFile);
            files.add(dbFile);

            // arbitrarily collect 5 and 10 files max.
            files.addAll(FileUtils.collectFiles(ServiceLocator.getUpgradesDir(), null, 5));
            files.addAll(FileUtils.collectFiles(ServiceLocator.getLogDir(), null, 10));

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

            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, to)
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, message.toString())
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            context.startActivity(intent);
            return true;

        } catch (@NonNull final ActivityNotFoundException | IOException e) {
            Logger.error(TAG, e);
            return false;
        }
    }

    /**
     * Write the global preferences to the log file.
     *
     * @param context Current context
     */
    public static void logPreferences(@NonNull final Context context) {
        final Map<String, ?> map = PreferenceManager
                .getDefaultSharedPreferences(context).getAll();
        final List<String> keyList = new ArrayList<>(map.keySet());
        Collections.sort(keyList);

        final StringBuilder sb = new StringBuilder("dumpPreferences|\n\nSharedPreferences:");
        for (final String key : keyList) {
            sb.append('\n').append(key).append('=').append(map.get(key));
        }
        sb.append("\n\n");

        Logger.warn(TAG, sb.toString());
    }

    public static void logScreenParams(@NonNull final Context context) {
        final Resources resources = context.getResources();
        final Configuration configuration = resources.getConfiguration();
        final DisplayMetrics metrics = resources.getDisplayMetrics();

        final String sb =
                "logScreenParams|\n\n"
                + "configuration:\n"
                + "  screenWidthDp=" + configuration.screenWidthDp + '\n'
                + "  screenHeightDp=" + configuration.screenHeightDp + '\n'
                + "  orientation=" + (configuration.orientation == 2 ? "LANDSCAPE" : "PORTRAIT")
                + '\n'
                + "  densityDpi=" + configuration.densityDpi + '\n'
                + "metrics:\n"
                + "  widthPixels=" + metrics.widthPixels + '\n'
                + "  heightPixels=" + metrics.heightPixels + '\n'
                + "  density=" + metrics.density + '\n';

        Logger.warn(TAG, sb);
    }
}
