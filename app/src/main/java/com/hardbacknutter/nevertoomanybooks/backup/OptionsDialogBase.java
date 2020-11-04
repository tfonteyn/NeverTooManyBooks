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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;

public abstract class OptionsDialogBase
        extends BaseDialogFragment {

    /** Log tag. */
    private static final String TAG = "OptionsDialogBase";
    protected static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    OptionsDialogBase(@LayoutRes final int layoutId) {
        super(layoutId);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequestKey = requireArguments().getString(BKEY_REQUEST_KEY);
    }

    protected void sendResult(final boolean startTask) {
        OnResultsListener.sendResult(this, mRequestKey, startTask);
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed or cancelled.
     */
    public interface OnResultsListener
            extends FragmentResultListener {

        /* private. */ String START = "start";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               final boolean startTask) {
            final Bundle result = new Bundle(1);
            result.putBoolean(START, startTask);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(result.getBoolean(START));
        }

        void onResult(final boolean success);
    }
}
