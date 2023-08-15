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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.network.ConnectionValidatorViewModel;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;

/**
 * Intermediate abstract class providing the bulk of the logic to validate a connection.
 */
public abstract class ConnectionValidationBasePreferenceFragment
        extends BasePreferenceFragment {

    private ConnectionValidatorViewModel vm;

    private CharSequence pkEnabled;

    @Nullable
    private ProgressDelegate progressDelegate;

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    proposeValidation();
                }
            };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        vm = new ViewModelProvider(this).get(ConnectionValidatorViewModel.class);
        // init is done in child classes
    }

    protected void init(@StringRes final int siteResId,
                        @NonNull final CharSequence pkEnabled) {
        vm.init(siteResId);
        this.pkEnabled = pkEnabled;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onConnectionSuccessful().observe(getViewLifecycleOwner(), this::onSuccess);
        vm.onConnectionCancelled().observe(getViewLifecycleOwner(), this::onCancelled);
        vm.onConnectionFailed().observe(getViewLifecycleOwner(), this::onFailure);
        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
    }

    private void proposeValidation() {
        Objects.requireNonNull(pkEnabled, "pkEnabled");
        final SwitchPreference sp = findPreference(pkEnabled);
        //noinspection DataFlowIssue
        if (sp.isChecked()) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_info_24)
                    .setTitle(R.string.lbl_test_connection)
                    .setMessage(R.string.confirm_test_connection)
                    .setNegativeButton(R.string.action_not_now, (d, w) ->
                            popBackStackOrFinish())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        if (progressDelegate == null) {
                            progressDelegate = new ProgressDelegate(getProgressFrame())
                                    .setTitle(R.string.progress_msg_connecting)
                                    .setPreventSleep(true)
                                    .setIndeterminate(true)
                                    .setOnCancelListener(v -> vm.cancelTask(
                                            R.id.TASK_ID_VALIDATE_CONNECTION));
                        }
                        //noinspection DataFlowIssue
                        progressDelegate.show(() -> getActivity().getWindow());
                        vm.validateConnection();
                    })
                    .create()
                    .show();
        } else {
            popBackStackOrFinish();
        }
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_test_connection)
                        .setPreventSleep(false)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> vm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private void onSuccess(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.getData().ifPresent(result -> {
            if (result) {
                //noinspection DataFlowIssue
                Snackbar.make(getView(), R.string.info_authorized, Snackbar.LENGTH_SHORT)
                        .show();
                getView().postDelayed(this::popBackStackOrFinish, BaseActivity.DELAY_SHORT_MS);
            } else {
                //For now we don't get here, instead we would be in onFailure.
                // But keeping this here to guard against future changes in the task logic
                //noinspection DataFlowIssue
                Snackbar.make(getView(), R.string.httpErrorAuth, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void onCancelled(@NonNull final LiveDataEvent<TaskResult<Boolean>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
        });
    }

    private void onFailure(@NonNull final LiveDataEvent<TaskResult<Throwable>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::getResult).filter(Objects::nonNull).ifPresent(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), e,
                             getString(R.string.httpError),
                             getString(R.string.error_network_failed_try_again));
        });
    }
}
