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
package com.eleybourn.bookcatalogue.backup.tararchive;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReaderAbstract;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntity;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntity.BackupEntityType;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntityAbstract;
import com.eleybourn.bookcatalogue.backup.xml.XmlImporter;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Implementation of TAR-specific reader functions.
 *
 * @author pjw
 */
public class TarBackupReader
        extends BackupReaderAbstract {

    /** The data stream for the archive. */
    @NonNull
    private final TarArchiveInputStream mInput;
    /** The INFO data read from the start of the archive. */
    @NonNull
    private final BackupInfo mInfo;
    /** Used to allow 'peeking' at the input stream. */
    @Nullable
    private ReaderEntity mPushedEntity;

    /**
     * Constructor.
     *
     * @param container Parent
     */
    TarBackupReader(@NonNull final TarBackupContainer container)
            throws IOException {

        // Open the file and create the archive stream
        final FileInputStream in = new FileInputStream(container.getFile());
        mInput = new TarArchiveInputStream(in);

        // Process the INFO entry. Should be first.
        ReaderEntity entity = nextEntity();
        if (entity == null || entity.getType() != BackupEntityType.Info) {
            throw new IOException("Not a valid backup");
        }

        // read the INFO
        mInfo = new BackupInfo();
        try (XmlImporter importer = new XmlImporter()) {
            importer.doBackupInfoBlock(entity, mInfo);
        }

        // Skip any following INFOs. Later versions may store more.
        while (entity != null && entity.getType() == BackupEntityType.Info) {
            entity = nextEntity();
        }
        // Save the 'peeked' entity
        mPushedEntity = entity;
    }

    /**
     * Get the next entity (allowing for peeking).
     *
     * @return the next entity found.
     */
    @Override
    @Nullable
    public ReaderEntity nextEntity()
            throws IOException {

        if (mPushedEntity != null) {
            ReaderEntity e = mPushedEntity;
            mPushedEntity = null;
            return e;
        }

        TarArchiveEntry entry = mInput.getNextTarEntry();
        if (entry == null) {
            return null;
        }

        // Based on the file name, determine entity type
        BackupEntityType type = getBackupEntityType(entry);

        // Create entity
        return new TarBackupReaderEntity(type, this, entry);

    }

    /**
     * @return the TarArchiveEntry type.
     */
    @NonNull
    private BackupEntityType getBackupEntityType(@NonNull final TarArchiveEntry entry) {
        String name = entry.getName().toLowerCase();

        // check covers first, as we will have many
        if (name.endsWith(".jpg") || name.endsWith(".png")) {
            return BackupEntityType.Cover;

        } else if (TarBackupContainer.INFO_FILE.equalsIgnoreCase(name)
                || TarBackupContainer.INFO_PATTERN.matcher(name).find()) {
            return BackupEntityType.Info;

        } else if (TarBackupContainer.BOOKS_FILE.equalsIgnoreCase(name)
                || TarBackupContainer.BOOKS_PATTERN.matcher(name).find()) {
            return BackupEntityType.Books;

        } else if (TarBackupContainer.PREFERENCES.equalsIgnoreCase(name)) {
            return BackupEntityType.Preferences;

        } else if (TarBackupContainer.STYLES.equalsIgnoreCase(name)) {
            return BackupEntityType.BooklistStyles;

        } else if (TarBackupContainer.DB_FILE.equalsIgnoreCase(name)) {
            return BackupEntityType.Database;

            // pre-v200
        } else if (Pattern.compile('^' + "style.blob." + "[0-9]*$",
                                   Pattern.CASE_INSENSITIVE).matcher(name).find()) {
            return BackupEntityType.BooklistStylesPreV200;

            // pre-v200
        } else if ("preferences".equals(name)) {
            return BackupEntityType.PreferencesPreV200;

            // needs to be below any specific xml files.
        } else if (name.endsWith(".xml")) {
            return BackupEntityType.XML;

        } else {
            Logger.info(this, "Unknown file in archive: " + entry.getName());
            return BackupEntityType.Unknown;
        }
    }

    /**
     * Accessor used by TarEntityReader to get access to the stream data.
     */
    @NonNull
    protected TarArchiveInputStream getInput() {
        return mInput;
    }

    @NonNull
    @Override
    public BackupInfo getInfo() {
        return mInfo;
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        super.close();
        mInput.close();
    }

    /**
     * Implementation of TAR-specific ReaderEntity functions. Not much to do.
     *
     * @author pjw
     */
    public static class TarBackupReaderEntity
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
        TarBackupReaderEntity(@NonNull final BackupEntityType type,
                              @NonNull final TarBackupReader reader,
                              @NonNull final TarArchiveEntry entry) {
            super(type);
            mReader = reader;
            mEntry = entry;
        }

        /**
         * @return the original "file name" of the object.
         */
        @NonNull
        @Override
        public String getName() {
            return mEntry.getName();
        }

        /**
         * @return the 'modified' date from archive entry
         */
        @NonNull
        @Override
        public Date getDateModified() {
            return mEntry.getLastModifiedDate();
        }

        /**
         * @return the stream to read the entity
         */
        @NonNull
        @Override
        public InputStream getStream() {
            return mReader.getInput();
        }
    }
}
