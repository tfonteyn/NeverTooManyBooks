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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;

public abstract class OptionsDialogBase<T extends Parcelable>
        extends BaseDialogFragment {

    /** Log tag. */
    private static final String TAG = "OptionsDialogBase";
    protected static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    public OptionsDialogBase(@LayoutRes final int layoutId) {
        super(layoutId);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = args.getString(BKEY_REQUEST_KEY);
    }

//    protected void fixDialogWidth(@DimenRes final int dimenId) {
//       if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            // force the dialog to be big enough
//            Dialog dialog = getDialog();
//            if (dialog != null) {
//                int width = getResources().getDimensionPixelSize(dimenId);
//                int height = ViewGroup.LayoutParams.WRAP_CONTENT;
//                //noinspection ConstantConditions
//                dialog.getWindow().setLayout(width, height);
//            }
//        }
//    }

    protected void onOptionsSet(@NonNull final T options) {
        OnOptionsListener.sendResult(this, mRequestKey, options);
        dismiss();
    }

    protected void onCancelled() {
        OnOptionsListener.sendCancelled(this, mRequestKey);
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed or cancelled.
     */
    public interface OnOptionsListener<T extends Parcelable>
            extends FragmentResultListener {

        /* private. */ String OPTIONS = "options";
        /* private. */ String CANCELLED = "cancelled";

        static void sendCancelled(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey) {
            final Bundle result = new Bundle(1);
            result.putBoolean(CANCELLED, true);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        static <T extends Parcelable> void sendResult(@NonNull final Fragment fragment,
                                                      @NonNull final String requestKey,
                                                      @NonNull final T options) {
            final Bundle result = new Bundle(1);
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
