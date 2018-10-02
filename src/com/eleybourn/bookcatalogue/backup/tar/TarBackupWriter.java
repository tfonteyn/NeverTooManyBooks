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

import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.backup.BackupContainer;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupUtils;
import com.eleybourn.bookcatalogue.backup.BackupWriterAbstract;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

/**
 * Implementation of TAR-specific writer functions
 *
 * @author pjw
 */
public class TarBackupWriter extends BackupWriterAbstract {
    private final TarBackupContainer mContainer;
    private final TarArchiveOutputStream mOutput;
    private int mStyleCounter = 0;

    /**
     * Constructor
     *
     * @param container Parent
     */
    TarBackupWriter(@NonNull final TarBackupContainer container) throws IOException {
        mContainer = container;
        // Open the archive for writing
        FileOutputStream out = new FileOutputStream(container.getFile());
        mOutput = new TarArchiveOutputStream(out);
    }

    @Override
    public BackupContainer getContainer() {
        return mContainer;
    }


    /**
     * Save the books export file
     */
    @Override
    public void putBooks(@NonNull final File books) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(new File(TarBackupContainer.BOOKS_FILE));
        entry.setModTime(books.lastModified());
        entry.setSize(books.length());
        mOutput.putArchiveEntry(entry);
        FileInputStream in = new FileInputStream(books);
        streamToArchive(in);
    }

    /**
     * Save a cover file
     */
    @Override
    public void putCoverFile(@NonNull final File source) throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(source.getName());
        entry.setModTime(source.lastModified());
        entry.setSize(source.length());
        mOutput.putArchiveEntry(entry);
        final FileInputStream in = new FileInputStream(source);
        streamToArchive(in);
    }

    /**
     * Save the INFO data
     */
    @Override
    public void putInfo(@NonNull final BackupInfo info) throws IOException {
        final ByteArrayOutputStream infoData = new ByteArrayOutputStream();
        final BufferedWriter infoOut = new BufferedWriter(new OutputStreamWriter(infoData, TarBackupContainer.UTF8), TarBackupContainer.BUFFER_SIZE);
        BackupUtils.bundleToXml(infoOut, info.getBundle());
        infoOut.close();
        bytesToArchive(TarBackupContainer.INFO_FILE, infoData.toByteArray());
    }

    /**
     * Save a Booklist style. We save them with increasing suffix counters to ensure uniqueness
     */
    @Override
    public void putBooklistStyle(@NonNull final BooklistStyle style) throws IOException {
        // Turn the object into a byte array
        final byte[] blob = SerializationUtils.serializeObject(style);
        if (blob == null) {
            Logger.logError("serializeObject failed?");
            throw new IllegalStateException("serializeObject failed?");
        }
        mStyleCounter++;
        bytesToArchive(TarBackupContainer.STYLE_PREFIX + mStyleCounter, blob);
    }

    /**
     * Save the preferences.
     * <p>
     * It would be nice to support groups (ie. more than one preference name), but ... we don't need it.
     */
    @Override
    public void putPreferences(@NonNull final SharedPreferences prefs) throws IOException {
        // Turn the preferences into an XML file in a byte array
        final ByteArrayOutputStream infoData = new ByteArrayOutputStream();
        final BufferedWriter infoOut = new BufferedWriter(new OutputStreamWriter(infoData, TarBackupContainer.UTF8), TarBackupContainer.BUFFER_SIZE);
        BackupUtils.preferencesToXml(infoOut, prefs);
        infoOut.close();
        // Save them
        bytesToArchive(TarBackupContainer.PREFERENCES, infoData.toByteArray());
    }

    /**
     * Utility routine to send the contents of a stream to the current archive entry
     *
     * @param in Stream to be saved
     */
    private void streamToArchive(@NonNull final InputStream in) throws IOException {
        try {
            final byte[] buffer = new byte[TarBackupContainer.BUFFER_SIZE];
            while (true) {
                int cnt = in.read(buffer);
                if (cnt <= 0)
                    break;
                mOutput.write(buffer, 0, cnt);
            }
        } finally {
            in.close();
            mOutput.closeArchiveEntry();
        }
    }

    /**
     * Utility routine to save the passed bytes to an entry with the passed name
     *
     * @param name  name of "file" in archive
     * @param bytes bytes to write
     */
    private void bytesToArchive(@NonNull final String name, @NonNull final byte[] bytes) throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        mOutput.putArchiveEntry(entry);
        final InputStream in = new ByteArrayInputStream(bytes);
        streamToArchive(in);
    }

    @Override
    public void close() throws IOException {
        super.close();
        mOutput.close();
    }
}
