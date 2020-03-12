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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.tararchive.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tararchive.TarArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Encapsulates access to the actual reader/writer used for backup/restore.
 */
public final class ArchiveManager {

    /** Potential backup archive entry. */
    static final String BOOKS_FILE = "books.csv";
    /** Used in the storage and identification of data store. */
    static final Pattern BOOKS_PATTERN =
            Pattern.compile("^books_.*\\.csv$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Potential backup archive entry. */
    static final String PREFERENCES = "preferences.xml";
    /** Potential backup archive entry. */
    static final String STYLES = "styles.xml";

    /**
     * archive entry that will contain xml dumps of actual tables.
     * For now, this is export only, cannot import yet.
     * Meant for those who want to read the data on a desktop/server
     * without the need to parse csv strings
     */
    static final String XML_DATA = "data.xml";

    /** LEGACY - Potential backup archive entry. A copy of the main database file. */
    static final String DB_FILE = "snapshot.db";

    /** Log tag. */
    private static final String TAG = "BackupManager";
    /** Proposed extension for backup files. Not mandatory and not significant to the content. */
    private static final String ARCHIVE_EXTENSION = ".ntmb";

    /** Constructor. */
    private ArchiveManager() {
    }

    /**
     * Create a BackupReader for the specified Uri.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    public static ArchiveReader getReader(@NonNull final Context context,
                                          @NonNull final ImportHelper helper)
            throws InvalidArchiveException, IOException {

        // TODO: we should detect the type of uri to choose a reader
        // for now, we only support tar files.
        ArchiveReader reader = new TarArchiveReader(context, helper);
        reader.validate(context);
        return reader;
    }

    /**
     * Create a BackupWriter for the specified Uri.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    @NonNull
    public static ArchiveWriter getWriter(@NonNull final Context context,
                                          @NonNull final ExportHelper helper)
            throws IOException {

        // for now, we only support tar files.
        return new TarArchiveWriter(context, helper);
    }

    public static String getDefaultBackupFileName(@NonNull final Context context) {
        return context.getString(R.string.app_name) + '-'
               + DateUtils.localSqlDateForToday()
                          .replace(" ", "-")
                          .replace(":", "")
               + ARCHIVE_EXTENSION;
    }

    /**
     * Get the info bundle for the passed Uri.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @return the info bundle, or {@code null} on failure
     */
    @Nullable
    public static ArchiveInfo getInfo(@NonNull final Context context,
                                      @NonNull final ImportHelper helper) {
        try (ArchiveReader reader = getReader(context, helper)) {
            ArchiveInfo info = reader.getInfo();
            reader.close();
            return info;
        } catch (@NonNull final IOException | InvalidArchiveException e) {
            // InvalidArchiveException is irrelevant here, as we would not have gotten this far
            Logger.error(context, TAG, e);
        }
        return null;
    }
}
