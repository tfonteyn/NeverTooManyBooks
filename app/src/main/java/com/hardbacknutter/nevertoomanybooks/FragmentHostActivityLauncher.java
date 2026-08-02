/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Objects;

public final class FragmentHostActivityLauncher {

    private FragmentHostActivityLauncher() {
    }

    @NonNull
    public static Intent createIntent(@NonNull final Context context,
                                      @NonNull final Class<? extends Fragment> fragmentClass) {
        return createIntent(context, fragmentClass, R.layout.activity_main);
    }

    @NonNull
    public static Intent createIntent(@NonNull final Context context,
                                      @NonNull final Class<? extends Fragment> fragmentClass,
                                      @LayoutRes final int activityLayoutId) {
        return createIntent(context, fragmentClass, activityLayoutId, FragmentHostActivity.class);
    }

    @NonNull
    public static Intent createIntent(@NonNull final Context context,
                                      @NonNull final Class<? extends Fragment> fragmentClass,
                                      @LayoutRes final int activityLayoutId,
                                      @NonNull final Class<? extends Activity> activityClass) {
        final Input args = new Input(activityLayoutId, fragmentClass.getName());
        return new Intent(context, activityClass).putExtras(args.toBundle());
    }

    public static class Input {

        private static final String TAG = "Input";
        private static final String BKEY_ACTIVITY = TAG + ":a";
        private static final String BKEY_FRAGMENT_CLASS = TAG + ":f";

        @LayoutRes
        private final int activityLayoutId;
        @NonNull
        private final String fragmentClassName;

        Input(@LayoutRes final int activityLayoutId,
                     @NonNull final String fragmentClassName) {
            this.activityLayoutId = activityLayoutId;
            this.fragmentClassName = fragmentClassName;
        }

        @NonNull
        static Input fromBundle(@NonNull final Bundle args) {
            @LayoutRes
            final int activityResId = args.getInt(BKEY_ACTIVITY);
            final String fragmentClassName = Objects.requireNonNull(
                    args.getString(BKEY_FRAGMENT_CLASS), BKEY_FRAGMENT_CLASS);

            return new Input(activityResId, fragmentClassName);
        }

        @NonNull
        Bundle toBundle() {
            final Bundle args = new Bundle(2);
            args.putInt(BKEY_ACTIVITY, activityLayoutId);
            args.putString(BKEY_FRAGMENT_CLASS, fragmentClassName);

            return args;
        }

        int getActivityLayoutId() {
            return activityLayoutId;
        }

        @NonNull
        String getFragmentClassName() {
            return fragmentClassName;
        }
    }
}
