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
package com.hardbacknutter.nevertoomanybooks._mocks.os;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;

import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

public final class ContextMock {

    @NonNull
    public static Context create(@NonNull final String packageName) {

        final Context context = Mockito.mock(Context.class);

        when(context.getPackageName()).thenReturn(packageName);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.createConfigurationContext(any())).thenReturn(context);

        when(context.getExternalCacheDir()).thenReturn(getTmpDir());
        when(context.getExternalFilesDir(isNull())).thenReturn(getTmpDir());
        when(context.getExternalFilesDir(eq("Pictures")))
                .thenReturn(getTmpDir("MockPictures"));

        return context;
    }

    public static File getTmpDir() {
        //noinspection ConstantConditions
        return new File(System.getProperty("java.io.tmpdir"));
    }

    public static File getTmpDir(@NonNull final String path) {
        final File tmp = new File(System.getProperty("java.io.tmpdir") + path);
        //noinspection ResultOfMethodCallIgnored
        tmp.mkdir();
        return tmp;
    }
}
