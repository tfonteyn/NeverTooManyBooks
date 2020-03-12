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
package com.hardbacknutter.nevertoomanybooks.backup.ui;

import android.app.Dialog;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public abstract class OptionsDialogBase<T extends Options>
        extends DialogFragment {

    /** Log tag. */
    private static final String TAG = "OptionsDialogBase";

    private WeakReference<OptionsListener<T>> mListener;

    @Override
    public void onStart() {
        super.onStart();

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // force the dialog to be big enough
            Dialog dialog = getDialog();
            if (dialog != null) {
                int width = ViewGroup.LayoutParams.MATCH_PARENT;
                int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                //noinspection ConstantConditions
                dialog.getWindow().setLayout(width, height);
            }
        }
    }

    /**
     * Show a popup info text.
     *
     * @param textView   the view from which we'll take the text;
     *                   Used for a title
     * @param infoButton the View from which we'll take the content-description;
     *                   Used for the message.
     */
    void infoPopup(@NonNull final TextView textView,
                   @NonNull final View infoButton) {

        //TODO: replace dialog with lightweight popup view.
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(textView.getText())
                .setMessage(infoButton.getContentDescription())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    public void setListener(@NonNull final OptionsListener<T> listener) {
        mListener = new WeakReference<>(listener);
    }

    void onOptionsSet(@NonNull final T options) {
        if (mListener.get() != null) {
            mListener.get().onOptionsSet(options);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onConfirmOptions|" + ErrorMsg.WEAK_REFERENCE);
            }
        }
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed or cancelled.
     */
    public interface OptionsListener<T extends Options> {

        void onOptionsSet(@NonNull T options);
    }
}
