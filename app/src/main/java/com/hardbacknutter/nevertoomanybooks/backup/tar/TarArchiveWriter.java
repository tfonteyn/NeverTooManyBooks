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
package com.hardbacknutter.nevertoomanybooks.backup.tar;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstractBase;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Implementation of TAR-specific writer functions.
 * Uses the default format of {@link ArchiveWriterAbstract}
 */
public class TarArchiveWriter
        extends ArchiveWriterAbstract
        implements ArchiveWriterAbstractBase.SupportsCovers {

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
     * @throws IOException on failure
     */
    public TarArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportManager helper)
            throws IOException {
        super(context, helper);

        mOutputStream = new TarArchiveOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(helper.getTempOutputFile(context)),
                        BUFFER_SIZE));
    }

    @Override
    public void prepareCovers(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException {
        doCovers(context, true, progressListener);
    }

    @Override
    public void writeCovers(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        doCovers(context, false, progressListener);
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
        entry.setModTime(new Date());
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
