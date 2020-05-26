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

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;

/**
 * Implementation of TAR-specific reader functions.
 */
public class TarArchiveReader
        extends ArchiveReaderAbstract {

    /** Buffer for {@link #mInputStream}. */
    private static final int BUFFER_SIZE = 65535;

    /** Provide access to the Uri InputStream. */
    @NonNull
    private final ContentResolver mContentResolver;
    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {link #getInputStream()}
     */
    @Nullable
    private TarArchiveInputStream mInputStream;

    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveInfo mInfo;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    public TarArchiveReader(@NonNull final Context context,
                            @NonNull final ImportManager helper) {
        super(context, helper);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void validate(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        if (mInfo == null) {
            mInfo = readArchiveInfo(context);
        }
        // the info block will/can do more checks.
        mInfo.validate();
    }

    @Override
    @Nullable
    public ReaderEntity seek(@NonNull final ArchiveContainerEntry type)
            throws IOException {

        TarArchiveEntry entry;
        while (true) {
            entry = getInputStream().getNextTarEntry();
            if (entry == null) {
                return null;
            }

            // Based on the file name, determine entity type
            final ArchiveContainerEntry typeFound = ArchiveContainerEntry.getType(entry.getName());
            if (type.equals(typeFound)) {
                return new TarReaderEntity(typeFound, this, entry);
            }
        }
    }

    @Override
    @Nullable
    public ReaderEntity next()
            throws IOException {

        final TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return null;
        }

        final ArchiveContainerEntry typeFound = ArchiveContainerEntry.getType(entry.getName());
        return new TarReaderEntity(typeFound, this, entry);
    }

    /**
     * A tar archive <strong>must</strong> have an info block.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public ArchiveInfo readArchiveInfo(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        if (mInfo == null) {
            // Tar archive info is stored in an xml file in the archive itself.
            // We try and find the InfoHeader entity, and process it with the XMLImporter
            final ReaderEntity entity;
            try {
                entity = seek(ArchiveContainerEntry.InfoHeaderXml);
            } catch (@NonNull final IOException e) {
                //VERY annoying... the apache tar library does not throw a unique exception.
                // We reluctantly look at the message, to give the user better error details
                if ("Error detected parsing the header".equals(e.getMessage())) {
                    throw new InvalidArchiveException(e);
                } else {
                    throw e;
                }
            }

            if (entity == null) {
                throw new IOException("No INFO entity found");
            }

            // read the INFO
            try (XmlImporter importer = new XmlImporter(context, Options.INFO)) {
                mInfo = importer.readInfo(entity.getInputStream());
            }
            // We MUST close the stream here, so the caller gets a pristine stream.
            resetToStart();
        }

        return mInfo;
    }

    /**
     * Get the input stream; (re)creating as needed.
     *
     * <strong>Note:</strong> TarArchiveInputStream does not support marking,
     * so we let {@link #resetToStart()} close/null the stream, and (re)create it here when needed.
     *
     * @return the stream
     *
     * @throws IOException on failure
     */
    @NonNull
    private TarArchiveInputStream getInputStream()
            throws IOException {
        if (mInputStream == null) {
            final InputStream is = mContentResolver.openInputStream(getUri());
            if (is == null) {
                throw new IOException("InputStream was NULL");
            }
            mInputStream = new TarArchiveInputStream(new BufferedInputStream(is, BUFFER_SIZE));
        }

        return mInputStream;
    }

    @Override
    public void resetToStart()
            throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        resetToStart();
        super.close();
    }

    private static class TarReaderEntity
            implements ReaderEntity {

        /** The entity source stream. */
        @NonNull
        private final TarArchiveReader mReader;
        /** Tar archive entry. */
        @NonNull
        private final TarArchiveEntry mEntry;
        /** Entity type. */
        @NonNull
        private final ArchiveContainerEntry mType;

        /**
         * Constructor.
         *
         * @param type   Type of item
         * @param reader Parent
         * @param entry  Corresponding archive entry
         */
        TarReaderEntity(@NonNull final ArchiveContainerEntry type,
                        @NonNull final TarArchiveReader reader,
                        @NonNull final TarArchiveEntry entry) {
            mType = type;
            mReader = reader;
            mEntry = entry;
        }

        @NonNull
        @Override
        public String getName() {
            return mEntry.getName();
        }

        @NonNull
        @Override
        public Date getDateModified() {
            return mEntry.getLastModifiedDate();
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws IOException {
            return mReader.getInputStream();
        }

        @NonNull
        @Override
        public ArchiveContainerEntry getType() {
            return mType;
        }
    }
}
