/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFiltersFragment;

/**
 * Handles a secondary preference key (originalKey + ".active")
 * to enable/disabled the actual preference.
 * <p>
 * See {@link StyleFiltersFragment}.
 */
public class BitmaskPreference
        extends MultiSelectListPreference {

    /** See {@link com.hardbacknutter.nevertoomanybooks.booklist.filters.BitmaskFilter}. */
    private static final String ACTIVE = ".active";

    /** The summary resource to display if the preference is set to "don't use". */
    @StringRes
    private int mNotSetSummary = R.string.unused;
    @Nullable
    private Boolean mActive;

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr,
                             final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public BitmaskPreference(@NonNull final Context context) {
        super(context);
    }

    /**
     * The summary to display when the value is <strong>not set</strong>.
     * This is different from when the value is set to all-blank.
     *
     * @return string
     */
    @NonNull
    public String getNotSetSummary() {
        if (mNotSetSummary != 0) {
            return getContext().getString(mNotSetSummary);
        } else {
            return "";
        }
    }

    /**
     * The summary to display when the value is <strong>not set</strong>.
     * This is different from when the value is set to all-blank.
     *
     * @param notSetSummary string resource id
     */
    public void setNotSetSummary(@StringRes final int notSetSummary) {
        mNotSetSummary = notSetSummary;
    }

    public boolean isActive() {
        if (mActive == null) {
            mActive = getSharedPreferences().getBoolean(getKey() + ACTIVE, false);
        }
        return mActive;
    }

    public void setActive(final boolean active) {
        mActive = active;
    }

    @Override
    public void setValues(@NonNull final Set<String> values) {
        if (isActive()) {
            getSharedPreferences().edit().putBoolean(getKey() + ACTIVE, true).apply();
            super.setValues(values);
        } else {
            getSharedPreferences().edit().remove(getKey() + ACTIVE).remove(getKey()).apply();
        }
    }

    /**
     * Custom dialog showing a 3rd button with an "unused" to deactivate the entire bitmask.
     * FIXME: prepare a dedicated layout with a 'disable' switch and an info message for the Bitmask
     * <p>
     * Base code taken from {@link MultiSelectListPreferenceDialogFragmentCompat}
     */
    public static class BitmaskPreferenceDialogFragment
            extends PreferenceDialogFragmentCompat {

        private static final String TAG = "BitmaskPreferenceDialog";
        private static final String BKEY_NOT_SET_STRING = TAG + ":notSetStr";

        private static final String SAVE_STATE_VALUES = TAG + ":values";
        private static final String SAVE_STATE_CHANGED = TAG + ":changed";
        private static final String SAVE_STATE_ENTRIES = TAG + ":entries";
        private static final String SAVE_STATE_ENTRY_VALUES = TAG + ":entryValues";

        private final Set<String> mNewValues = new HashSet<>();
        private boolean mPreferenceChanged;
        private CharSequence[] mEntries;
        private CharSequence[] mEntryValues;

        /** Text for the neutral button. */
        @Nullable
        private String mNotSetString;

        /**
         * Set to {@code true} when the user clicks the NeutralButton.
         * Passed to the actual preference in {@link #onDialogClosed(boolean)}.
         */
        private boolean mUnused;

        /**
         * Constructor.
         * <p>
         * The 'hostFragment' will be set as the target fragment.
         * This is a requirement of {@link PreferenceDialogFragmentCompat}.
         *
         * @param hostFragment the fragment which is hosting the preference.
         * @param preference   for this dialog
         *
         * @return instance
         */
        public static DialogFragment newInstance(@NonNull final Fragment hostFragment,
                                                 @NonNull final BitmaskPreference preference) {
            final DialogFragment frag = new BitmaskPreferenceDialogFragment();
            final Bundle args = new Bundle(2);
            args.putString(ARG_KEY, preference.getKey());
            args.putString(BKEY_NOT_SET_STRING, preference.getNotSetSummary());
            frag.setArguments(args);

            // required by PreferenceDialogFragmentCompat
            //noinspection deprecation
            frag.setTargetFragment(hostFragment, 0);
            return frag;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = getArguments();
            if (args != null) {
                mNotSetString = args.getString(BKEY_NOT_SET_STRING);
            }
            if (mNotSetString == null) {
                mNotSetString = getString(R.string.unused);
            }

            if (savedInstanceState == null) {
                final MultiSelectListPreference preference = getListPreference();

                if (preference.getEntries() == null || preference.getEntryValues() == null) {
                    throw new IllegalStateException(
                            "MultiSelectListPreference requires an entries array and "
                            + "an entryValues array.");
                }

                mNewValues.clear();
                mNewValues.addAll(preference.getValues());
                mPreferenceChanged = false;
                mEntries = preference.getEntries();
                mEntryValues = preference.getEntryValues();
            } else {
                mNewValues.clear();
                //noinspection ConstantConditions
                mNewValues.addAll(savedInstanceState.getStringArrayList(SAVE_STATE_VALUES));
                mPreferenceChanged = savedInstanceState.getBoolean(SAVE_STATE_CHANGED, false);
                mEntries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES);
                mEntryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putStringArrayList(SAVE_STATE_VALUES, new ArrayList<>(mNewValues));
            outState.putBoolean(SAVE_STATE_CHANGED, mPreferenceChanged);
            outState.putCharSequenceArray(SAVE_STATE_ENTRIES, mEntries);
            outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, mEntryValues);
        }

        @NonNull
        private BitmaskPreference getListPreference() {
            return (BitmaskPreference) getPreference();
        }

        @Override
        protected void onPrepareDialogBuilder(@NonNull final AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            // FIXME: the default builder uses a mutually exclusive message/listView.
            // builder.setMessage(getString(R.string.info_bitmask_preference,
            //                    getString(mNotSetString)));
            builder.setNeutralButton(mNotSetString, (d, w) -> mUnused = true);

            final int entryCount = mEntryValues.length;
            final boolean[] checkedItems = new boolean[entryCount];
            for (int i = 0; i < entryCount; i++) {
                checkedItems[i] = mNewValues.contains(mEntryValues[i].toString());
            }
            builder.setMultiChoiceItems(mEntries, checkedItems, (dialog, which, isChecked) -> {
                if (isChecked) {
                    mPreferenceChanged |= mNewValues.add(mEntryValues[which].toString());
                } else {
                    mPreferenceChanged |= mNewValues.remove(mEntryValues[which].toString());
                }
            });
        }

        @Override
        public void onDialogClosed(final boolean positiveResult) {
            final BitmaskPreference preference = getListPreference();

            // a tat annoying... we only get whether the user clicked the BUTTON_POSITIVE
            // or not; which is why we need to use the mUnused variable.
            if (positiveResult) {
                // BUTTON_POSITIVE
                preference.setActive(true);
            } else if (mUnused) {
                // BUTTON_NEUTRAL
                preference.setActive(false);
                preference.getValues().clear();
            }

            if (positiveResult || mUnused) {
                if (preference.callChangeListener(mNewValues)) {
                    preference.setValues(mNewValues);
                }
            }

            mPreferenceChanged = false;
        }
    }
}
