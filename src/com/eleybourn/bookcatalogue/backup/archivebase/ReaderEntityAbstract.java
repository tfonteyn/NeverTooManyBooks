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
package com.eleybourn.bookcatalogue.backup.archivebase;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Basic implementation of format-agnostic ReaderEntity methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class ReaderEntityAbstract
        implements ReaderEntity {

    /** Buffer size for buffered streams. */
    protected static final int BUFFER_SIZE = 32768;

    @NonNull
    private final BackupEntityType mType;

    public ReaderEntityAbstract(@NonNull final BackupEntityType type) {
        this.mType = type;
    }

    /** Get the type of this entity. */
    @NonNull
    @Override
    public BackupEntityType getType() {
        return mType;
    }

    @Override
    public void saveToDirectory(@NonNull final File dir)
            throws IOException {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory");
        }

        // Build the new File and save
        File destFile = new File(dir.getAbsoluteFile() + File.separator + getName());
        try {
            StorageUtils.copyFile(getStream(), BUFFER_SIZE, destFile);
        } finally {
            if (destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.setLastModified(this.getDateModified().getTime());
            }
        }
    }

    @NonNull
    @Override
    public <T extends Serializable> T getSerializable()
            throws IOException, DeserializationException {
        // Turn the input into a byte array
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final byte[] buffer = new byte[BUFFER_SIZE];

        while (true) {
            int cnt = getStream().read(buffer);
            if (cnt <= 0) {
                break;
            }
            out.write(buffer);
        }
        out.close();
        return SerializationUtils.deserializeObject(out.toByteArray());
    }
}
