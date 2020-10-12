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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class ArchiveInfo {

    /** Version of archiver used to write this archive. */
    private static final String INFO_ARCHIVER_VERSION = "ArchVersion";
    /** Creation Date of archive (in SQL format). */
    private static final String INFO_CREATION_DATE = "CreateDate";

    private static final String INFO_APP_PACKAGE = "AppPackage";
    private static final String INFO_APP_VERSION_NAME = "AppVersionName";
    private static final String INFO_APP_VERSION_CODE = "AppVersionCode";
    private static final String INFO_SDK = "SDK";

    private static final String INFO_NUMBER_OF_BOOKS = "NumBooks";
    private static final String INFO_NUMBER_OF_COVERS = "NumCovers";

    private static final String INFO_HAS_BOOKS = "HasBooks";
    private static final String INFO_HAS_COVERS = "HasCovers";

    /** Stores the database version. */
    private static final String INFO_DATABASE_VERSION = "DatabaseVersionCode";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mInfo;

    /**
     * Constructor. Used when reading info from an archive.
     */
    public ArchiveInfo() {
        mInfo = new Bundle();
    }

    /**
     * Constructor to create an INFO block based on the current environment.
     *
     * @param context Current context
     * @param version of the archive structure
     */
    public ArchiveInfo(@NonNull final Context context,
                       final int version) {
        mInfo = new Bundle();
        mInfo.putInt(INFO_ARCHIVER_VERSION, version);

        mInfo.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        mInfo.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);
        mInfo.putString(INFO_CREATION_DATE,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            final PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            mInfo.putString(INFO_APP_PACKAGE, info.packageName);
            mInfo.putString(INFO_APP_VERSION_NAME, info.versionName);
            if (Build.VERSION.SDK_INT >= 28) {
                mInfo.putLong(INFO_APP_VERSION_CODE, info.getLongVersionCode());
            } else {
                mInfo.putLong(INFO_APP_VERSION_CODE, info.versionCode);
            }
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // ignore
        }
    }

    /**
     * Get the raw bundle, this is used for writing out the info block to the backup archive.
     *
     * @return the bundle with all settings.
     */
    @NonNull
    public Bundle getBundle() {
        return mInfo;
    }

    /**
     * Get the version of the Archiver that wrote this archive.
     *
     * @return archive version
     */
    public int getArchiveVersion() {
        return mInfo.getInt(INFO_ARCHIVER_VERSION);
    }

    /**
     * Get the date from the creation-date field.
     *
     * @param context Current context
     *
     * @return date, or {@code null} if none or invalid
     */
    @Nullable
    public LocalDateTime getCreationDate(@NonNull final Context context) {
        return DateParser.getInstance(context).parseISO(mInfo.getString(INFO_CREATION_DATE));
    }

    /**
     * Get the version of the <strong>application</strong> this archive was generated from.
     *
     * @return version
     */
    private long getAppVersionCode() {
        // old archives used an Integer, newer use Long.
        final Object version = mInfo.get(INFO_APP_VERSION_CODE);
        if (version == null) {
            return 0;
        } else {
            try {
                return (long) version;
            } catch (@NonNull final ClassCastException e) {
                return 0;
            }
        }
    }

    /**
     * Get the version of the <strong>database</strong> this archive was generated from.
     *
     * @return version
     */
    public int getDatabaseVersionCode() {
        return mInfo.getInt(INFO_DATABASE_VERSION, 0);
    }

    /**
     * Check if the archive has a books file stored (with or without an exact number).
     *
     * @return {@code true} if books are present
     */
    public boolean hasBooks() {
        return mInfo.getBoolean(INFO_HAS_BOOKS) || hasBookCount();
    }

    /**
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0); this does not
     * mean there might not be any books though. Use {@link #hasBooks()} to be sure.
     *
     * @return {@code true} if the number of books are known
     */
    public boolean hasBookCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_BOOKS)
               && mInfo.getInt(INFO_NUMBER_OF_BOOKS) > 0;
    }

    public int getBookCount() {
        return mInfo.getInt(INFO_NUMBER_OF_BOOKS);
    }

    public void setBookCount(final int count) {
        mInfo.putInt(INFO_NUMBER_OF_BOOKS, count);
    }

    /**
     * Check if the archive has covers (with or without an exact number).
     *
     * @return {@code true} if covers are present
     */
    public boolean hasCovers() {
        return mInfo.getBoolean(INFO_HAS_COVERS) || hasCoverCount();
    }

    /**
     * Check if the archive has a known number of covers.
     * Will return {@code false} if there is no number (or if the number is 0); this does not
     * mean there might not be any covers though. Use {@link #hasCovers()}} to be sure.
     *
     * @return {@code true} if the number of books are known
     */
    public boolean hasCoverCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_COVERS)
               && mInfo.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mInfo.getInt(INFO_NUMBER_OF_COVERS);
    }

    public void setCoverCount(final int count) {
        mInfo.putInt(INFO_NUMBER_OF_COVERS, count);
    }

    /**
     * This is partially a debug method and partially a basic check to see if the info
     * block looks more or less correct.
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    public void validate()
            throws InvalidArchiveException {
        // extremely simple check: the archiver version field must be present
        if (!mInfo.containsKey(INFO_ARCHIVER_VERSION)) {
            throw new InvalidArchiveException("info block lacks version field");
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ArchiveInfo{"
               + "mInfo=" + mInfo
               + '}';
    }
}
