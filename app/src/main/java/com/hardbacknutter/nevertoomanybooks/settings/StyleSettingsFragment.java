/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleSettingsFragment
        extends BaseSettingsFragment {

    /** Fragment manager tag. */
    public static final String TAG = "StyleSettingsFragment";

    static final String BKEY_TEMPLATE_ID = TAG + ":templateId";

    /** Style we are editing. */
    private BooklistStyle mStyle;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        Bundle args = getArguments();
        if (args != null) {
            mStyle = args.getParcelable(UniqueId.BKEY_STYLE);
        }

        if (mStyle == null) {
            // we're doing the global preferences
            mStyle = new BooklistStyle();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debugEnter(this, "onCreatePreferences", mStyle);
        }

        // We use the style UUID as the filename for the prefs.
        String uuid = mStyle.getUuid();
        if (!uuid.isEmpty()) {
            getPreferenceManager().setSharedPreferencesName(uuid);
        }
        // else if uuid.isEmpty(), use global SharedPreferences for editing global defaults

        setPreferencesFromResource(R.xml.preferences_book_style, rootKey);

        PreferenceScreen screen = getPreferenceScreen();

        // TODO: use this for all prefs instead of doing this in our base class.
//        EditTextPreference np = screen.findPreference(Prefs.X));
//        np.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());

        // add the preferences from all groups:
        for (BooklistGroup group : mStyle.getGroups()) {
            group.addPreferencesTo(screen);
        }

        @SuppressWarnings("ConstantConditions")
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (mStyle.getId() == 0) {
                actionBar.setTitle(R.string.title_clone_style);
            } else {
                actionBar.setTitle(R.string.title_edit_style);
            }
            //noinspection ConstantConditions
            actionBar.setSubtitle(mStyle.getLabel(getContext()));
        }

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_booklist_style_properties, null);
        }

        // Some PreferenceScreen use a click listener.
        initClickListeners();
        // Set the summaries reflecting the current values for all basic Preferences.
        updateSummaries(screen);
        // and for a set of special ones. See method doc.
        updateThisScreen();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // pass the non-global style back; whether existing or new.
        if (!mStyle.getUuid().isEmpty()) {
            mResultDataModel.putExtra(UniqueId.BKEY_STYLE, mStyle);
        }

        // pass the template id back; not currently used here.
        Bundle args = getArguments();
        if (args != null) {
            mResultDataModel.putExtra(BKEY_TEMPLATE_ID, args.getLong(BKEY_TEMPLATE_ID));
        }
    }

    /**
     * Update the local summaries after a change and set the activity result.
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        updateThisScreen();

        // set the result (and again and again...)
        mResultDataModel.putExtra(UniqueId.BKEY_STYLE_MODIFIED, true);
    }

    @Override
    public void onResume() {
        super.onResume();

        updateThisScreen();
    }

    /**
     * Hook up a {@link Preference.OnPreferenceClickListener} to start a dedicated activity.
     */
    private void initClickListeners() {
        Preference preference;

        // the 'groups' in use.
        preference = findPreference(Prefs.psk_style_groupings);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), StyleGroupsActivity.class);
                intent.putExtra(UniqueId.BKEY_STYLE, mStyle);
                startActivityForResult(intent, UniqueId.REQ_EDIT_STYLE_GROUPS);
                return true;
            });
        }
    }

    /**
     * Update non-standard summary texts.
     *
     * <ul>
     * <li>hide/show the "Series" category</li>
     * <li>filter labels</li>
     * <li>extras labels</li>
     * <li>group labels</li>
     * </ul>
     * <p>
     * Reminder: prefs lookups can return {@code null} as the screen swaps in and out sub screens.
     */
    private void updateThisScreen() {
        Preference preference;
        List<String> labels;

        // the 'extra' fields in use.
        preference = findPreference(Prefs.psk_style_show_details);
        if (preference != null) {
            //noinspection ConstantConditions
            labels = mStyle.getExtraFieldsLabels(getContext());
            if (labels.isEmpty()) {
                preference.setSummary(getString(R.string.none));
            } else {
                preference.setSummary(TextUtils.join(", ", labels));
            }
        }

        // the 'filters' in use
        preference = findPreference(Prefs.psk_style_filters);
        if (preference != null) {
            //noinspection ConstantConditions
            labels = mStyle.getFilterLabels(getContext(), false);
            if (labels.isEmpty()) {
                preference.setSummary(getString(R.string.none));
            } else {
                preference.setSummary(TextUtils.join(", ", labels));
            }
        }

        // the 'groups' in use.
        preference = findPreference(Prefs.psk_style_groupings);
        if (preference != null) {
            //noinspection ConstantConditions
            preference.setSummary(mStyle.getGroupLabels(getContext()));
        }

        // The "Series" category has no settings of its own (in contrast to "Authors").
        // So unless the group is included, we hide the "Series" category.
        preference = findPreference(Prefs.psk_style_series);
        if (preference != null) {
            preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.SERIES));
        }

        // always visible
//        preference = findPreference(Prefs.psk_style_author);
//        if (preference != null) {
//            preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.AUTHOR));
//        }
    }


    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_EDIT_STYLE_GROUPS:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    // replace the current style with the edited copy
                    mStyle = Objects.requireNonNull(data.getParcelableExtra(UniqueId.BKEY_STYLE));
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
