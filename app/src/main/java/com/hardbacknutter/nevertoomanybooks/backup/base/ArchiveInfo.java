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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class ArchiveInfo {

    /** Log tag. */
    private static final String TAG = "ArchiveInfo";

    /** version of archiver used to write this archive. */
    private static final String INFO_ARCHIVER_VERSION = "ArchVersion";
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
    private final Bundle mBundle;

    /**
     * Constructor. Used when reading info from an archive.
     */
    public ArchiveInfo() {
        mBundle = new Bundle();
    }

    /**
     * Constructor to create an INFO block based on the current environment.
     *
     * @param context Current context
     * @param version of the archive structure
     */
    public ArchiveInfo(@NonNull final Context context,
                       final int version) {
        mBundle = new Bundle();
        mBundle.putInt(INFO_ARCHIVER_VERSION, version);

        mBundle.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        mBundle.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);
        mBundle.putString(INFO_CREATION_DATE, DateUtils.utcSqlDateTimeForToday());

        try {
            PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            mBundle.putString(INFO_APP_PACKAGE, info.packageName);
            mBundle.putString(INFO_APP_VERSION_NAME, info.versionName);
            if (Build.VERSION.SDK_INT >= 28) {
                mBundle.putLong(INFO_APP_VERSION_CODE, info.getLongVersionCode());
            } else {
                mBundle.putLong(INFO_APP_VERSION_CODE, info.versionCode);
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
        return mBundle;
    }

    /**
     * Get the version of the Archiver that wrote this archive.
     *
     * @return archive version
     */
    public int getArchiveVersion() {
        return mBundle.getInt(INFO_ARCHIVER_VERSION);
    }

    /**
     * Get the date from the creation-date field.
     *
     * @return date, or {@code null} if none or invalid
     */
    @Nullable
    public Date getCreationDate() {
        return DateUtils.parseSqlDateTime(mBundle.getString(INFO_CREATION_DATE));
    }

    /**
     * Get the version of the <strong>application</strong> this archive was generated from.
     *
     * @return version
     */
    private long getAppVersionCode() {
        // old archives used an Integer, newer use Long.
        Object version = mBundle.get(INFO_APP_VERSION_CODE);
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
        return mBundle.getInt(INFO_DATABASE_VERSION, 0);
    }

    /**
     * Check if the archive has a books file stored (with or without an exact number).
     *
     * @return {@code true} if books are present
     */
    public boolean hasBooks() {
        return mBundle.getBoolean(INFO_HAS_BOOKS) || hasBookCount();
    }

    /**
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0); this does not
     * mean there might not be any books though. Use {@link #hasBooks()} to be sure.
     *
     * @return {@code true} if the number of books are known
     */
    public boolean hasBookCount() {
        return mBundle.containsKey(INFO_NUMBER_OF_BOOKS)
               && mBundle.getInt(INFO_NUMBER_OF_BOOKS) > 0;
    }

    public int getBookCount() {
        return mBundle.getInt(INFO_NUMBER_OF_BOOKS);
    }

    public void setBookCount(final int count) {
        mBundle.putInt(INFO_NUMBER_OF_BOOKS, count);
    }

    /**
     * Check if the archive has covers (with or without an exact number).
     *
     * @return {@code true} if covers are present
     */
    public boolean hasCovers() {
        return mBundle.getBoolean(INFO_HAS_COVERS) || hasCoverCount();
    }

    /**
     * Check if the archive has a known number of covers.
     * Will return {@code false} if there is no number (or if the number is 0); this does not
     * mean there might not be any covers though. Use {@link #hasCovers()}} to be sure.
     *
     * @return {@code true} if the number of books are known
     */
    public boolean hasCoverCount() {
        return mBundle.containsKey(INFO_NUMBER_OF_COVERS)
               && mBundle.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mBundle.getInt(INFO_NUMBER_OF_COVERS);
    }

    public void setCoverCount(final int count) {
        mBundle.putInt(INFO_NUMBER_OF_COVERS, count);
    }

    /**
     * This is partially a debug method and partially a basic check to see if the info
     * block looks more or less correct.
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    public void validate()
            throws InvalidArchiveException {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "validate|" + mBundle);
        }

        // extremely simple check: the archiver version field must be present
        if (!mBundle.containsKey(INFO_ARCHIVER_VERSION)) {
            throw new InvalidArchiveException("info block lacks version field");
        }
    }
}
