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
package com.eleybourn.bookcatalogue.backup.tar;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupReaderAbstract;
import com.eleybourn.bookcatalogue.backup.ReaderEntity;
import com.eleybourn.bookcatalogue.backup.ReaderEntity.BackupEntityType;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Implementation of TAR-specific reader functions
 *
 * @author pjw
 */
public class TarBackupReader extends BackupReaderAbstract {
    /** The data stream for the archive */
    @NonNull
    private final TarArchiveInputStream mInput;
    /** The INFO data read from the start of the archive */
    @NonNull
    private final BackupInfo mInfo;
    /** Used to allow 'peeking' at the input stream */
    @Nullable
    private ReaderEntity mPushedEntity;

    /**
     * Constructor
     *
     * @param container Parent
     */
    TarBackupReader(final @NonNull TarBackupContainer container) throws IOException {
        super(container.getContext());

        // Open the file and create the archive stream
        final FileInputStream in = new FileInputStream(container.getFile());
        mInput = new TarArchiveInputStream(in);

        // Process the INFO entry. Should be first.
        ReaderEntity info = nextEntity();
        if (info == null || info.getType() != BackupEntityType.Info)
            throw new IOException("Not a valid backup");

        // Save the INFO
        mInfo = new BackupInfo(info.getBundle());

        // Skip any following INFOs. Later versions may store more.
        while (info != null && info.getType() == BackupEntityType.Info) {
            info = nextEntity();
        }
        // Save the 'peeked' entity
        mPushedEntity = info;
    }

    /**
     * Get the next entity (allowing for peeking).
     *
     * @return the next entity found.
     */
    @Override
    @Nullable
    public ReaderEntity nextEntity() throws IOException {

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
        return new TarReaderEntity(this, entry, type);

    }

    /**
     * @return the TarArchiveEntry type. However, {@link BackupEntityType#Cover} is returned for
     *         *all* files which are not actually recognised.
     */
    @NonNull
    private BackupEntityType getBackupEntityType(final @NonNull TarArchiveEntry entry) {
        String name = entry.getName();

        if (name.equalsIgnoreCase(TarBackupContainer.BOOKS_FILE)
                ||TarBackupContainer.BOOKS_PATTERN.matcher(name).find()) {
            return BackupEntityType.Books;

        } else if (name.equalsIgnoreCase(TarBackupContainer.INFO_FILE)
                || TarBackupContainer.INFO_PATTERN.matcher(name).find()) {
            return  BackupEntityType.Info;

        } else if (name.toLowerCase().endsWith(".xml")) {
            return  BackupEntityType.XML;

        } else if (name.equalsIgnoreCase(TarBackupContainer.DB_FILE)) {
            return  BackupEntityType.Database;

        } else if (TarBackupContainer.STYLE_PATTERN.matcher(name).find()) {
            return BackupEntityType.BooklistStyle;

        } else if (name.equalsIgnoreCase(TarBackupContainer.PREFERENCES)) {
            return BackupEntityType.Preferences;

//        } else if (name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png")) {
//            return BackupEntityType.Cover;

        } else {
            // any not recognised file is considered a cover file.
            return BackupEntityType.Cover;
        }
    }

    /**
     * Accessor used by TarEntityReader to get access to the stream data
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
    public void close() throws IOException {
        super.close();
        mInput.close();
    }
}
