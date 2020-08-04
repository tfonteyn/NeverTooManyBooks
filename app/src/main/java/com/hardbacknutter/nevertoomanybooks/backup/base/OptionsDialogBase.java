/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.app.Dialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.util.Objects;

public abstract class OptionsDialogBase<T extends Parcelable>
        extends DialogFragment {

    /** Log tag. */
    private static final String TAG = "OptionsDialogBase";
    public static final String REQUEST_KEY = TAG + ":rk";

    protected void fixDialogWidth(@DimenRes final int dimenId) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // force the dialog to be big enough
            Dialog dialog = getDialog();
            if (dialog != null) {
                int width = getResources().getDimensionPixelSize(dimenId);
                int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                //noinspection ConstantConditions
                dialog.getWindow().setLayout(width, height);
            }
        }
    }

    protected void onOptionsSet(@NonNull final T options) {
        OptionsListener.sendResult(this, REQUEST_KEY, options);
    }

    protected void onCancelled() {
        OptionsListener.sendCancelled(this, REQUEST_KEY);
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed or cancelled.
     */
    public interface OptionsListener<T extends Parcelable>
            extends FragmentResultListener {

        /* private. */ String OPTIONS = "options";
        /* private. */ String CANCELLED = "cancelled";

        static void sendCancelled(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey) {
            final Bundle result = new Bundle();
            result.putBoolean(CANCELLED, true);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        static <T extends Parcelable> void sendResult(@NonNull final Fragment fragment,
                                                      @NonNull final String requestKey,
                                                      @NonNull final T options) {
            final Bundle result = new Bundle();
            result.putParcelable(OPTIONS, options);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            if (result.getBoolean(CANCELLED)) {
                onCancelled();
            } else {
                onOptionsSet(Objects.requireNonNull(result.getParcelable(OPTIONS)));
            }
        }

        void onOptionsSet(@NonNull T options);

        default void onCancelled() {
            // do nothing
        }
    }
}
