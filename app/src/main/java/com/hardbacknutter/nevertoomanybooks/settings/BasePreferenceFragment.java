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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.dialogs.PreferenceDialogFactory;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;
import com.hardbacknutter.nevertoomanybooks.utils.Delay;

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
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";
    private static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";
    @Nullable
    private View progressFrame;
    @Nullable
    private Toolbar toolbar;

    @NonNull
    protected View getProgressFrame() {
        if (progressFrame == null) {
            //noinspection DataFlowIssue
            progressFrame = Objects.requireNonNull(getActivity().findViewById(R.id.progress_frame),
                                                   "R.id.progress_frame");
        }
        return progressFrame;
    }

    @NonNull
    protected Toolbar getToolbar() {
        if (toolbar == null) {
            //noinspection DataFlowIssue
            toolbar = Objects.requireNonNull(getActivity().findViewById(R.id.toolbar),
                                             "R.id.toolbar");
        }
        return toolbar;
    }

    @Override
    @CallSuper
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
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
            final FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final Bundle args = getArguments();
        if (args != null) {
            final String autoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
            if (autoScrollToKey != null) {
                final Preference preference = findPreference(autoScrollToKey);
                if (preference != null) {
                    scrollToPreference(preference);
                }
                // we're only scrolling ONCE
                args.remove(BKEY_AUTO_SCROLL_TO_KEY);
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
                                             @NonNull final Preference pref) {

        final FragmentManager fm = getParentFragmentManager();

        /* Instantiate the new Fragment
         *
         * Proguard rule needed:
         * -keep public class * extends androidx.preference.PreferenceFragmentCompat
         * or use @Keep annotation on individual fragments
         */
        //noinspection DataFlowIssue
        final Fragment fragment =
                fm.getFragmentFactory().instantiate(getContext().getClassLoader(),
                                                    pref.getFragment());

        // combine the original extras with any new ones (the latter can override the former)
        Bundle args = getArguments();
        if (args == null) {
            args = pref.getExtras();
        } else {
            args.putAll(pref.getExtras());
        }
        fragment.setArguments(args);

        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(fragment.getTag())
          .replace(R.id.main_fragment, fragment)
          .commit();
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {

        // check if dialog is already showing
        if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f = PreferenceDialogFactory.create(preference);
        // Don't blame me... this is the androidx.preference requirement
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
    }

    /**
     * Initialise a field with an editable url.
     * The summary will indicate if the URL is valid or not.
     *
     * @param pHostUrl the preference to init
     *
     * @return the validator
     */
    @NonNull
    protected HostUrlValidator initHostUrlPreference(@NonNull final EditTextPreference pHostUrl) {
        final HostUrlValidator hostUrlValidator = new HostUrlValidator();
        pHostUrl.setSummaryProvider(hostUrlValidator);
        pHostUrl.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });

        return hostUrlValidator;
    }

    /**
     * Copied from {@link com.hardbacknutter.nevertoomanybooks.BaseFragment} - keep in sync.
     *
     * @param message to show
     */
    void showMessageAndFinishActivity(@NonNull final CharSequence message) {
        final View view = getView();
        // Can be null in race conditions.
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            view.postDelayed(() -> {
                // Can be null in race conditions.
                // i.e. the user cancelled which got us here, and then very quickly taps 'back'
                // before we get here.
                final FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.finish();
                }
            }, Delay.LONG_MS);
        }
    }
}
