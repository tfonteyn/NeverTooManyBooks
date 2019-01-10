/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.archivebase;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.CatalogueDBHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.Date;

/**
 * Class to encapsulate the INFO block from an archive.
 *
 * @author pjw
 */
public class BackupInfo
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BackupInfo> CREATOR =
            new Creator<BackupInfo>() {
                @Override
                public BackupInfo createFromParcel(@NonNull final Parcel source) {
                    return new BackupInfo(source);
                }

                @Override
                public BackupInfo[] newArray(final int size) {
                    return new BackupInfo[size];
                }
            };
    /**
     * version of archiver used to write this archive.
     */
    private static final String INFO_ARCHIVER_VERSION = "ArchVersion";
    /**
     * version of archiver needed to read this archive.
     * <p>
     * Obsolete since v200: now always set to the same as the writer.
     * No support for reading new archives by older versions.
     * It would basically mean writing out 2 (or more) copies of the same data
     * for each piece of data where the format changed.
     */
    private static final String INFO_ARCHIVER_COMPATIBLE = "CompatArchiver";


    private static final String INFO_CREATION_DATE = "CreateDate";
    private static final String INFO_NUMBOOKS = "NumBooks";
    private static final String INFO_NUMCOVERS = "NumCovers";
    private static final String INFO_APPPACKAGE = "AppPackage";
    private static final String INFO_APPVERSIONNAME = "AppVersionName";
    private static final String INFO_APPVERSIONCODE = "AppVersionCode";
    private static final String INFO_SDK = "SDK";

    private static final String INFO_HAS_BOOKS = "HasBooks";
    private static final String INFO_HAS_COVERS = "HasCovers";
    private static final String INFO_HAS_DATABASE = "HasDatabase";
    private static final String INFO_HAS_PREFERENCES = "HasSettings";
    private static final String INFO_HAS_BOOKLIST_STYLES = "HasBooklistStyles";

    /**
     * @since v200
     */
    private static final String INFO_DATABASE_VERSION = "DatabaseVersionCode";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mBundle;

    public BackupInfo() {
        mBundle = new Bundle();
    }

    public BackupInfo(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    protected BackupInfo(@NonNull final Parcel in) {
        mBundle = in.readBundle(getClass().getClassLoader());
    }

    /**
     * Static method to create an INFO block based on the current environment.
     *
     * @param container The container being used (we want the version)
     * @param values    information to populate the info block
     *
     * @return a new BackupInfo object
     */
    @NonNull
    static BackupInfo newInstance(@NonNull final BackupContainer container,
                                  @NonNull final InfoUserValues values) {
        Bundle infoBundle = new Bundle();
        if (values.bookCount > 0) {
            infoBundle.putInt(INFO_NUMBOOKS, values.bookCount);
        }
        if (values.coverCount > 0) {
            infoBundle.putInt(INFO_NUMCOVERS, values.coverCount);
        }
        infoBundle.putBoolean(INFO_HAS_PREFERENCES, values.hasPrefs);
        infoBundle.putBoolean(INFO_HAS_BOOKLIST_STYLES, values.hasStyles);

        infoBundle.putInt(INFO_ARCHIVER_VERSION, container.getVersion());
        infoBundle.putInt(INFO_ARCHIVER_COMPATIBLE, container.getVersion());

        infoBundle.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        infoBundle.putInt(INFO_DATABASE_VERSION, CatalogueDBHelper.DATABASE_VERSION);
        infoBundle.putString(INFO_CREATION_DATE, DateUtils.utcSqlDateTimeForToday());
        try {
            Context context = BookCatalogueApp.getAppContext();
            // Get app info from the manifest
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(), 0);
            infoBundle.putString(INFO_APPPACKAGE, appInfo.packageName);
            infoBundle.putString(INFO_APPVERSIONNAME, appInfo.versionName);
            // versionCode deprecated and new method in API: 28, till then ignore...
            infoBundle.putInt(INFO_APPVERSIONCODE, appInfo.versionCode);
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return new BackupInfo(infoBundle);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeBundle(mBundle);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
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

    public int getCompatArchiver() {
        return mBundle.getInt(INFO_ARCHIVER_COMPATIBLE);
    }

    @Nullable
    public String getAppPackage() {
        return mBundle.getString(INFO_APPPACKAGE);
    }

    @Nullable
    public String getAppVersionName() {
        return mBundle.getString(INFO_APPVERSIONNAME);
    }

    public int getAppVersionCode() {
        return mBundle.getInt(INFO_APPVERSIONCODE);
    }

    /**
     * @return version of the *database* this archive was generated from;
     * or 0 for a database version 81 or earlier
     *
     * @since v200
     */
    public int getDatabaseVersionCode() {
        // 5.2.2
        switch (getAppVersionCode()) {
            case 179:
                return 82;

            default:
                return mBundle.getInt(INFO_DATABASE_VERSION, 0);
        }
    }


    public int getSdk() {
        return mBundle.getInt(INFO_SDK);
    }

    public boolean hasDatabase() {
        return mBundle.getBoolean(INFO_HAS_DATABASE);
    }

    /**
     * We *should* have Preferences in the archive.
     * At least that was the intention when it was created.
     *
     * @return <tt>true</tt> if present
     */
    public boolean hasPreferences() {
        return mBundle.getBoolean(INFO_HAS_PREFERENCES);
    }

    /**
     * We *should* have Styles in the archive.
     * At least that was the intention when it was created.
     *
     * @return <tt>true</tt> if present
     */
    public boolean hasBooklistStyles() {
        return mBundle.getBoolean(INFO_HAS_BOOKLIST_STYLES);
    }

    /**
     * Check if the archive has books (with or without an exact number).
     *
     * @return <tt>true</tt> if books are present
     */
    public boolean hasBooks() {
        return mBundle.getBoolean(INFO_HAS_BOOKS) || hasBookCount();
    }

    /**
     * Check if the archive has a known number of books.
     * Will return <tt>false</tt> if there is no number (or if the number is 0); this does not
     * mean there might not be any books though. Use {@link #hasBooks()} to be sure.
     *
     * @return <tt>true</tt> if the number of books are known
     */
    public boolean hasBookCount() {
        return mBundle.containsKey(INFO_NUMBOOKS) && mBundle.getInt(INFO_NUMBOOKS) > 0;
    }

    public int getBookCount() {
        return mBundle.getInt(INFO_NUMBOOKS);
    }

    /**
     * Check if the archive has covers (with or without an exact number).
     *
     * @return <tt>true</tt> if covers are present
     */
    public boolean hasCovers() {
        return mBundle.getBoolean(INFO_HAS_COVERS) || hasCoverCount();
    }

    /**
     * Check if the archive has a known number of covers.
     * Will return <tt>false</tt> if there is no number (or if the number is 0); this does not
     * mean there might not be any covers though. Use {@link #hasCovers()}} to be sure.
     *
     * @return <tt>true</tt> if the number of books are known
     */
    public boolean hasCoverCount() {
        return mBundle.containsKey(INFO_NUMCOVERS) && mBundle.getInt(INFO_NUMCOVERS) > 0;
    }

    public int getCoverCount() {
        return mBundle.getInt(INFO_NUMCOVERS);
    }

    /**
     * This is partially a debug method and partially a basic check to see if the info
     * block looks more or less correct.
     *
     * @return <tt>true</tt> if valid
     */
    public boolean isValid() {
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, mBundle.toString());
        }
        // extremely simple check: we assume that if one field is present, the rest will be there.
        return mBundle.containsKey(INFO_ARCHIVER_VERSION);
    }

    /**
     * A set of variables to use when creating a new info block.
     * This is cleaner then adding new arguments to the actual {@link #newInstance} method.
     */
    static class InfoUserValues {

        int bookCount;
        int coverCount;
        boolean hasPrefs;
        boolean hasStyles;
    }
}
