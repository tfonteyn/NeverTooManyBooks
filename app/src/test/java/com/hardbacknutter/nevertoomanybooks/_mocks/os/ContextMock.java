/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

public final class ContextMock {

    private ContextMock() {
    }

    @NonNull
    public static Context create(@NonNull final String packageName) {

        final Context context = Mockito.mock(Context.class);

        when(context.getPackageName()).thenReturn(packageName);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.createConfigurationContext(any())).thenReturn(context);

        when(context.getFilesDir()).thenReturn(getTmpDir());
        when(context.getExternalCacheDir()).thenReturn(getTmpDir());

        when(context.getExternalFilesDir(isNull())).thenReturn(getTmpDir());
        when(context.getExternalFilesDirs(eq(Environment.DIRECTORY_PICTURES))).thenAnswer(
                (Answer<File[]>) invocation -> {
                    final File[] dirs = new File[1];
                    dirs[0] = new File(getTmpDir(), "Pictures");
                    //noinspection ResultOfMethodCallIgnored
                    dirs[0].mkdir();
                    return dirs;
                });

        return context;
    }

    private static File getTmpDir() {
        //noinspection DataFlowIssue
        return new File(System.getProperty("java.io.tmpdir"));
    }
}
