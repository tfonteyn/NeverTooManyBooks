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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSimpleTextBinding;
import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;
import com.hardbacknutter.util.logger.LoggerFactory;

class Ext2EditTextPreferenceDelegate
        extends Ext2PreferenceDelegate<EditTextPreference, Ext2EditTextViewModel> {
    private static final String TAG = "Ext2EditTextPrefDelegate";
    private static final int SHOW_REQUEST_TIMEOUT = 1000;
    private static final int SHOW_REQUEST_DELAY_MS = 50;
    private long showRequestTime = -1;

    private DialogEditSimpleTextBinding vb;

    Ext2EditTextPreferenceDelegate(@NonNull final DialogFragment owner,
                                   @NonNull final Bundle args) {
        super(owner, args, Ext2EditTextViewModel.class);
        // See comment in Ext2PreferenceDelegate-constructor
        //noinspection DataFlowIssue
        vm.init(owner.getContext(), getPreference());
    }

    @NonNull
    View onCreateView(@NonNull final LayoutInflater inflater,
                      @Nullable final ViewGroup container) {
        vb = DialogEditSimpleTextBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    void onViewCreated(@NonNull final DialogMode mode) {
        switch (mode) {
            case Dialog:
                // Ensure the drag handle and title field is hidden.
                vb.dragHandle.setVisibility(View.GONE);
                vb.title.setVisibility(View.GONE);
                break;

            case BottomSheet:
                // Ensure the drag handle is visible and show the title.
                vb.dragHandle.setVisibility(View.VISIBLE);
                vb.title.setText(getDialogTitle());
                break;
        }
        bindMessageView(vb.message);
        bindEditText();
    }

    private void bindEditText() {
        vb.edit.requestFocus();
        vb.edit.setText(vm.getText());
        // Place cursor at the end
        //noinspection DataFlowIssue
        vb.edit.setSelection(vb.edit.getText().length());

        // ENHANCE: this uses reflection, some day we need to fix this.
        // https://issuetracker.google.com/issues/351994240
        //
        // The listener is typically used to force TEXT/PASSWORD input types
        try {
            final EditTextPreference p = getPreference();

            final Method method = p.getClass().getDeclaredMethod("getOnBindEditTextListener");
            method.setAccessible(true);
            final EditTextPreference.OnBindEditTextListener listener =
                    (EditTextPreference.OnBindEditTextListener) method.invoke(p);

            if (listener != null) {
                listener.onBindEditText(vb.edit);
            }
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    void saveValue() {
        //noinspection DataFlowIssue
        final String value = vb.edit.getText().toString();
        final EditTextPreference p = getPreference();
        if (p.callChangeListener(value)) {
            p.setText(value);
        }
    }

    /**
     * Android 10 or lower only.
     */
    void requestIME() {
        setPendingShowSoftInputRequest(true);
        scheduleShowSoftInputInner();
    }

    private boolean hasPendingShowSoftInputRequest() {
        return (showRequestTime != -1 &&
                ((showRequestTime + Ext2EditTextPreferenceDelegate.SHOW_REQUEST_TIMEOUT)
                 > SystemClock.currentThreadTimeMillis()));
    }

    private void setPendingShowSoftInputRequest(final boolean pendingShowSoftInputRequest) {
        showRequestTime = pendingShowSoftInputRequest ? SystemClock.currentThreadTimeMillis() : -1;
    }

    private void scheduleShowSoftInputInner() {
        if (hasPendingShowSoftInputRequest()) {
            if (!vb.edit.isFocused()) {
                setPendingShowSoftInputRequest(false);
                return;
            }
            final InputMethodManager imm = (InputMethodManager)
                    vb.edit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            // Schedule showSoftInput once the input connection of the editor established.
            if (imm.showSoftInput(vb.edit, 0)) {
                setPendingShowSoftInputRequest(false);
            } else {
                vb.edit.removeCallbacks(showSoftInputRunnable);
                vb.edit.postDelayed(showSoftInputRunnable,
                                    Ext2EditTextPreferenceDelegate.SHOW_REQUEST_DELAY_MS);
            }
        }
    }

    private final Runnable showSoftInputRunnable = this::scheduleShowSoftInputInner;
}
