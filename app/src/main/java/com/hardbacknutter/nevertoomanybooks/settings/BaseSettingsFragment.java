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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.dialogs.DBSDialogFactory;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

public abstract class BaseSettingsFragment
        extends BaseFragment {

    private static final String TAG = "BaseSettingsFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";

    /** Default handler. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    popBackStackOrFinish();
                }
            };

    private SettingsManager settingsManager;

    @NonNull
    protected abstract SettingsManager.Builder onCreateSettings();

    @NonNull
    protected SettingsManager getSettingsManager() {
        return settingsManager;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView recyclerView = Objects.requireNonNull(view.findViewById(R.id.settings));

        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(recyclerView);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        this.settingsManager = onCreateSettings()
                //.setDialogFactory(new DBSDialogFactory())
                .build(this, recyclerView);
    }

    @Override
    public void onResume() {
        super.onResume();
        scrollToKey();
    }

    /**
     * Should be called instead of direct calls to popBackStack/finish.
     * This will make sure the current fragment can be the top-fragment (then finish)
     * or be called from another fragment (then pop).
     *
     * @see #backPressedCallback
     */
    @CallSuper
    protected void popBackStackOrFinish() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            final FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    }

    /**
     * Check the Fragment arguments for the {@link #BKEY_AUTO_SCROLL_TO_KEY}
     * and scroll the display if applicable.
     */
    @SuppressWarnings("WeakerAccess")
    protected void scrollToKey() {
        final Bundle args = getArguments();
        if (args != null) {
            final String autoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
            if (autoScrollToKey != null) {
                settingsManager.scrollToKey(autoScrollToKey);
                // we're only scrolling ONCE
                args.remove(BKEY_AUTO_SCROLL_TO_KEY);
            }
        }
    }
}
