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

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlUtils;

/**
 * Implementation of XML-specific writer functions.
 * Uses the default format of {@link ArchiveWriterAbstract}, overriding:
 * Books: XML
 * Covers: not supported
 */
public class XmlArchiveWriter
        extends ArchiveWriterAbstract {

    /** The output stream for the archive. */
    @NonNull
    private final OutputStream mOutputStream;
    @Nullable
    private File mTmpBookXmlFile;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    public XmlArchiveWriter(@NonNull final Context context,
                            @NonNull final ExportManager helper)
            throws IOException {
        super(context, helper);

        mOutputStream = new FileOutputStream(mHelper.getTempOutputFile(context));

        String text = XmlUtils.XML_VERSION_1_0_ENCODING_UTF_8
                      + '<' + XmlTags.XML_ROOT + XmlUtils.versionAttr(XmlExporter.VERSION) + ">\n";
        mOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write as a temporary XML file, to be added to the archive in {@link #writeBooks}.
     *
     * <br><br>{@inheritDoc}
     */
    public void prepareBooks(@NonNull final Context context,
                             @NonNull final ProgressListener progressListener)
            throws IOException {
        // Get a temp file and set for delete
        mTmpBookXmlFile = File.createTempFile("data_xml_", ".tmp");
        mTmpBookXmlFile.deleteOnExit();

        Exporter exporter = new XmlExporter(mHelper.getOptions(), mHelper.getDateSince());
        mResults.add(exporter.write(context, mTmpBookXmlFile, progressListener));
    }

    /**
     * Write the file as prepared in {@link #prepareBooks}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeBooks(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws IOException {
        try {
            Objects.requireNonNull(mTmpBookXmlFile);
            putFile("", mTmpBookXmlFile);
        } finally {
            FileUtils.delete(mTmpBookXmlFile);
        }
    }

    /**
     * Supports text files only.
     *
     * @param unused file is copied to the output as-is
     * @param file   to store in the archive
     *
     * @throws IOException on failure
     */
    @Override
    public void putFile(@NonNull final String unused,
                        @NonNull final File file)
            throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, mOutputStream);
        }
    }

    /**
     * Supports text only.
     *
     * @param bytes to store in the archive
     *
     * @throws IOException on failure
     */
    @Override
    public void putByteArray(@NonNull final String unused,
                             @NonNull final byte[] bytes)
            throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, mOutputStream);
        }
    }

    @Override
    public void close()
            throws IOException {
        String text = "</" + XmlTags.XML_ROOT + ">\n";
        mOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
        mOutputStream.close();
        super.close();
    }
}
