/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.tar;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Implementation of TAR-specific writer functions.
 * Uses the default format of {@link ArchiveWriterAbstract}
 *
 * @deprecated we should remove writing to a tar archive (but keep support for reading)
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class TarArchiveWriter
        extends ArchiveWriterAbstract
        implements ArchiveWriter.SupportsCovers {

    /** Buffer for {@link #mOutputStream}. */
    private static final int BUFFER_SIZE = 65535;

    /** The output stream for the archive. */
    @NonNull
    private final TarArchiveOutputStream mOutputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws FileNotFoundException on failure
     */
    public TarArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportHelper helper)
            throws FileNotFoundException {
        super(context, helper);

        mOutputStream = new TarArchiveOutputStream(new BufferedOutputStream(
                new FileOutputStream(helper.getTempOutputFile(context)),
                BUFFER_SIZE));
    }

    @Override
    public void writeCovers(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        defWriteCovers(context, progressListener);
    }

    /**
     * Add a File to the archive. Supports text files only.
     *
     * @param name     for the entry;  allows easier overriding of the file name
     * @param file     to store in the archive
     * @param compress ignored
     *
     * @throws IOException on failure
     */
    @Override
    public void putFile(@NonNull final String name,
                        @NonNull final File file,
                        final boolean compress)
            throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(new File(name));
        entry.setModTime(file.lastModified());
        entry.setSize(file.length());
        mOutputStream.putArchiveEntry(entry);
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, mOutputStream);
        } finally {
            mOutputStream.closeArchiveEntry();
        }
    }

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress ignored
     *
     * @throws IOException on failure
     */
    @Override
    public void putByteArray(@NonNull final String name,
                             @NonNull final byte[] bytes,
                             final boolean compress)
            throws IOException {

        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setModTime(Instant.now().toEpochMilli());
        entry.setSize(bytes.length);
        mOutputStream.putArchiveEntry(entry);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, mOutputStream);
        } finally {
            mOutputStream.closeArchiveEntry();
        }
    }

    @Override
    public void close()
            throws IOException {
        mOutputStream.close();
        super.close();
    }
}
