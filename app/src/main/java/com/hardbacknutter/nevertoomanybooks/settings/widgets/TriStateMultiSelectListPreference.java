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
package com.hardbacknutter.nevertoomanybooks.settings.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.BitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.widgets.ChecklistRecyclerAdapter;

/**
 * Allows a user to select 0 or more checkboxes or use the neutral-button to disregard
 * the entire setting.
 * <p>
 * The latter is handled with a secondary preference key (originalKey + ".active")
 * to enable/disabled the actual preference.
 *
 * <pre>
 *     {@code
 *         <declare-styleable name="TriStateMultiSelectListPreference">
 *           <attr name="neutralButtonText" format="string" />
 *           <attr name="neutralButtonSummaryText" format="string" />
 *         </declare-styleable>
 *     }
 * </pre>
 */
@SuppressWarnings("unused")
public class TriStateMultiSelectListPreference
        extends MultiSelectListPreference {

    /** See {@link BitmaskFilter}. */
    private static final String ACTIVE = ".active";

    /** The text to use for the neutral button, which allows the user to choose "don't use". */
    @Nullable
    private String mNeutralButtonText;

    /**
     * The summary text to display if the preference is set to "don't use".
     * If not set, then the text from {@link #mNeutralButtonText} will be used.
     */
    @Nullable
    private String mNeutralButtonSummaryText;

    @Nullable
    private Boolean mActive;

    public TriStateMultiSelectListPreference(@NonNull final Context context,
                                             @Nullable final AttributeSet attrs,
                                             final int defStyleAttr,
                                             final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public TriStateMultiSelectListPreference(@NonNull final Context context,
                                             @Nullable final AttributeSet attrs,
                                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public TriStateMultiSelectListPreference(@NonNull final Context context,
                                             @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TriStateMultiSelectListPreference(@NonNull final Context context) {
        super(context);
    }

    public void init(@NonNull final Context context,
                     @Nullable final AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray ta = context.getTheme().obtainStyledAttributes(
                    attrs, R.styleable.TriStateMultiSelectListPreference, 0, 0);
            try {
                mNeutralButtonText = ta.getString(
                        R.styleable.TriStateMultiSelectListPreference_neutralButtonText);
                mNeutralButtonSummaryText = ta.getString(
                        R.styleable.TriStateMultiSelectListPreference_neutralButtonSummaryText);
            } finally {
                ta.recycle();
            }
        }
    }

    /**
     * The summary to display when the value is <strong>not set</strong>.
     * This is different from when the value is set to all-blank.
     *
     * @return string, can be {@code null}
     */
    @Nullable
    private String getNeutralButtonSummaryText() {
        if (mNeutralButtonSummaryText != null) {
            return mNeutralButtonSummaryText;
        } else if (mNeutralButtonText != null) {
            return mNeutralButtonText;
        } else {
            return null;
        }
    }

    /**
     * The text to display on the neutral button.
     *
     * @return string, can be {@code null}
     */
    @Nullable
    private String getNeutralButtonText() {
        if (mNeutralButtonText != null) {
            return mNeutralButtonText;
        } else {
            return null;
        }
    }

    public boolean isActive() {
        if (mActive == null) {
            //noinspection ConstantConditions
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
            //noinspection ConstantConditions
            getSharedPreferences().edit().putBoolean(getKey() + ACTIVE, true).apply();
        } else {
            //noinspection ConstantConditions
            getSharedPreferences().edit().remove(getKey() + ACTIVE).remove(getKey()).apply();
        }
        super.setValues(values);
    }

    /**
     * Custom dialog showing a 3rd button with an "unused" to deactivate the entire bitmask.
     * <p>
     * Base code taken from {@link MultiSelectListPreferenceDialogFragmentCompat}
     * <p>
     * We're using a custom dialog so we can have a checklist AND a message.
     * <p>
     * For reference, {@link PreferenceDialogFragmentCompat#onCreateDialog} runs this code:
     * <pre>
     *     {@code
     *         View contentView = onCreateDialogView(context);
     *         onBindDialogView(contentView);
     *         builder.setView(contentView);
     *         onPrepareDialogBuilder(builder);
     *         }
     * </pre>
     */
    public static class TSMSLPreferenceDialogFragment
            extends PreferenceDialogFragmentCompat {

        public static final String TAG = "BitmaskPreferenceDialog";

        private static final String SAVE_STATE_VALUES = TAG + ":values";
        private static final String SAVE_STATE_CHANGED = TAG + ":changed";
        private static final String SAVE_STATE_ENTRIES = TAG + ":entries";
        private static final String SAVE_STATE_ENTRY_VALUES = TAG + ":entryValues";

        private final Set<String> mSelectedItems = new HashSet<>();
        private boolean mPreferenceChanged;

        private ArrayList<String> mEntryValues;
        private ArrayList<String> mEntries;

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
         */
        public static void launch(@NonNull final Fragment hostFragment,
                                  @NonNull final TriStateMultiSelectListPreference preference) {
            final Bundle args = new Bundle(1);
            args.putString(ARG_KEY, preference.getKey());

            final DialogFragment frag = new TSMSLPreferenceDialogFragment();
            frag.setArguments(args);
            // Using setTargetFragment + getParentFragmentManager
            // which is required by PreferenceDialogFragmentCompat
            // as the latter insists on using this to communicate the results back to us.
            //noinspection deprecation
            frag.setTargetFragment(hostFragment, 0);
            frag.show(hostFragment.getParentFragmentManager(), TAG);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (savedInstanceState == null) {
                final MultiSelectListPreference preference =
                        (MultiSelectListPreference) getPreference();

                if (preference.getEntries() == null || preference.getEntryValues() == null) {
                    throw new IllegalStateException(
                            "MultiSelectListPreference requires an entries array and "
                            + "an entryValues array.");
                }

                mEntries = Arrays.stream(preference.getEntries())
                                 .map(CharSequence::toString)
                                 .collect(Collectors.toCollection(ArrayList::new));

                mEntryValues = Arrays.stream(preference.getEntryValues())
                                     .map(CharSequence::toString)
                                     .collect(Collectors.toCollection(ArrayList::new));

                mPreferenceChanged = false;
                mSelectedItems.clear();
                mSelectedItems.addAll(preference.getValues());
            } else {
                //noinspection ConstantConditions
                mEntries = savedInstanceState.getStringArrayList(SAVE_STATE_ENTRIES);
                //noinspection ConstantConditions
                mEntryValues = savedInstanceState.getStringArrayList(SAVE_STATE_ENTRY_VALUES);
                mPreferenceChanged = savedInstanceState.getBoolean(SAVE_STATE_CHANGED, false);
                mSelectedItems.clear();
                //noinspection ConstantConditions
                mSelectedItems.addAll(savedInstanceState.getStringArrayList(SAVE_STATE_VALUES));
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putStringArrayList(SAVE_STATE_ENTRIES, mEntries);
            outState.putStringArrayList(SAVE_STATE_ENTRY_VALUES, mEntryValues);
            outState.putBoolean(SAVE_STATE_CHANGED, mPreferenceChanged);
            outState.putStringArrayList(SAVE_STATE_VALUES, new ArrayList<>(mSelectedItems));
        }

        @SuppressLint("InflateParams")
        @Override
        protected View onCreateDialogView(@NonNull final Context context) {
            return getLayoutInflater().inflate(R.layout.dialog_bitmask_preference, null);
        }

        @Override
        protected void onBindDialogView(@NonNull final View view) {
            super.onBindDialogView(view);

            final TextView messageView = view.findViewById(R.id.message);
            final CharSequence message = getPreference().getDialogMessage();
            if (message != null && message.length() > 0) {
                messageView.setText(message);
                messageView.setVisibility(View.VISIBLE);
            } else {
                messageView.setVisibility(View.GONE);
            }

            final ChecklistRecyclerAdapter<String, String> adapter =
                    new ChecklistRecyclerAdapter<>(view.getContext(),
                                                   mEntryValues, mEntries, mSelectedItems,
                                                   (id, checked) -> mPreferenceChanged = true);

            final RecyclerView listView = view.findViewById(R.id.item_list);
            listView.setAdapter(adapter);
        }

        @Override
        protected void onPrepareDialogBuilder(@NonNull final AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            final String neutralText = ((TriStateMultiSelectListPreference) getPreference())
                    .getNeutralButtonText();
            if (neutralText != null) {
                builder.setNeutralButton(neutralText, (d, w) -> mUnused = true);
            }
        }

        @Override
        public void onDialogClosed(final boolean positiveResult) {
            final TriStateMultiSelectListPreference preference =
                    (TriStateMultiSelectListPreference) getPreference();

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
                if (preference.callChangeListener(mSelectedItems)) {
                    preference.setValues(mSelectedItems);
                }
            }

            mPreferenceChanged = false;
        }
    }


    /**
     * A simple {@link androidx.preference.Preference.SummaryProvider} implementation for a
     * {@link TriStateMultiSelectListPreference}.
     */
    public static final class SimpleSummaryProvider
            implements SummaryProvider<TriStateMultiSelectListPreference> {

        private static TriStateMultiSelectListPreference.SimpleSummaryProvider
                sSimpleSummaryProvider;

        private SimpleSummaryProvider() {
        }

        /**
         * Retrieve a singleton instance of this simple
         * {@link androidx.preference.Preference.SummaryProvider} implementation.
         *
         * @return a singleton instance of this simple
         *         {@link androidx.preference.Preference.SummaryProvider} implementation
         */
        public static TriStateMultiSelectListPreference.SimpleSummaryProvider getInstance() {
            if (sSimpleSummaryProvider == null) {
                sSimpleSummaryProvider =
                        new TriStateMultiSelectListPreference.SimpleSummaryProvider();
            }
            return sSimpleSummaryProvider;
        }

        @Override
        @Nullable
        public CharSequence provideSummary(@NonNull final TriStateMultiSelectListPreference
                                                   preference) {
            if (preference.isActive()) {
                // if it is active, drop through to MultiSelectListPreference
                return MultiSelectListPreferenceSummaryProvider
                        .getInstance().provideSummary(preference);
            } else {
                return preference.getNeutralButtonSummaryText();
            }
        }
    }
}
