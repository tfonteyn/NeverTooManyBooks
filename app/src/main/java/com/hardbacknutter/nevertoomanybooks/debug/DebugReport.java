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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public class DebugReport {

    private final Collection<File> files = new ArrayList<>();
    @NonNull
    private final Context context;
    private final String dateTime;
    @Nullable
    private String message;
    @Nullable
    private String preferences;

    public DebugReport(@NonNull final Context context) {
        this.context = context;
        // User local zone - it's for THEIR reference.
        dateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @NonNull
    public DebugReport addDefaultMessage() {
        final PackageInfoWrapper info = PackageInfoWrapper.createWithSignatures(context);

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        message = "App: " + info.getPackageName() + '\n'
                  + "Version: " + info.getVersionName()
                  + " (" + info.getVersionCode() + ", " + BuildConfig.TIMESTAMP + ")\n"
                  + "User country: " + locale.getCountry() + '\n'
                  + "User language: " + locale.getLanguage() + '\n'
                  + "SDK: " + Build.VERSION.RELEASE
                  + " (" + Build.VERSION.SDK_INT + ' ' + Build.TAGS + ")\n"
                  + "Model: " + Build.MODEL + '\n'
                  + "Manufacturer: " + Build.MANUFACTURER + '\n'
                  + "Device: " + Build.DEVICE + '\n'
                  + "Product: " + Build.PRODUCT + '\n'
                  + "Brand: " + Build.BRAND + '\n'
                  + "Build: " + Build.ID + '\n'
                  + "Signed-By: " + info.getSignedBy() + '\n';
        return this;
    }

    public void addDatabase()
            throws IOException {

        final File file = new File(context.getCacheDir(), "nevertoomanybooks.db");
        file.deleteOnExit();
        // Copy the database from the internal protected area to the cache dir
        // so we can create a valid Uri for it.
        FileUtils.copy(DBHelper.getDatabasePath(context), file);
        files.add(file);

    }

    public void addDatabaseUpgrades(final int maxFiles)
            throws IOException {
        files.addAll(collectFiles(ServiceLocator.getUpgradesDir(), maxFiles));
    }

    public void addLogs(final int maxFiles)
            throws IOException {
        files.addAll(collectFiles(ServiceLocator.getLogDir(), maxFiles));
    }

    public void addPreferences()
            throws FileNotFoundException {
        final Map<String, ?> map = PreferenceManager
                .getDefaultSharedPreferences(context).getAll();

        preferences = map.keySet()
                         .stream()
                         .sorted()
                         .map(k -> {
                             if (k.contains("password")) {
                                 return k + "=********";
                             } else {
                                 return k + "=" + map.get(k);
                             }
                         })
                         .collect(Collectors.joining("\n"));
    }

    public void sendToFile(@NonNull final Uri destUri)
            throws IOException {

        final File file = zipAllInfo();
        try (InputStream is = new FileInputStream(file);
             OutputStream os = context.getContentResolver().openOutputStream(destUri)) {
            if (os != null) {
                FileUtils.copy(is, os);
            }
        }
    }

    @NonNull
    private File zipAllInfo()
            throws IOException {

        if (message != null) {
            writeAndAddFile("info.txt", message);
        }
        if (preferences != null) {
            writeAndAddFile("preferences.txt", preferences);
        }

        final File zipFile = new File(context.getCacheDir(), "NTMBBugReport-" + dateTime + ".zip");
        zipFile.deleteOnExit();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile),
                                         RecordWriter.BUFFER_SIZE))) {

            for (final File file : files) {
                final ZipEntry entry = new ZipEntry(file.getName());
                entry.setTime(file.lastModified());
                entry.setMethod(ZipEntry.DEFLATED);
                zipOutputStream.putNextEntry(entry);
                try (InputStream is = new FileInputStream(file)) {
                    FileUtils.copy(is, zipOutputStream);
                } finally {
                    zipOutputStream.closeEntry();
                }
            }
        }

        return zipFile;
    }

    private void writeAndAddFile(@NonNull final String filename,
                                 @NonNull final String text)
            throws FileNotFoundException, UnsupportedEncodingException {
        final File file = new File(context.getCacheDir(), filename);
        file.deleteOnExit();
        // Charset needs API 33
        //noinspection CharsetObjectCanBeUsed
        try (PrintWriter printWriter = new PrintWriter(file, "UTF-8")) {
            printWriter.println(text);
        }
        files.add(file);
    }

    @NonNull
    public ArrayList<Uri> createUriList() {
        // Build the attachment list
        return files
                .stream()
                .filter(file -> file.exists() && file.length() > 0)
                .map(file -> GenericFileProvider.createUri(context, file))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @NonNull
    private List<File> collectFiles(@NonNull final File dir,
                                    final int maxFiles) {
        List<File> list = FileUtils.collectFiles(dir, null);
        // Sort in reverse order. Newest file first.
        list.sort((o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
        list = list.subList(0, Math.min(maxFiles, list.size()));
        return list;
    }

    @NonNull
    public DebugReport addScreenParams() {
        final Resources res = context.getResources();
        final Configuration configuration = res.getConfiguration();
        final DisplayMetrics metrics = res.getDisplayMetrics();

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

        if (message != null) {
            message += "\n" + sb;
        } else {
            message = sb;
        }

        return this;
    }
}
