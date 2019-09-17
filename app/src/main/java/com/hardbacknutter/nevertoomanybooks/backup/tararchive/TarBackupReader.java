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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupContainer;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.ReaderEntity.Type;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.ReaderEntityAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Implementation of TAR-specific reader functions.
 * <p>
 * Peeking is no longer used, but leaving it in for now.
 */
public class TarBackupReader
        extends BackupReaderAbstract {

    private static final Pattern LEGACY_STYLES_PATTERN =
            Pattern.compile('^' + "style.blob." + "[0-9]*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** The archive container. */
    @NonNull
    private final BackupContainer mContainer;
    /** The data stream for the archive. */
    @Nullable
    private TarArchiveInputStream mInputStream;

    /** The INFO data read from the start of the archive. */
    @Nullable
    private BackupInfo mInfo;
    /** Used to allow 'peeking' at the input stream. */
    @Nullable
    private ReaderEntity mPushedEntity;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param container The archive container.
     */
    TarBackupReader(@NonNull final Context context,
                    @NonNull final BackupContainer container) {
        super(context);
        mContainer = container;
    }

    @Override
    @Nullable
    public ReaderEntity findEntity(@NonNull final Type type)
            throws IOException {

        TarArchiveEntry entry;
        while (true) {
            entry = getInputStream().getNextTarEntry();
            if (entry == null) {
                return null;
            }

            // Based on the file name, determine entity type
            Type found = getBackupEntityType(entry);
            if (type.equals(found)) {
                return new TarBackupReaderEntity(type, this, entry);
            }
        }
    }

    @Override
    @Nullable
    public ReaderEntity nextEntity()
            throws IOException {

        if (mPushedEntity != null) {
            ReaderEntity e = mPushedEntity;
            mPushedEntity = null;
            return e;
        }

        TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return null;
        }

        Type type = getBackupEntityType(entry);
        return new TarBackupReaderEntity(type, this, entry);
    }

    @NonNull
    @Override
    public BackupInfo getInfo()
            throws IOException {
        if (mInfo == null) {
            // Find and process the INFO entry.
            ReaderEntity entity = findEntity(Type.Info);
            if (entity == null) {
                throw new IOException("Not a valid backup; no INFO entity found");
            }
            // read the INFO
            mInfo = new BackupInfo();
            try (XmlImporter importer = new XmlImporter()) {
                importer.doBackupInfoBlock(entity, mInfo);
            }
            // We MUST close the stream here, so the caller gets a pristine stream.
            reset();
        }
        return mInfo;
    }

    /**
     * Detect the type of the passed entry.
     *
     * @param entry to get the type of
     *
     * @return the TarArchiveEntry type.
     */
    @NonNull
    private Type getBackupEntityType(@NonNull final TarArchiveEntry entry) {
        String name = entry.getName().toLowerCase(App.getSystemLocale());

        // check covers first, as we will have many
        if (name.endsWith(".jpg") || name.endsWith(".png")) {
            return Type.Cover;

        } else if (TarBackupContainer.INFO_FILE.equalsIgnoreCase(name)
                   || TarBackupContainer.INFO_PATTERN.matcher(name).find()) {
            return Type.Info;

        } else if (TarBackupContainer.BOOKS_FILE.equalsIgnoreCase(name)
                   || TarBackupContainer.BOOKS_PATTERN.matcher(name).find()) {
            return Type.Books;

        } else if (TarBackupContainer.PREFERENCES.equalsIgnoreCase(name)) {
            return Type.Preferences;

        } else if (TarBackupContainer.STYLES.equalsIgnoreCase(name)) {
            return Type.BooklistStyles;

        } else if (TarBackupContainer.DB_FILE.equalsIgnoreCase(name)) {
            return Type.Database;

            // pre-v200
        } else if (LEGACY_STYLES_PATTERN.matcher(name).find()) {
            return Type.BooklistStylesPreV200;

            // pre-v200
        } else if ("preferences".equals(name)) {
            return Type.PreferencesPreV200;

            // needs to be below any specific xml files.
        } else if (name.endsWith(".xml")) {
            return Type.XML;

        } else {
            Logger.info(this, "getBackupEntityType",
                        "Unknown file in archive: " + entry.getName());
            return Type.Unknown;
        }
    }

    /**
     * Get the input stream; (re)creating as needed.
     *
     * <strong>Note:</strong> we don't access mInputStream directly for reading,
     * as other code can set it to null when needed.
     *
     * @return the stream
     *
     * @throws IOException on failure
     */
    @NonNull
    private TarArchiveInputStream getInputStream()
            throws IOException {
        if (mInputStream == null) {
            InputStream is = App.getAppContext().getContentResolver()
                                .openInputStream(mContainer.getUri());
            if (is == null) {
                throw new IOException("InputStream was NULL");
            }
            mInputStream = new TarArchiveInputStream(is);
        }

        return mInputStream;
    }

    @Override
    public void reset()
            throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        reset();
        super.close();
    }

    /**
     * Implementation of TAR-specific {@link ReaderEntity} functions.
     */
    private static class TarBackupReaderEntity
            extends ReaderEntityAbstract {

        @NonNull
        private final TarBackupReader mReader;
        @NonNull
        private final TarArchiveEntry mEntry;

        /**
         * Constructor.
         *
         * @param type   Type of item
         * @param reader Parent
         * @param entry  Corresponding archive entry
         */
        TarBackupReaderEntity(@NonNull final Type type,
                              @NonNull final TarBackupReader reader,
                              @NonNull final TarArchiveEntry entry) {
            super(type);
            mReader = reader;
            mEntry = entry;
        }

        @NonNull
        @Override
        public String getName() {
            return mEntry.getName();
        }

        @NonNull
        @Override
        public Date getDateModified() {
            return mEntry.getLastModifiedDate();
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws IOException {
            return mReader.getInputStream();
        }
    }
}
