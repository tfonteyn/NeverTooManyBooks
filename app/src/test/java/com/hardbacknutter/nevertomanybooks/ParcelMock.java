package com.hardbacknutter.nevertomanybooks;

import android.os.Parcel;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * <a href="https://github.com/konmik/nucleus/blob/master/nucleus-test-kit/src/main/java/mocks/ParcelMock.java">
 * https://github.com/konmik/nucleus/blob/master/nucleus-test-kit/src/main/java/mocks/ParcelMock.java</a>
 */
public class ParcelMock {

    public static Parcel mock() {

        Parcel parcel = Mockito.mock(Parcel.class);
        final ArrayList<Object> objects = new ArrayList<>();
        final AtomicInteger position = new AtomicInteger();

        doAnswer(invocation -> {
            objects.add(invocation.getArguments()[0]);
            position.incrementAndGet();
            return null;
        }).when(parcel).writeValue(any());

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
