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
import android.text.InputType;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;

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

    private View progressFrame;
    private Toolbar toolbar;

    @Nullable
    private String autoScrollToKey;

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
        final Bundle args = getArguments();
        if (args != null) {
            autoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // The child class is responsible to set the toolbar title,
        // but is not obliged to set the subtitle.
        // So we must always erase any existing subtitle!
        getToolbar().setSubtitle("");
    }

    /**
     * Should be called instead of direct calls to popBackStack/finish.
     * This will make sure the current fragment can be the top-fragment (then finish)
     * or be called from another fragment (then pop).
     */
    void popBackStackOrFinish() {
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

        if (autoScrollToKey != null) {
            scrollToPreference(autoScrollToKey);
            autoScrollToKey = null;
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

    /**
     * Initialise a field with an editable url.
     *
     * @param key the preference key to init
     */
    protected void initHostUrlPreference(@NonNull final CharSequence key) {
        final EditTextPreference etp = findPreference(key);
        //noinspection DataFlowIssue
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
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
            }, BaseActivity.DELAY_LONG_MS);
        }
    }
}
