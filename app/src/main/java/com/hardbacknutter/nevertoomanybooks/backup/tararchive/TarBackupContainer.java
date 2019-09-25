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
package com.hardbacknutter.nevertoomanybooks.backup.tararchive;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupContainer;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.InvalidArchiveException;

/**
 * Class to handle TAR archive storage.
 * <p>
 * TAR files have some limitations: no application-defined metadata can be stored with
 * the files, the index is at the start, so it helps to know the entity size before it
 * is written, and they usually do not support random access.
 * <p>
 * So we:
 * <p>
 * - use "file names" to encode special meaning (e.g. "books*.csv" is always an export file).
 * - use intermediate temp files so we can figure out sizes
 * <p>
 * {@link #getVersion()}
 * #1: original code, used serialized styles and flat xml files for info/prefs
 * <p>
 * #2: writes new xml format supporting lists of elements, styles are xml as wel now.
 * Can still read #1 archives
 */
public class TarBackupContainer
        implements BackupContainer {

    /** Buffer size for buffered streams. */
    static final int BUFFER_SIZE = 32768;
    /** Always first entry; Used in the storage and identification of data store in TAR file. */
    static final String INFO_FILE = "INFO.xml";
    /** Used in the storage and identification of data store in TAR file. */
    static final Pattern INFO_PATTERN =
            Pattern.compile("^INFO_.*\\.xml$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Used in the storage and identification of data store in TAR file. */
    static final String BOOKS_FILE = "books.csv";
    /** Used in the storage and identification of data store in TAR file. */
    static final Pattern BOOKS_PATTERN =
            Pattern.compile("^books_.*\\.csv$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Used in the storage and identification of data store in TAR file. */
    static final String DB_FILE = "snapshot.db";
    /** Used in the storage and identification of data store in TAR file. */
    static final String PREFERENCES = "preferences.xml";
    /** Used in the storage and identification of data store in TAR file. */
    static final String STYLES = "styles.xml";
    /**
     * archive entry that will contain xml dumps of actual tables.
     * For now, this is export only, cannot import yet.
     * Meant for those who want to experiment with the data on a desktop/server
     * without the need to parse csv strings
     */
    static final String XML_DATA = "data.xml";
    /** archives are written in this version. */
    private static final int VERSION_WRITTEN = 2;
    /** we can still read archives from this version and up to our current version. */
    private static final int VERSION_READ = 1;
    /** Uri to read from, or write to. */
    @NonNull
    private final Uri mUri;

    /**
     * Constructor.
     *
     * @param uri to read from or write to.
     */
    public TarBackupContainer(@NonNull final Uri uri) {
        mUri = uri;
    }

    @NonNull
    public Uri getUri() {
        return mUri;
    }

    @Override
    @NonNull
    public BackupReader newReader(@NonNull final Context context) {
        return new TarBackupReader(context, this);
    }

    @Override
    @NonNull
    public BackupWriter newWriter(@NonNull final Context context)
            throws IOException {
        return new TarBackupWriter(context, this);
    }

    /**
     * We always write the latest version archives (no backwards compatibility).
     * Not valid during reading.
     */
    @Override
    public int getVersion() {
        return VERSION_WRITTEN;
    }

    @Override
    public int canReadVersion() {
        return VERSION_READ;
    }

    @Override
    public void validate(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        // The reader will do basic validation.
        try (BackupReader reader = newReader(context)) {
            BackupInfo backupInfo = reader.getInfo();
            // the info block will/can do more checks.
            backupInfo.validate();
        }
    }
}
