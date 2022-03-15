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

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * <a href="https://github.com/konmik/nucleus/blob/master/nucleus-test-kit/src/main/java/mocks/ParcelMock.java">ParcelMock</a>
 */
public final class ParcelMock {

    private ParcelMock() {
    }

    public static Parcel create() {

        final Parcel parcel = Mockito.mock(Parcel.class);
        final List<Object> objects = new ArrayList<>();
        final AtomicInteger position = new AtomicInteger();

        doAnswer(invocation -> {
            objects.add(invocation.getArguments()[0]);
            position.incrementAndGet();
            return null;
        }).when(parcel).writeValue(any());

        //noinspection ZeroLengthArrayAllocation
        when(parcel.marshall()).thenReturn(new byte[0]);

        doAnswer(invocation -> {
            position.set((Integer) invocation.getArguments()[0]);
            return null;
        }).when(parcel).setDataPosition(anyInt());

        when(parcel.readValue(any(ClassLoader.class)))
                .thenAnswer(invocation -> objects.get(position.getAndIncrement()));

        return parcel;
    }
}
