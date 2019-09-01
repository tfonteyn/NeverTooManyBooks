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
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class BackupInfo {

    /**
     * version of archiver used to write this archive.
     */
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
    private static final String INFO_HAS_DATABASE = "HasDatabase";
    private static final String INFO_HAS_PREFERENCES = "HasSettings";
    /** Note the '2'. Legacy serialized styles are ignored. */
    private static final String INFO_HAS_BOOKLIST_STYLES = "HasBooklistStyles2";

    /**
     * Stores the database version.
     *
     * @since v200
     */
    private static final String INFO_DATABASE_VERSION = "DatabaseVersionCode";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mBundle;

    /**
     * Constructor.
     */
    public BackupInfo() {
        mBundle = new Bundle();
    }

    /**
     * Constructor.
     */
    private BackupInfo(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    /**
     * Static method to create an INFO block based on the current environment.
     *
     * @param container The container being used (we want the version)
     *
     * @return a new BackupInfo object
     */
    @NonNull
    static BackupInfo newInstance(@NonNull final BackupContainer container,
                                  final int bookCount,
                                  final int coverCount,
                                  final boolean hasStyles,
                                  final boolean hasPrefs) {
        Bundle infoBundle = new Bundle();
        if (bookCount > 0) {
            infoBundle.putInt(INFO_NUMBER_OF_BOOKS, bookCount);
        }
        if (coverCount > 0) {
            infoBundle.putInt(INFO_NUMBER_OF_COVERS, coverCount);
        }
        infoBundle.putBoolean(INFO_HAS_PREFERENCES, hasPrefs);
        infoBundle.putBoolean(INFO_HAS_BOOKLIST_STYLES, hasStyles);

        infoBundle.putInt(INFO_ARCHIVER_VERSION, container.getVersion());

        infoBundle.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        infoBundle.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);
        infoBundle.putString(INFO_CREATION_DATE, DateUtils.utcSqlDateTimeForToday());

        PackageInfo packageInfo = App.getPackageInfo(0);
        if (packageInfo != null) {
            infoBundle.putString(INFO_APP_PACKAGE, packageInfo.packageName);
            infoBundle.putString(INFO_APP_VERSION_NAME, packageInfo.versionName);
            if (Build.VERSION.SDK_INT >= 28) {
                infoBundle.putLong(INFO_APP_VERSION_CODE, packageInfo.getLongVersionCode());
            } else {
                infoBundle.putLong(INFO_APP_VERSION_CODE, packageInfo.versionCode);
            }
        }
        return new BackupInfo(infoBundle);
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

    @Nullable
    public Date getCreationDate() {
        return DateUtils.parseDate(mBundle.getString(INFO_CREATION_DATE));
    }

    public int getArchVersion() {
        return mBundle.getInt(INFO_ARCHIVER_VERSION);
    }

    @SuppressWarnings("unused")
    @Nullable
    public String getAppPackage() {
        return mBundle.getString(INFO_APP_PACKAGE);
    }

    @SuppressWarnings("unused")
    @Nullable
    public String getAppVersionName() {
        return mBundle.getString(INFO_APP_VERSION_NAME);
    }

    public long getAppVersionCode() {
        return mBundle.getLong(INFO_APP_VERSION_CODE);
    }

    /**
     * @return version of the *database* this archive was generated from;
     * or 0 for a database version 81 or earlier
     *
     * @since v200
     */
    @SuppressWarnings("unused")
    public int getDatabaseVersionCode() {
        if (getAppVersionCode() == 179L) {
            // 5.2.2
            return 82;
        }
        return mBundle.getInt(INFO_DATABASE_VERSION, 0);
    }

    @SuppressWarnings("unused")
    public int getSdk() {
        return mBundle.getInt(INFO_SDK);
    }

    @SuppressWarnings("unused")
    public boolean hasDatabase() {
        return mBundle.getBoolean(INFO_HAS_DATABASE);
    }

    /**
     * We *should* have Preferences in the archive.
     * At least that was the intention when it was created.
     *
     * @return {@code true} if present
     */
    public boolean hasPreferences() {
        return mBundle.getBoolean(INFO_HAS_PREFERENCES);
    }

    /**
     * We *should* have Styles in the archive.
     * At least that was the intention when it was created.
     *
     * @return {@code true} if present
     */
    public boolean hasBooklistStyles() {
        return mBundle.getBoolean(INFO_HAS_BOOKLIST_STYLES);
    }

    /**
     * Check if the archive has books (with or without an exact number).
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
        return mBundle.containsKey(INFO_NUMBER_OF_BOOKS) && mBundle.getInt(
                INFO_NUMBER_OF_BOOKS) > 0;
    }

    public int getBookCount() {
        return mBundle.getInt(INFO_NUMBER_OF_BOOKS);
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
        return mBundle.containsKey(INFO_NUMBER_OF_COVERS) && mBundle.getInt(
                INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mBundle.getInt(INFO_NUMBER_OF_COVERS);
    }

    /**
     * This is partially a debug method and partially a basic check to see if the info
     * block looks more or less correct.
     *
     * @return {@code true} if valid
     */
    public boolean isValid() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Logger.debug(this, "isValid", mBundle.toString());
        }
        // extremely simple check: we assume that if one field is present, the rest will be there.
        return mBundle.containsKey(INFO_ARCHIVER_VERSION);
    }
}
