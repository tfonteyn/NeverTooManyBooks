/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Collection of methods to wrap common serialization routines.
 *
 * @author Philip Warner
 */
public class SerializationUtils {

    /**
     * Utility routine to convert a Serializable object to a byte array.
     *
     * @param o Object to convert
     *
     * @return Resulting byte array. NULL on failure.
     * FIXME: not all callers check null returns.... never happens or ?, for now lets log error
     */
    @Nullable
    public static byte[] serializeObject(@NonNull final Serializable o) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        } catch (IOException e) {
            Logger.logError(e);
            return null;
        }
        return bos.toByteArray();
    }

    /**
     * Deserialize the passed byte array
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T deserializeObject(@NonNull final byte[] blob) throws DeserializationException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(blob))) {
            return (T) in.readObject();
        } catch (ClassCastException | ClassNotFoundException | IOException e) {
            throw new DeserializationException(e);
        }
    }

    /**
     * Serialize then de-serialize to create a deep clone.
     */
    @NonNull
    public static <T extends Serializable> T cloneObject(@NonNull final T o) throws DeserializationException {
        byte[] bytes = serializeObject(o);
        if (bytes == null) {
            throw new DeserializationException();
        }
        return deserializeObject(bytes);
    }

    /**
     * Catchall class for errors in serialization
     *
     * @author Philip Warner
     */
    public static class DeserializationException extends Exception {
        private static final long serialVersionUID = -2040548134317746620L;

        DeserializationException() {
            super();
        }
        DeserializationException(@Nullable final Exception e) {
            super();
            initCause(e);
        }
    }
}
