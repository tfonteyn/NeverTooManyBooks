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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param read       Read/Unread status
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "SameParameterValue"})
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          final boolean read) {
        final Bundle result = new Bundle(1);
        result.putBoolean(DBKey.READ__BOOL, read);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment        the calling DialogFragment
     * @param requestKey      to use
     * @param readingProgress data
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @Nullable final ReadingProgress readingProgress) {
        final Bundle result = new Bundle(1);
        result.putParcelable(DBKey.READ_PROGRESS, readingProgress);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param context         preferably the {@code Activity}
     *                        but another UI {@code Context} will also do.
     * @param readingProgress to edit
     */
    public void launch(@NonNull final Context context,
                       @NonNull final ReadingProgress readingProgress) {
        final Bundle args = new Bundle(2);
        args.putParcelable(DBKey.READ_PROGRESS, readingProgress);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        if (result.containsKey(DBKey.READ__BOOL)) {
            onReadListener.onRead(result.getBoolean(DBKey.READ__BOOL));
        } else {
            onReadingProgressListener.onReadingProgress(
                    Objects.requireNonNull(result.getParcelable(DBKey.READ_PROGRESS),
                                           DBKey.READ_PROGRESS));

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
