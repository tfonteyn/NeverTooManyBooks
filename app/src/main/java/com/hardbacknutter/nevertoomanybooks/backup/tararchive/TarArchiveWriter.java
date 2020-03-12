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
package com.hardbacknutter.nevertoomanybooks.backup.tararchive;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;

/**
 * Implementation of TAR-specific writer functions.
 */
public class TarArchiveWriter
        extends ArchiveWriterAbstract {

    /** Buffer size for buffered streams. */
    private static final int BUFFER_SIZE = 32768;

    /** The data stream for the archive. */
    @NonNull
    private final TarArchiveOutputStream mOutputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws IOException on failure
     */
    public TarArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportHelper helper)
            throws IOException {
        super(context, helper);

        OutputStream os = context.getContentResolver().openOutputStream(helper.getUri());
        mOutputStream = new TarArchiveOutputStream(os);
    }

    /**
     * Tar files don't support meta-data, so we just write the info to an xml file.
     *
     * @param archiveInfo info about what we're writing to this archive
     *
     * @throws IOException on failure
     */
    @Override
    public void putInfo(@NonNull final ArchiveInfo archiveInfo)
            throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doBackupInfoBlock(out, archiveInfo);
        }

        putByteArray(TarArchiveReader.INFO_FILE, data.toByteArray());
    }

    @Override
    public void putFile(@NonNull final String name,
                        @NonNull final File file)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(new File(name));
        entry.setModTime(file.lastModified());
        entry.setSize(file.length());
        mOutputStream.putArchiveEntry(entry);
        try (InputStream is = new FileInputStream(file)) {
            streamToArchive(is);
        }
    }

    @Override
    public void putByteArray(@NonNull final String name,
                             @NonNull final byte[] bytes)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        mOutputStream.putArchiveEntry(entry);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            streamToArchive(is);
        }
    }

    /**
     * Sends the contents of a stream to the current archive entry.
     *
     * @param is Stream to be written to the archive
     *
     * @throws IOException on failure
     */
    private void streamToArchive(@NonNull final InputStream is)
            throws IOException {
        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                int cnt = is.read(buffer);
                if (cnt <= 0) {
                    break;
                }
                mOutputStream.write(buffer, 0, cnt);
            }
        } finally {
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
