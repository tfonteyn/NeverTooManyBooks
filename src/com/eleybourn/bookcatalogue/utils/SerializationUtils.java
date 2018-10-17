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
     * Note: original code caught exceptions and returned 'null'. No longer doing this as
     * any exception means something is wrong and should be flagged as such.
     *
     * @param o Object to convert
     *
     * @return Resulting byte array.
     */
    @NonNull
    public static byte[] serializeObject(@NonNull final Serializable o) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        } catch (IOException e) {
            Logger.error(e);
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    /**
     * Deserialize the passed byte array
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T deserializeObject(@NonNull final byte[] o) throws RTE.DeserializationException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(o))) {
            return (T) in.readObject();
        } catch (@NonNull ClassCastException | ClassNotFoundException | IOException e) {
            throw new RTE.DeserializationException(e);
        }
    }

    /**
     * Serialize then de-serialize to create a deep clone.
     */
    @NonNull
    public static <T extends Serializable> T cloneObject(@NonNull final T o) throws RTE.DeserializationException {
        return deserializeObject(serializeObject(o));
    }

}
