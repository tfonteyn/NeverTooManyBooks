/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;

/**
 * Implementation of ZIP-specific writer functions.
 */
public class ZipArchiveWriter
        extends ArchiveWriterAbstract
        implements ArchiveWriterAbstract.SupportsCovers {

    /** The output stream for the archive. */
    @NonNull
    private final ZipOutputStream zipOutputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws FileNotFoundException on ...
     */
    public ZipArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportHelper helper)
            throws FileNotFoundException {
        super(helper);

        zipOutputStream = new ZipOutputStream(new BufferedOutputStream(
                helper.createOutputStream(context), RecordWriter.BUFFER_SIZE));
    }

    @NonNull
    @Override
    public RecordEncoding getEncoding(@NonNull final RecordType recordType) {
        switch (recordType) {
            case MetaData:
            case Styles:
            case Preferences:
            case Certificates:
            case Bookshelves:
            case CalibreLibraries:
            case CalibreCustomFields:
            case Books:
            case AutoDetect:
                return RecordEncoding.Json;
            case Cover:
                return RecordEncoding.Cover;

            case Database:
            default:
                throw new IllegalArgumentException(recordType.toString());
        }
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

        zipOutputStream.putNextEntry(entry);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    public void putFile(@NonNull final String name,
                        @NonNull final File file,
                        final boolean compress)
            throws IOException {

        final BasicFileAttributes fileAttr =
                Files.readAttributes(file.toPath(), BasicFileAttributes.class);

        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(fileAttr.lastModifiedTime().toMillis());
        if (compress) {
            entry.setMethod(ZipEntry.DEFLATED);

        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(fileAttr.size());
            entry.setCompressedSize(fileAttr.size());
            final CRC32 crc32 = FileUtils.getCrc32(file);
            entry.setCrc(crc32.getValue());
        }

        zipOutputStream.putNextEntry(entry);
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    @Override
    public void close()
            throws IOException {
        zipOutputStream.close();
        super.close();
    }
}
