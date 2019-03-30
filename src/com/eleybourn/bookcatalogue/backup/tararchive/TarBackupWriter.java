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

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriterAbstract;

/**
 * Implementation of TAR-specific writer functions.
 *
 * @author pjw
 */
public class TarBackupWriter
        extends BackupWriterAbstract {

    @NonNull
    private final TarBackupContainer mContainer;
    @NonNull
    private final TarArchiveOutputStream mOutput;

    /**
     * Constructor.
     *
     * @param context caller context
     * @param container Parent
     */
    TarBackupWriter(@NonNull final Context context,
                    @NonNull final TarBackupContainer container)
            throws IOException {
        super(context);
        mContainer = container;
        // Open the archive for writing
        OutputStream out = new FileOutputStream(container.getFile());
        mOutput = new TarArchiveOutputStream(out);
    }

    @NonNull
    @Override
    public BackupContainer getContainer() {
        return mContainer;
    }

    @Override
    public void putInfo(@NonNull final byte[] bytes)
            throws IOException {
        putByteArray(TarBackupContainer.INFO_FILE, bytes);
    }

    @Override
    public void putBooklistStyles(@NonNull final byte[] bytes)
            throws IOException {
        putByteArray(TarBackupContainer.STYLES, bytes);
    }

    @Override
    public void putPreferences(@NonNull final byte[] bytes)
            throws IOException {
        putByteArray(TarBackupContainer.PREFERENCES, bytes);
    }

    @Override
    public void putBooks(@NonNull final File file)
            throws IOException {
        putFile(TarBackupContainer.BOOKS_FILE, file);
    }

    @Override
    public void putXmlData(@NonNull final File file)
            throws IOException {
        putFile(TarBackupContainer.XML_DATA, file);
    }

    /**
     * Write a generic file to the archive.
     *
     * @param name of the entry in the archive
     * @param file actual file to store in the archive
     */
    @Override
    public void putFile(@NonNull final String name,
                        @NonNull final File file)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(new File(name));
        entry.setModTime(file.lastModified());
        entry.setSize(file.length());
        mOutput.putArchiveEntry(entry);
        final InputStream in = new FileInputStream(file);
        streamToArchive(in);
    }

    /**
     * Write a generic byte array to the archive.
     *
     * @param name  of the entry in the archive
     * @param bytes bytes to write
     */
    private void putByteArray(@NonNull final String name,
                              @NonNull final byte[] bytes)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        mOutput.putArchiveEntry(entry);
        final InputStream in = new ByteArrayInputStream(bytes);
        streamToArchive(in);
    }

    /**
     * Sends the contents of a stream to the current archive entry.
     *
     * @param in Stream to be written to the archive; will be closed when done
     */
    private void streamToArchive(@NonNull final InputStream in)
            throws IOException {
        try {
            final byte[] buffer = new byte[TarBackupContainer.BUFFER_SIZE];
            while (true) {
                int cnt = in.read(buffer);
                if (cnt <= 0) {
                    break;
                }
                mOutput.write(buffer, 0, cnt);
            }
        } finally {
            in.close();
            mOutput.closeArchiveEntry();
        }
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        super.close();
        mOutput.close();
    }
}
