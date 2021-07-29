/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Intermediate abstract class providing the bulk of the logic to validate a connection.
 */
public abstract class ConnectionValidationBasePreferenceFragment
        extends BasePreferenceFragment {

    @Nullable
    private ProgressDelegate mProgressDelegate;

    /** start the validation. This is called from the proposal dialog by the user. */
    protected abstract void validateConnection();

    /** Cancels the validation. This is called from the progress dialog by the user. */
    protected abstract void cancelTask(final int taskId);

    protected void proposeConnectionValidation(@NonNull final CharSequence pkEnabled) {
        final SwitchPreference sp = findPreference(pkEnabled);
        //noinspection ConstantConditions
        if (sp.isChecked()) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_info_24)
                    .setTitle(R.string.lbl_test_connection)
                    .setMessage(R.string.confirm_test_connection)
                    .setNegativeButton(R.string.action_not_now, (d, w) ->
                            popBackStackOrFinish())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        validateConnection();
                    })
                    .create()
                    .show();
        } else {
            popBackStackOrFinish();
        }
    }

    protected void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(
                        getActivity().findViewById(R.id.progress_frame))
                        .setTitle(getString(R.string.lbl_test_connection))
                        .setPreventSleep(false)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> cancelTask(message.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }

    protected void closeProgressDialog() {
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
    }

    protected void onSuccess(@NonNull final FinishedMessage<Boolean> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Boolean result = message.getResult();
            if (result != null) {
                if (result) {
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), R.string.info_authorized, Snackbar.LENGTH_SHORT)
                            .show();
                    getView().postDelayed(this::popBackStackOrFinish, BaseActivity.ERROR_DELAY_MS);
                } else {
                    //For now we don't get here, instead we would be in onFailure.
                    // But keeping this here to guard against future changes in the task logic
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), R.string.httpErrorAuth, Snackbar.LENGTH_LONG).show();
                }
            }
        }
    }

    protected void onFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Exception e = message.getResult();

            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, e)
                                    .orElse(getString(R.string.error_unknown));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_network_failed_try_again)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }
}
