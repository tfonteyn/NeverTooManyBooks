/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.DetailScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleFragment
        extends StyleBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "StylePreferenceFragment";

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_SHOW_DETAILS = "psk_style_show_details";
    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_FILTERS = "psk_style_filters";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        setPreferencesFromResource(R.xml.preferences_style, rootKey);

        // Cover on LIST
        final Preference thumbScale = findPreference(
                ListScreenBookFields.PK_COVER_SCALE);
        if (thumbScale != null) {
            thumbScale.setDependency(ListScreenBookFields.PK_COVERS);
        }

        // Covers on DETAIL screen
        // Setting cover 0 to false -> disable cover 1; also see onSharedPreferenceChanged
        final Preference cover = findPreference(
                DetailScreenBookFields.PK_COVER[1]);
        if (cover != null) {
            cover.setDependency(
                    DetailScreenBookFields.PK_COVER[0]);
        }

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Preference preference;

        // the 'groups' in use.
        preference = findPreference(Groups.PK_STYLE_GROUPS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), StyleGroupsActivity.class)
                        .putExtra(BooklistStyle.BKEY_STYLE, mStyle);
                startActivityForResult(intent, RequestCode.EDIT_STYLE_GROUPS);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        final PreferenceScreen screen = getPreferenceScreen();
        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        // Use the global style to get the groups.
        //noinspection ConstantConditions
        final BooklistStyle style = new BooklistStyle(getContext());
        final Groups styleGroups = mStyle.getGroups();

        for (BooklistGroup group : BooklistGroup.getAllGroups(getContext(), style)) {
            group.setPreferencesVisible(screen, styleGroups.contains(group.getId()));
        }

        super.onResume();

        // These keys are never physically present in the SharedPreferences; so handle explicitly.
        updateSummary(PSK_STYLE_SHOW_DETAILS);
        updateSummary(PSK_STYLE_FILTERS);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {

        // Covers on DETAIL screen
        // Setting cover 0 to false -> set cover 1 to false as well
        if (DetailScreenBookFields.PK_COVER[0].equals(key)
            && !preferences.getBoolean(key, false)) {
            final SwitchPreference cover =
                    findPreference(
                            DetailScreenBookFields.PK_COVER[1]);
            //noinspection ConstantConditions
            cover.setChecked(false);
        }

        super.onSharedPreferenceChanged(preferences, key);
    }

    /**
     * Update summary texts.
     * <p>
     * Reminder: prefs lookups can return {@code null} as the screen swaps in and out sub screens.
     */
    @Override
    protected void updateSummary(@NonNull final String key) {

        switch (key) {
            case TextScale.PK_TEXT_SCALE: {
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getTextScale()
                                                .getFontScaleSummaryText(getContext()));
                }
                break;
            }

            case ListScreenBookFields.PK_COVER_SCALE: {
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getListScreenBookFields()
                                                .getCoverScaleSummaryText(getContext()));
                }
                break;
            }

            case BooklistStyle.PK_LEVELS_EXPANSION: {
                final SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    preference.setMax(mStyle.getGroups().size());
                    //noinspection ConstantConditions
                    preference.setSummary(String.valueOf(mStyle.getTopLevel(getContext())));
                }
                break;
            }

            case Groups.PK_STYLE_GROUPS: {
                // the 'groups' in use.
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getGroups().getSummaryText(getContext()));
                }
                break;
            }

            case ListScreenBookFields.PK_COVERS:
            case PSK_STYLE_SHOW_DETAILS: {
                // the 'extra' fields in use.
                final Preference preference = findPreference(PSK_STYLE_SHOW_DETAILS);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getListScreenBookFields()
                                                .getSummaryText(getContext()));
                }
                break;
            }
            case PSK_STYLE_FILTERS: {
                // the 'filters' in use (i.e. the actives ones)
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getFilters().getSummaryText(getContext(), false));
                }
                break;
            }

            default:
                super.updateSummary(key);
                break;
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.EDIT_STYLE_GROUPS:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // replace the current style with the edited copy;
                    // do NOT save to the database yet/here
                    mStyle = data.getParcelableExtra(BooklistStyle.BKEY_STYLE);
                    Objects.requireNonNull(mStyle, ErrorMsg.NULL_STYLE);

                    mResultData.putResultData(BooklistStyle.BKEY_STYLE_MODIFIED, true);
                    mResultData.putResultData(BooklistStyle.BKEY_STYLE, mStyle);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
