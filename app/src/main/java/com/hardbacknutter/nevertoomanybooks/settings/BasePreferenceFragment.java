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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.TriStateMultiSelectListPreference;

/**
 * Base settings page. This handles:
 * <ul>
 *     <li>Summaries</li>
 *     <li>custom preference dialogs</li>
 *     <li>auto-scroll key</li>
 * </ul>
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";
    protected Toolbar mToolbar;
    @Nullable
    private String mAutoScrollToKey;

    @Override
    @CallSuper
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        final Bundle args = getArguments();
        if (args != null) {
            mAutoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //noinspection ConstantConditions
        mToolbar = getActivity().findViewById(R.id.toolbar);
    }

    /**
     * Should be called instead of direct calls to popBackStack/finish.
     * This will make sure the current fragment can be the top-fragment (then finish)
     * or be called from another fragment (then pop).
     */
    protected void popBackStackOrFinish() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            //noinspection ConstantConditions
            getActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                             .registerOnSharedPreferenceChangeListener(this);

        if (mAutoScrollToKey != null) {
            scrollToPreference(mAutoScrollToKey);
            mAutoScrollToKey = null;
        }
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        // do nothing (not abstract, easier)
    }

    /**
     * TriStateMultiSelectListPreference get a custom Dialog were the neutral button displays
     * the "unused" option.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        if (preference instanceof TriStateMultiSelectListPreference) {
            // getParentFragmentManager is required by PreferenceDialogFragmentCompat
            // as the latter insists on using setTargetFragment to communicate back.

            // check if dialog is already showing
            if (getParentFragmentManager().findFragmentByTag(
                    TriStateMultiSelectListPreference.TSMSLPreferenceDialogFragment.TAG) == null) {
                TriStateMultiSelectListPreference.TSMSLPreferenceDialogFragment
                        .launch(this, (TriStateMultiSelectListPreference) preference);
            }
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }
}
