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
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class ArchiveInfo
        implements Parcelable {

    public static final Creator<ArchiveInfo> CREATOR = new Creator<ArchiveInfo>() {
        @Override
        public ArchiveInfo createFromParcel(@NonNull final Parcel in) {
            return new ArchiveInfo(in);
        }

        @Override
        public ArchiveInfo[] newArray(final int size) {
            return new ArchiveInfo[size];
        }
    };
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
    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mInfo;

    /**
     * Constructor used while reading from an Archive.
     */
    public ArchiveInfo() {
        mInfo = new Bundle();
    }

    /**
     * Constructor used while writing to an Archive.
     *
     * @param context Current context
     * @param version of the archive structure
     */
    public ArchiveInfo(@NonNull final Context context,
                       final int version) {
        mInfo = new Bundle();

        mInfo.putInt(INFO_ARCHIVER_VERSION, version);
        mInfo.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);
        mInfo.putString(INFO_CREATION_DATE,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        final PackageInfoWrapper info = PackageInfoWrapper.create(context);
        mInfo.putString(INFO_APP_PACKAGE, info.getPackageName());
        mInfo.putString(INFO_APP_VERSION_NAME, info.getVersionName());
        mInfo.putLong(INFO_APP_VERSION_CODE, info.getVersionCode());
        mInfo.putInt(INFO_SDK, Build.VERSION.SDK_INT);
    }

    /**
     * Constructor. Used when reading JSON info from an archive.
     */
    public ArchiveInfo(@NonNull final JSONObject from)
            throws IOException {
        try {
            mInfo = new BundleCoder().decode(from);
        } catch (@NonNull final JSONException e) {
            throw new IOException(e);
        }
    }

    protected ArchiveInfo(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mInfo = in.readBundle(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeBundle(mInfo);
    }

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

    public void setDatabaseVersionCode(final int version) {
        mInfo.putInt(INFO_DATABASE_VERSION, version);
    }

    /**
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any books though.
     *
     * @return {@code true} if the number of books is known
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
     * @return {@code true} if the number of books is known
     */
    boolean hasCoverCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_COVERS)
               && mInfo.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mInfo.getInt(INFO_NUMBER_OF_COVERS);
    }

    void setCoverCount(final int count) {
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
            throw new InvalidArchiveException("Missing version information");
        }
    }

    /**
     * Covert to json.
     *
     * @return a JSON object
     *
     * @throws IOException on failure
     */
    @NonNull
    JSONObject toJson()
            throws IOException {
        try {
            return new BundleCoder().encode(mInfo);
        } catch (@NonNull final JSONException e) {
            throw new IOException(e);
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
