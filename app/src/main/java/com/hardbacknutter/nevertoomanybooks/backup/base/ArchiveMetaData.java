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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class ArchiveMetaData {

    /** Version of archiver used to write this archive. */
    private static final String INFO_ARCHIVER_VERSION = "ArchVersion";
    /** Creation Date of archive (in SQL format). */
    private static final String INFO_CREATION_DATE = "CreateDate";
    /** Identifier. */
    private static final String INFO_APP_PACKAGE = "AppPackage";
    /** For reference only. */
    private static final String INFO_APP_VERSION_NAME = "AppVersionName";
    /** Can be used for version checks. */
    private static final String INFO_APP_VERSION_CODE = "AppVersionCode";
    /** Debug info. */
    private static final String INFO_SDK = "SDK";
    /** Stores the database version. */
    private static final String INFO_DATABASE_VERSION = "DatabaseVersionCode";

    private static final String INFO_NUMBER_OF_BOOKS = "NumBooks";
    private static final String INFO_NUMBER_OF_COVERS = "NumCovers";

    private static final String ERROR_MISSING_VERSION_INFORMATION = "Missing version information";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mInfo;

    /**
     * Constructor used while reading from an Archive.
     */
    public ArchiveMetaData() {
        mInfo = new Bundle();
    }

    /**
     * Constructor used while reading from an Archive.
     */
    public ArchiveMetaData(@NonNull final Bundle from) {
        mInfo = from;
    }

    /**
     * Constructor used while writing to an Archive.
     *
     * @param context Current context
     * @param version of the archive structure
     * @param data    to add to the header bundle
     */
    @NonNull
    public static ArchiveMetaData create(@NonNull final Context context,
                                         final int version,
                                         @NonNull final ExportResults data) {

        final ArchiveMetaData metaData = new ArchiveMetaData();
        // Pure info/debug information
        metaData.mInfo.putInt(INFO_ARCHIVER_VERSION, version);
        metaData.mInfo.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);
        final PackageInfoWrapper info = PackageInfoWrapper.create(context);
        metaData.mInfo.putString(INFO_APP_PACKAGE, info.getPackageName());
        metaData.mInfo.putString(INFO_APP_VERSION_NAME, info.getVersionName());
        metaData.mInfo.putLong(INFO_APP_VERSION_CODE, info.getVersionCode());
        metaData.mInfo.putInt(INFO_SDK, Build.VERSION.SDK_INT);

        // the actual data the user will care about
        metaData.mInfo.putString(INFO_CREATION_DATE, LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (data.getBookCount() > 0) {
            metaData.mInfo.putInt(INFO_NUMBER_OF_BOOKS, data.getBookCount());
        }
        if (data.getCoverCount() > 0) {
            metaData.mInfo.putInt(INFO_NUMBER_OF_COVERS, data.getCoverCount());
        }
        return metaData;
    }

    /**
     * Get the raw bundle, this is used for reading out the info block to the backup archive.
     *
     * @return the bundle with all settings.
     */
    @NonNull
    public Bundle getBundle() {
        return mInfo;
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
     * Get the version of the Archiver that wrote this archive.
     *
     * @return archive version
     */
    int getArchiveVersion() {
        return mInfo.getInt(INFO_ARCHIVER_VERSION);
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
     * Get the version of the <strong>application</strong> this archive was generated from.
     *
     * @return version
     */
    public long getAppVersionCode() {
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
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any books though.
     *
     * @return {@code true} if the number of books is <strong>known</strong>
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
     * Check if the archive has a known number of covers.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any covers though.
     *
     * @return {@code true} if the number of books is <strong>known</strong>
     */
    public boolean hasCoverCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_COVERS)
               && mInfo.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mInfo.getInt(INFO_NUMBER_OF_COVERS);
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
            throw new InvalidArchiveException(ERROR_MISSING_VERSION_INFORMATION);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ArchiveMetaData{"
               + "mInfo=" + mInfo
               + '}';
    }
}
