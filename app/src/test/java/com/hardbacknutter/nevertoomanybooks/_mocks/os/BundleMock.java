/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks._mocks.os;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * <a href="https://github.com/konmik/nucleus/blob/master/nucleus-test-kit/src/main/java/mocks/BundleMock.java">BundleMock</a>
 * <p>
 * ADDED:
 * - allow storing String null values
 * - putAll implementation
 */
public final class BundleMock {

    @SuppressWarnings("unchecked")
    @NonNull
    public static Bundle create() {

        final HashMap<String, Object> map = new HashMap<>();
        final Bundle bundle = Mockito.mock(Bundle.class);


        final Answer<Object> unsupported = invocation -> {
            throw new UnsupportedOperationException();
        };
        final Answer<Object> put = invocation -> {
            map.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
        final Answer<Object> get = invocation -> map.get((String) invocation.getArguments()[0]);
        final Answer<Object> getOrDefault = invocation -> {
            final String key = (String) invocation.getArguments()[0];
            return map.containsKey(key) ? map.get(key) : invocation.getArguments()[1];
        };

        doAnswer(invocation -> map.size()).when(bundle).size();
        doAnswer(invocation -> map.isEmpty()).when(bundle).isEmpty();
        doAnswer(invocation -> {
            map.clear();
            return null;
        }).when(bundle).clear();

        doAnswer(invocation -> map.containsKey((String) invocation.getArguments()[0]))
                .when(bundle).containsKey(anyString());
        doAnswer(invocation -> map.get((String) invocation.getArguments()[0]))
                .when(bundle).get(anyString());
        doAnswer(invocation -> {
            map.remove((String) invocation.getArguments()[0]);
            return null;
        }).when(bundle).remove(anyString());

        doAnswer(invocation -> map.keySet()).when(bundle).keySet();

        doAnswer(invocation -> BundleMock.class.getSimpleName() + "{map=" + map + "}")
                .when(bundle).toString();

        doAnswer(put).when(bundle).putBoolean(anyString(), anyBoolean());
        when(bundle.getBoolean(anyString())).thenAnswer(get);
        when(bundle.getBoolean(anyString(), anyBoolean())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putByte(anyString(), anyByte());
        when(bundle.getByte(anyString())).thenAnswer(get);
        when(bundle.getByte(anyString(), anyByte())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putChar(anyString(), anyChar());
        when(bundle.getChar(anyString())).thenAnswer(get);
        when(bundle.getChar(anyString(), anyChar())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putInt(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putLong(anyString(), anyLong());
        when(bundle.getLong(anyString())).thenAnswer(get);
        when(bundle.getLong(anyString(), anyLong())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putDouble(anyString(), anyDouble());
        when(bundle.getDouble(anyString())).thenAnswer(get);
        when(bundle.getDouble(anyString(), anyDouble())).thenAnswer(getOrDefault);

        // 2020-02-02: allow storing null String values
        doAnswer(put).when(bundle).putString(anyString(), nullable(String.class));
        when(bundle.getString(anyString())).thenAnswer(get);
        when(bundle.getString(anyString(), anyString())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBooleanArray(anyString(), any(boolean[].class));
        when(bundle.getBooleanArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putLongArray(anyString(), any(long[].class));
        when(bundle.getLongArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putDoubleArray(anyString(), any(double[].class));
        when(bundle.getDoubleArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntArray(anyString(), any(int[].class));
        when(bundle.getIntArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putInt(anyString(), anyInt());
        when(bundle.getInt(anyString())).thenAnswer(get);
        when(bundle.getInt(anyString(), anyInt())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putShort(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putCharSequence(anyString(), any(CharSequence.class));
        when(bundle.getCharSequence(anyString())).thenAnswer(get);
        when(bundle.getCharSequence(anyString(), any(CharSequence.class))).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBundle(anyString(), any(Bundle.class));
        when(bundle.getBundle(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelable(anyString(), any(Parcelable.class));
        when(bundle.getParcelable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArray(anyString(), any(Parcelable[].class));
        when(bundle.getParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArrayList(anyString(), any(ArrayList.class));
        when(bundle.getParcelableArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSparseParcelableArray(anyString(), any(SparseArray.class));
        when(bundle.getSparseParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSerializable(anyString(), any(Serializable.class));
        when(bundle.getSerializable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntegerArrayList(anyString(), any(ArrayList.class));
        when(bundle.getIntegerArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putStringArrayList(anyString(), any(ArrayList.class));
        when(bundle.getStringArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArrayList(anyString(), any(ArrayList.class));
        when(bundle.getCharSequenceArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharArray(anyString(), any(char[].class));
        when(bundle.getCharArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putByteArray(anyString(), any(byte[].class));
        when(bundle.getByteArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putShortArray(anyString(), any(short[].class));
        when(bundle.getShortArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putFloatArray(anyString(), any(float[].class));
        when(bundle.getFloatArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArray(anyString(), any(CharSequence[].class));
        when(bundle.getCharSequenceArray(anyString())).thenAnswer(get);

        // 2020-08-07: implemented putAll
        doAnswer(invocation -> {
            final Bundle source = (Bundle) invocation.getArguments()[0];
            for (final String key : source.keySet()) {
                map.put(key, source.get(key));
            }
            return null;
        }).when(bundle).putAll(any(Bundle.class));


        when(bundle.hasFileDescriptors()).thenAnswer(unsupported);

        return bundle;
    }

    @Test
    void bu() {
        final Bundle b1 = create();
        b1.putString("foo", "bar");
        b1.putInt("ii", 11);
        final Bundle b2 = create();
        b2.putAll(b1);

        assertEquals("bar", b2.getString("foo"));
        assertEquals(11, b2.getInt("ii"));
    }
}
