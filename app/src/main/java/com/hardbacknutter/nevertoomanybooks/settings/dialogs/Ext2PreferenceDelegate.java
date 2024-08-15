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
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.DialogPreference;

import java.util.Objects;

class Ext2PreferenceDelegate<P extends DialogPreference,
        VM extends Ext2PreferenceViewModel> {

    @NonNull
    protected final DialogFragment owner;
    private final String key;
    /** Use {@link #getPreference()} to access. */
    private P preference;

    final VM vm;

    Ext2PreferenceDelegate(@NonNull final DialogFragment owner,
                           @NonNull final Bundle args,
                           @NonNull final Class<VM> vmClass) {
        this.owner = owner;
        key = Objects.requireNonNull(args.getString(Ext2PreferenceViewModel.ARG_KEY));

        vm = new ViewModelProvider(owner).get(key, vmClass);
        // TODO: this is an ultra-weird Android bug.
        //  Example with Ext2ListPreferenceViewModel:
        //  If we run the vm.init() here, then it will call  Ext2PreferenceViewModel.init()
        //  and NOT Ext2ListPreferenceViewModel.init()
        //  When in the debugger, it shows the vm variable as being the CORRECT child-viewmodel.
        //  Need to replicate this in a testcase and log it with google.
        //  Workaround: if we run the init from the child-class itself, it calls the correct
        //  child-viewmodel.
        //        vm.init(owner.getContext(), getPreference(), args);
        //  .
        //  The only difference being is that "here" we are in the constructor/init phase
        //  of this class
    }

    /**
     * Get it.
     *
     * @return the preference
     *
     * @see Ext2PreferenceViewModel#init(Context, DialogPreference)
     */
    @SuppressWarnings("deprecation")
    @NonNull
    P getPreference() {
        if (preference == null) {
            final Fragment rawFragment = owner.getTargetFragment();
            if (!(rawFragment instanceof DialogPreference.TargetFragment)) {
                throw new IllegalStateException("Target fragment must implement TargetFragment"
                                                + " interface");
            }

            preference = Objects.requireNonNull(
                    ((DialogPreference.TargetFragment) rawFragment).findPreference(key));
        }
        return preference;
    }

    @Nullable
    CharSequence getDialogTitle() {
        return vm.getDialogTitle();
    }

    @Nullable
    CharSequence getDialogMessage() {
        return vm.getDialogMessage();
    }

    @Nullable
    CharSequence getPositiveButtonText() {
        return vm.getPositiveButtonText();
    }

    @Nullable
    CharSequence getNegativeButtonText() {
        return vm.getNegativeButtonText();
    }

    @Nullable
    BitmapDrawable getDialogIcon() {
        return vm.getDialogIcon();
    }

    void bindMessageView(@Nullable final TextView dialogMessageView) {
        if (dialogMessageView != null) {
            final CharSequence dialogMessage = getDialogMessage();
            if (dialogMessage != null && dialogMessage.length() != 0) {
                dialogMessageView.setText(dialogMessage);
                dialogMessageView.setVisibility(View.VISIBLE);
            } else {
                dialogMessageView.setVisibility(View.GONE);
            }
        }
    }
}
