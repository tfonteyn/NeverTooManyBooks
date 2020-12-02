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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

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
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Implementation of ZIP-specific writer functions.
 * Uses the default format of {@link ArchiveWriterAbstract}
 * <p>
 * Compared to tar file, the results are as expected:
 * - the xml/csv compression is huge
 * - image compression minimal
 */
public class ZipArchiveWriter
        extends ArchiveWriterAbstract
        implements ArchiveWriter.SupportsCovers {

    /** Buffer for {@link #mOutputStream}. */
    private static final int BUFFER_SIZE = 65535;

    /** The output stream for the archive. */
    @NonNull
    private final ZipOutputStream mOutputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws FileNotFoundException on failure
     */
    public ZipArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportHelper helper)
            throws FileNotFoundException {
        super(context, helper);

        mOutputStream = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(helper.getTempOutputFile(context)), BUFFER_SIZE));
    }

    @Override
    public void writeCovers(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        // delegate to the default implementation
        writeCoversDefImpl(context, progressListener);
    }

    @Override
    public void putByteArray(@NonNull final String name,
                             @NonNull final byte[] bytes,
                             final boolean compress)
            throws IOException {

        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(Instant.now().toEpochMilli());
        if (compress) {
            entry.setMethod(ZipEntry.DEFLATED);
        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            final CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            entry.setCrc(crc32.getValue());
        }

        mOutputStream.putNextEntry(entry);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, mOutputStream);
        } finally {
            mOutputStream.closeEntry();
        }
    }

    @Override
    public void putFile(@NonNull final String name,
                        @NonNull final File file,
                        final boolean compress)
            throws IOException {

        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        if (compress) {
            entry.setMethod(ZipEntry.DEFLATED);

        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(file.length());
            entry.setCompressedSize(file.length());
            final CRC32 crc32 = FileUtils.getCrc32(file);
            entry.setCrc(crc32.getValue());
        }

        mOutputStream.putNextEntry(entry);
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, mOutputStream);
        } finally {
            mOutputStream.closeEntry();
        }
    }

    @Override
    public void close()
            throws IOException {
        mOutputStream.close();
        super.close();
    }
}
