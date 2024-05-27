/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.debug;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.LoggerFactory;

public class Unused {
    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugArguments(@NonNull final Activity activity,
                                       @NonNull final String tag,
                                       @NonNull final String method) {
        debugArguments(tag, method, activity.getIntent().getExtras());
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugArguments(@NonNull final Fragment fragment,
                                       @NonNull final String tag,
                                       @NonNull final String method) {
        debugArguments(tag, method, fragment.getArguments());
    }

    private static void debugArguments(@NonNull final String tag,
                                       @NonNull final String method,
                                       @Nullable final Bundle args) {
        if (args != null) {
            LoggerFactory.getLogger().d(tag, method, "args=" + args);
            if (args.containsKey(Book.BKEY_BOOK_DATA)) {
                LoggerFactory.getLogger().d(tag, method, "args[Book]=" + args.getParcelable(
                        Book.BKEY_BOOK_DATA));
            }
        }
    }

    /**
     * DEBUG only.
     * Dump an InputStream to the console.
     */
    @SuppressLint("LogConditional")
    @SuppressWarnings("unused")
    public void dump(@NonNull final String tag,
                     @NonNull final String method,
                     @NonNull final InputStream inputStream) {
        try {
            final BufferedInputStream bis = new BufferedInputStream(inputStream);
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int result = bis.read();
            while (result != -1) {
                buf.write((byte) result);
                result = bis.read();
            }
            // Charset needs API 33
            //noinspection CharsetObjectCanBeUsed
            final String msg = buf.toString("UTF-8");
            LoggerFactory.getLogger().d(tag, method, msg);
        } catch (@NonNull final IOException e) {
            LoggerFactory.getLogger().d(tag, "dumping failed: ", e);
        }
    }
}
