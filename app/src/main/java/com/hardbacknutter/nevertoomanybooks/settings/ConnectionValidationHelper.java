/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.net.ConnectException;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.network.ConnectionValidatorViewModel;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.Delay;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

/**
 * Provides the bulk of the logic to validate a connection.
 */
public class ConnectionValidationHelper {

    private static final String TAG = "ConnectionValidationHel";

    @NonNull
    protected final Fragment owner;
    @NonNull
    protected final Runnable finish;
    @NonNull
    private final View progressFrame;
    @NonNull
    private final ConnectionValidatorViewModel vm;
    @NonNull
    private final BooleanSupplier shouldProposeValidation;
    @Nullable
    private ProgressDelegate progressDelegate;
    @SuppressWarnings("FieldCanBeLocal")
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (vm.isEnabled() && shouldProposeValidation.getAsBoolean()) {
                        proposeValidation();
                    } else {
                        finish.run();
                    }
                }
            };

    /**
     * Constructor. Should be called from {@code Fragment#onViewCreated}.
     *
     * @param siteResId         for the site we're validating
     * @param owner             of the helper
     * @param progressFrame     to use
     * @param proposeValidation callback to check if when finishing,
     *                          the connection should be validated
     * @param finish            callback to finish the Fragment/Activity
     */
    public ConnectionValidationHelper(@StringRes final int siteResId,
                                      @NonNull final Fragment owner,
                                      @NonNull final View progressFrame,
                                      @NonNull final BooleanSupplier proposeValidation,
                                      @NonNull final Runnable finish) {
        this.owner = owner;
        this.progressFrame = progressFrame;
        this.shouldProposeValidation = proposeValidation;
        this.finish = finish;

        vm = new ViewModelProvider(owner).get(ConnectionValidatorViewModel.class);
        vm.init(siteResId);

        final LifecycleOwner viewLifecycleOwner = owner.getViewLifecycleOwner();

        //noinspection DataFlowIssue
        owner.getActivity().getOnBackPressedDispatcher()
             .addCallback(viewLifecycleOwner, backPressedCallback);

        if (vm.isEnabled()) {
            vm.onConnectionSuccessful().observe(viewLifecycleOwner, this::onSuccess);
            vm.onConnectionCancelled().observe(viewLifecycleOwner, this::onCancelled);
            vm.onConnectionFailed().observe(viewLifecycleOwner, this::onFailure);
            vm.onProgress().observe(viewLifecycleOwner, this::onProgress);
        }
    }

    /**
     * Called when the user taps "back" AND if validation/authentication is enabled.
     * <p>
     * Prompt the user to either start a connection test, or continue with the "back" action.
     */
    @CallSuper
    protected void proposeValidation() {
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(owner.getContext())
                .setIcon(R.drawable.info_24px)
                .setTitle(R.string.lbl_test_connection)
                .setMessage(R.string.confirm_test_connection)
                .setNegativeButton(R.string.action_not_now, (d, w) -> finish.run())
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    if (progressDelegate == null) {
                        progressDelegate = new ProgressDelegate(progressFrame)
                                .setTitle(R.string.progress_msg_connecting)
                                .setPreventSleep(true)
                                .setIndeterminate(true)
                                .setOnCancelListener(v -> vm.cancelTask(
                                        R.id.TASK_ID_VALIDATE_CONNECTION));
                    }
                    progressDelegate.show();
                    vm.validateConnection();
                })
                .create()
                .show();
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            progressDelegate.dismiss();
            progressDelegate = null;
        }
    }

    private void onSuccess(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.process(success -> {
            if (success) {
                //noinspection DataFlowIssue
                Snackbar.make(owner.getView(), R.string.info_authorized, Snackbar.LENGTH_SHORT)
                        .show();
                owner.getView().postDelayed(finish, Delay.SHORT_MS);
            } else {
                //For now, we don't get here, instead we would be in onFailure.
                // But keeping this here to guard against future changes in the task logic
                //noinspection DataFlowIssue
                Snackbar.make(owner.getView(), R.string.httpErrorAuth, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void onCancelled(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.process(ignored -> {
            //noinspection DataFlowIssue
            Snackbar.make(owner.getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
        });
    }

    private void onFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            final Context context = owner.getContext();
            if (e instanceof CredentialsException) {
                //noinspection DataFlowIssue
                final Optional<String> msg = ExMsg.map(context, e);
                if (msg.isPresent()) {
                    ErrorDialog.show(context, TAG, e, msg.get(),
                                     context.getString(R.string.error_network_failed_try_again));
                    return;
                }
            }
            if (e instanceof ConnectException) {
                //noinspection DataFlowIssue
                final Optional<String> msg = ExMsg.map(context, e);
                if (msg.isPresent()) {
                    ErrorDialog.show(context, TAG, e, msg.get(),
                                     context.getString(R.string.error_network_failed_try_again));
                    return;
                }
            }
            //noinspection DataFlowIssue
            ErrorDialog.show(context, TAG, e,
                             context.getString(R.string.httpError),
                             context.getString(R.string.error_network_failed_try_again));
        });
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressDelegate == null) {
                progressDelegate = new ProgressDelegate(progressFrame)
                        .setTitle(R.string.lbl_test_connection)
                        .setPreventSleep(false)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                        .show();
            }
            progressDelegate.onProgress(progress);
        });
    }
}
