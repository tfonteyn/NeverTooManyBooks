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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Implementation of XML-specific writer functions.
 * <p>
 * Uses the default format of {@link ArchiveWriterAbstract}, overriding:
 * <ul>
 *     <li>Books: XML</li>
 *     <li>Covers: not supported</li>
 * </ul>
 */
public class XmlArchiveWriter
        extends ArchiveWriterAbstract {

    /** The output stream for the archive. */
    @NonNull
    private final OutputStream mOutputStream;
    /** {@link #prepareData} writes to this file; {@link #writeBooks} copies it to the archive. */
    @Nullable
    private File mTmpBooksFile;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws IOException on failure to create / and write the header
     */
    public XmlArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportHelper helper)
            throws IOException {
        super(context, helper);

        mOutputStream = new FileOutputStream(mHelper.getTempOutputFile(context));

        final String text = XmlUtils.XML_VERSION_1_0_ENCODING_UTF_8
                            + '<' + XmlTags.XML_ROOT + XmlUtils.versionAttr(XmlExporter.VERSION)
                            + ">\n";
        mOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write as a temporary XML file, to be added to the archive in {@link #writeBooks}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public ExportResults prepareData(@NonNull final Context context,
                                     @NonNull final ProgressListener progressListener)
            throws IOException {
        // Get a temp file and set for delete
        mTmpBooksFile = File.createTempFile("data_xml_", ".tmp");
        mTmpBooksFile.deleteOnExit();

        // We must strip out other entities here as the XmlExporter supports all entities
        // which we don't want to do here.
        final int entities = mHelper.getOptions() & (Options.BOOKS | Options.COVERS);
        try (Exporter exporter = new XmlExporter(context, entities,
                                                 mHelper.getUtcDateTimeSince())) {
            return exporter.write(context, mTmpBooksFile, progressListener);
        }
    }

    /**
     * Write the file as prepared in {@link #prepareData}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeBooks(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws IOException {
        try {
            Objects.requireNonNull(mTmpBooksFile);
            putFile(ArchiveContainerEntry.BooksXml.getName(), mTmpBooksFile, true);
        } finally {
            FileUtils.delete(mTmpBooksFile);
        }
    }

    /**
     * Add a File to the archive. Supports text files only.
     *
     * @param name     ignored
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
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, mOutputStream);
        }
    }

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     ignored
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
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, mOutputStream);
        }
    }

    @Override
    public void close()
            throws IOException {
        final String text = "</" + XmlTags.XML_ROOT + ">\n";
        mOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
        mOutputStream.close();
        super.close();
    }
}
