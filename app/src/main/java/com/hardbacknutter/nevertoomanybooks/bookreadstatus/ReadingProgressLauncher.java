/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.UiContext;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class ReadingProgressLauncher
        extends DialogLauncher {

    @NonNull
    private final OnReadListener onReadListener;
    @NonNull
    private final OnReadingProgressListener onReadingProgressListener;

    /**
     * Constructor.
     *
     * @param onReadListener            listener for Read/Unread status updates
     * @param onReadingProgressListener listener for extended progress updates
     */
    ReadingProgressLauncher(@NonNull final OnReadListener onReadListener,
                            @NonNull final OnReadingProgressListener onReadingProgressListener) {
        super(DBKey.READ_PROGRESS,
              ReadingProgressDialogFragment::new,
              ReadingProgressBottomSheet::new);
        this.onReadListener = onReadListener;
        this.onReadingProgressListener = onReadingProgressListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context         preferably the {@code Activity}
     *                        but another UI {@code Context} will also do.
     * @param readingProgress to edit
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final ReadingProgress readingProgress) {

        final ReadingProgressInput input = new ReadingProgressInput(getRequestKey(),
                                                                    readingProgress);
        showDialog(context, input.tobundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final ReadingProgressOutput output = ReadingProgressOutput.fromBundle(result);
        final Boolean read = output.getRead();
        if (read != null) {
            onReadListener.onRead(read);
        } else {
            onReadingProgressListener.onReadingProgress(
                    Objects.requireNonNull(output.getReadingProgress()));
        }
    }

    @FunctionalInterface
    public interface OnReadListener {

        /**
         * Callback handler.
         *
         * @param read flag
         */
        void onRead(boolean read);
    }

    @FunctionalInterface
    public interface OnReadingProgressListener {

        /**
         * Callback handler.
         *
         * @param readingProgress progress
         */
        void onReadingProgress(@NonNull ReadingProgress readingProgress);
    }
}
