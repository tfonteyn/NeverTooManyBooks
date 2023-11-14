/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

/**
 * Class to encapsulate the INFO block from an archive.
 */
public class ArchiveMetaData
        extends BasicMetaData {

    /** Version of archiver used to write this archive. */
    public static final String INFO_ARCHIVER_VERSION = "ArchVersion";
    /** Creation LocalDateTime(local zone) of archive; ISO formatted. */
    private static final String INFO_CREATED_DATE = "CreateDate";
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

    /**
     * Constructor used while <strong>reading</strong> from an Archive.
     *
     * @param data The bundle with the information previously read.
     */
    public ArchiveMetaData(@NonNull final Bundle data) {
        super(data);
    }

    /**
     * Constructor used while <strong>reading</strong> from an Archive.
     * The meta-data will be (re)constructed on-the-fly.
     *
     * @param version explicitly set or override the version of the archive
     * @param data    The bundle with (optional) the information previously read.
     */
    public ArchiveMetaData(final int version,
                           @NonNull final Bundle data) {
        super(data);
        data.putInt(INFO_ARCHIVER_VERSION, version);
    }

    /**
     * Constructor used while <strong>writing</strong> to an Archive.
     *
     * @param context Current context
     * @param version of the archive structure
     * @param result  to add to the header bundle
     *
     * @return new instance
     */
    @NonNull
    public static ArchiveMetaData create(@NonNull final Context context,
                                         final int version,
                                         @NonNull final ExportResults result) {

        final ArchiveMetaData metaData = new ArchiveMetaData(
                ServiceLocator.getInstance().newBundle());

        final Bundle data = metaData.getData();

        // Pure info/debug information
        data.putInt(INFO_ARCHIVER_VERSION, version);
        data.putInt(INFO_DATABASE_VERSION, DBHelper.DATABASE_VERSION);

        final PackageInfoWrapper info = PackageInfoWrapper.create(context);
        data.putString(INFO_APP_PACKAGE, info.getPackageName());
        data.putString(INFO_APP_VERSION_NAME, info.getVersionName());
        data.putLong(INFO_APP_VERSION_CODE, info.getVersionCode());
        data.putInt(INFO_SDK, Build.VERSION.SDK_INT);

        // the actual data the user will care about
        data.putString(INFO_CREATED_DATE, LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (result.getBookCount() > 0) {
            data.putInt(INFO_NUMBER_OF_BOOKS, result.getBookCount());
        }
        if (result.getCoverCount() > 0) {
            data.putInt(INFO_NUMBER_OF_COVERS, result.getCoverCount());
        }
        return metaData;
    }

    /**
     * Get the version of the Archiver that wrote this archive.
     *
     * @return archive version, or {@code 0} if not known.
     */
    public int getArchiveVersion() {
        return getData().getInt(INFO_ARCHIVER_VERSION, 0);
    }

    /**
     * Get the version of the <strong>database</strong> this archive was generated from.
     *
     * @return version, or {@code 0} if not known.
     */
    public int getDatabaseVersionCode() {
        return getData().getInt(INFO_DATABASE_VERSION, 0);
    }

    /**
     * Get the version of the <strong>application</strong> this archive was generated from.
     *
     * @return version, or {@code 0} if not known.
     */
    public long getAppVersionCode() {
        // old archives used an Integer, newer use Long.
        final Object version = getData().get(INFO_APP_VERSION_CODE);
        if (version == null) {
            return 0L;
        } else {
            try {
                return (long) version;
            } catch (@NonNull final ClassCastException e) {
                return 0L;
            }
        }
    }

    /**
     * Get the date from the creation-date field.
     *
     * @param systemLocale to use for ISO date parsing
     *
     * @return Optional LocalDateTime(user local at time of creation)
     */
    @NonNull
    public Optional<LocalDateTime> getCreatedLocalDate(@NonNull final Locale systemLocale) {
        return new ISODateParser(systemLocale).parse(getData().getString(INFO_CREATED_DATE));
    }

    /**
     * This is partially a debug method and partially a basic check to see if the info
     * block looks more or less correct.
     *
     * @param context Current context
     *
     * @throws DataReaderException on failure to recognise a supported archive
     */
    public void validate(@NonNull final Context context)
            throws DataReaderException {
        // extremely simple check: the archiver version field must be present
        if (!getData().containsKey(INFO_ARCHIVER_VERSION)) {
            throw new DataReaderException(context.getString(
                    R.string.error_file_not_recognized));
        }
    }
}
