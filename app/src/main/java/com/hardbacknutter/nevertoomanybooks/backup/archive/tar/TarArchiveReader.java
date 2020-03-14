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
package com.hardbacknutter.nevertoomanybooks.backup.archive.tar;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.archive.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ReaderEntity.Type;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ReaderEntityAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.options.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;

/**
 * Implementation of TAR-specific reader functions.
 */
public class TarArchiveReader
        extends ArchiveReaderAbstract {

    /** The data stream for the archive. */
    @Nullable
    private TarArchiveInputStream mInputStream;

    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveInfo mInfo;
    /** Used to allow 'peeking' at the input stream. */
    @Nullable
    private ReaderEntity mPushedEntity;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    public TarArchiveReader(@NonNull final Context context,
                            @NonNull final ImportHelper helper) {
        super(context, helper);
    }

    @Override
    public void validate(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        ArchiveInfo archiveInfo = getInfo();
        // the info block will/can do more checks.
        archiveInfo.validate();
    }

    @Override
    @Nullable
    public ReaderEntity findEntity(@NonNull final Type type)
            throws IOException {

        TarArchiveEntry entry;
        while (true) {
            entry = getInputStream().getNextTarEntry();
            if (entry == null) {
                return null;
            }

            // Based on the file name, determine entity type
            Type typeFound = getBackupEntityType(entry);
            if (type.equals(typeFound)) {
                return new TarBackupReaderEntity(typeFound, this, entry);
            }
        }
    }

    @Override
    @Nullable
    public ReaderEntity nextEntity()
            throws IOException {

        if (mPushedEntity != null) {
            ReaderEntity e = mPushedEntity;
            mPushedEntity = null;
            return e;
        }

        TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return null;
        }

        Type typeFound = getBackupEntityType(entry);
        return new TarBackupReaderEntity(typeFound, this, entry);
    }

    /**
     * Tar archive info is stored in an xml file.
     *
     * @return the info
     *
     * @throws IOException             on failure
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @NonNull
    @Override
    public ArchiveInfo getInfo()
            throws IOException, InvalidArchiveException {
        if (mInfo == null) {
            ReaderEntity entity;
            try {
                entity = findEntity(Type.InfoXml);
            } catch (@NonNull final IOException e) {
                //VERY annoying... the apache tar library does not throw a unique exception.
                // So we, reluctantly, look at the message...
                if ("Error detected parsing the header".equals(e.getMessage())) {
                    throw new InvalidArchiveException();
                } else {
                    throw e;
                }
            }

            if (entity == null) {
                throw new IOException("No INFO entity found");
            }

            // read the INFO
            mInfo = new ArchiveInfo();
            try (XmlImporter importer = new XmlImporter(null)) {
                importer.doBackupInfoBlock(entity, mInfo);
            }
            // We MUST close the stream here, so the caller gets a pristine stream.
            reset();
        }
        return mInfo;
    }

    /**
     * Get the input stream; (re)creating as needed.
     *
     * <strong>Note:</strong> we don't access mInputStream directly for reading,
     * as other code can set it to null when needed.
     *
     * @return the stream
     *
     * @throws IOException on failure
     */
    @NonNull
    private TarArchiveInputStream getInputStream()
            throws IOException {
        if (mInputStream == null) {
            InputStream is = mContentResolver.openInputStream(getUri());
            if (is == null) {
                throw new IOException("InputStream was NULL");
            }
            mInputStream = new TarArchiveInputStream(is);
        }

        return mInputStream;
    }

    @Override
    public void reset()
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
        reset();
        super.close();
    }

    /**
     * Implementation of TAR-specific {@link ReaderEntity} functions.
     */
    private static class TarBackupReaderEntity
            extends ReaderEntityAbstract {

        @NonNull
        private final TarArchiveReader mReader;
        @NonNull
        private final TarArchiveEntry mEntry;

        /**
         * Constructor.
         *
         * @param type   Type of item
         * @param reader Parent
         * @param entry  Corresponding archive entry
         */
        TarBackupReaderEntity(@NonNull final Type type,
                              @NonNull final TarArchiveReader reader,
                              @NonNull final TarArchiveEntry entry) {
            super(type);
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
    }
}
