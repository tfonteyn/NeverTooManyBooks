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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupContainer;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriterAbstract;

/**
 * Implementation of TAR-specific writer functions.
 */
public class TarBackupWriter
        extends BackupWriterAbstract {

    /** The archive container. */
    @NonNull
    private final BackupContainer mContainer;
    /** The data stream for the archive. */
    @NonNull
    private final TarArchiveOutputStream mOutputStream;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param container The archive container.
     *
     * @throws IOException on failure
     */
    TarBackupWriter(@NonNull final Context context,
                    @NonNull final BackupContainer container)
            throws IOException {
        super(context);
        mContainer = container;

        OutputStream os = context.getContentResolver().openOutputStream(mContainer.getUri());
        mOutputStream = new TarArchiveOutputStream(os);
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
     *
     * @throws IOException on failure
     */
    @Override
    public void putFile(@NonNull final String name,
                        @NonNull final File file)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(new File(name));
        entry.setModTime(file.lastModified());
        entry.setSize(file.length());
        mOutputStream.putArchiveEntry(entry);
        final InputStream is = new FileInputStream(file);
        streamToArchive(is);
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

    /**
     * Write a generic byte array to the archive.
     *
     * @param name  of the entry in the archive
     * @param bytes bytes to write
     *
     * @throws IOException on failure
     */
    private void putByteArray(@NonNull final String name,
                              @NonNull final byte[] bytes)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        mOutputStream.putArchiveEntry(entry);
        final InputStream is = new ByteArrayInputStream(bytes);
        streamToArchive(is);
    }

    /**
     * Sends the contents of a stream to the current archive entry.
     *
     * @param is Stream to be written to the archive; the stream will be closed when done
     *
     * @throws IOException on failure
     */
    private void streamToArchive(@NonNull final InputStream is)
            throws IOException {
        try {
            final byte[] buffer = new byte[TarBackupContainer.BUFFER_SIZE];
            while (true) {
                int cnt = is.read(buffer);
                if (cnt <= 0) {
                    break;
                }
                mOutputStream.write(buffer, 0, cnt);
            }
        } finally {
            is.close();
            mOutputStream.closeArchiveEntry();
        }
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        mOutputStream.close();
        super.close();
    }
}
