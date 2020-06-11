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
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public abstract class OptionsDialogBase<T>
        extends DialogFragment {

    /** Log tag. */
    private static final String TAG = "OptionsDialogBase";

    /** Where to send the result. */
    @Nullable
    private WeakReference<OptionsListener<T>> mListener;

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

    public void setListener(@NonNull final OptionsListener<T> listener) {
        mListener = new WeakReference<>(listener);
    }

    protected void onOptionsSet(@NonNull final T options) {
        if (mListener != null && mListener.get() != null) {
            mListener.get().onOptionsSet(options);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onOptionsSet|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    protected void onCancelled() {
        if (mListener != null && mListener.get() != null) {
            mListener.get().onCancelled();
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onCancelled|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed or cancelled.
     */
    public interface OptionsListener<T> {

        void onOptionsSet(@NonNull T options);

        default void onCancelled() {
            // do nothing
        }
    }
}
