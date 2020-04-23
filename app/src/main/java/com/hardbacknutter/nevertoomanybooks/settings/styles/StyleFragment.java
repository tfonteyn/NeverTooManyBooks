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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

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

    @Override
    @XmlRes
    protected int getLayoutId() {
        return R.xml.preferences_style;
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        Preference thumbScale = findPreference(Prefs.pk_style_scale_thumbnail);
        if (thumbScale != null) {
            thumbScale.setDependency(Prefs.pk_style_book_show_thumbnails);
        }

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        Preference preference;

        // the 'groups' in use.
        preference = findPreference(Prefs.pk_style_groups);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), StyleGroupsActivity.class);
                intent.putExtra(BooklistStyle.BKEY_STYLE, mStyle);
                startActivityForResult(intent, RequestCode.EDIT_STYLE_GROUPS);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        PreferenceScreen screen = getPreferenceScreen();
        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        // Use the global style to get the groups.
        //noinspection ConstantConditions
        BooklistStyle style = new BooklistStyle(getContext());
        for (BooklistGroup group : BooklistGroup.getAllGroups(style)) {
            group.setPreferencesVisible(screen, mStyle.containsGroup(group.getId()));
        }

        super.onResume();

        // These keys are never physically present in the SharedPreferences; so handle explicitly.
        updateSummary(Prefs.PSK_STYLE_SHOW_DETAILS);
        updateSummary(Prefs.PSK_STYLE_FILTERS);
    }

    /**
     * Update summary texts.
     * <p>
     * Reminder: prefs lookups can return {@code null} as the screen swaps in and out sub screens.
     */
    @Override
    protected void updateSummary(@NonNull final String key) {

        switch (key) {
            case Prefs.pk_style_scale_font: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    String summary = getResources()
                            .getStringArray(R.array.pe_bob_text_scale)
                            [mStyle.getTextScale(getContext())];
                    preference.setSummary(summary);
                }
                break;
            }
            case Prefs.pk_style_scale_thumbnail: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    String summary = getResources()
                            .getStringArray(R.array.pe_bob_thumbnail_scale)
                            [mStyle.getThumbnailScale(getContext())];
                    preference.setSummary(summary);
                }
                break;
            }

            case Prefs.pk_style_levels_expansion: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    preference.setMax(mStyle.getGroupCount());
                    //noinspection ConstantConditions
                    preference.setSummary(String.valueOf(mStyle.getTopLevel(getContext())));
                }
                break;
            }

            case Prefs.pk_style_groups: {
                // the 'groups' in use.
                Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getGroupLabels(getContext()));
                }
                break;
            }

            case Prefs.pk_style_book_show_thumbnails:
            case Prefs.PSK_STYLE_SHOW_DETAILS: {
                // the 'extra' fields in use.
                Preference preference = findPreference(Prefs.PSK_STYLE_SHOW_DETAILS);
                if (preference != null) {
                    //noinspection ConstantConditions
                    List<String> labels = mStyle.getBookDetailsFieldLabels(getContext());
                    if (labels.isEmpty()) {
                        preference.setSummary(getString(R.string.none));
                    } else {
                        preference.setSummary(TextUtils.join(", ", labels));
                    }
                }
                break;
            }
            case Prefs.PSK_STYLE_FILTERS: {
                // the 'filters' in use
                Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    List<String> labels = mStyle.getFilterLabels(getContext(), false);
                    if (labels.isEmpty()) {
                        preference.setSummary(getString(R.string.none));
                    } else {
                        preference.setSummary(TextUtils.join(", ", labels));
                    }
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
                    Objects.requireNonNull(mStyle, ErrorMsg.ARGS_MISSING_STYLE);

                    mResultDataModel.putResultData(BooklistStyle.BKEY_STYLE_MODIFIED, true);
                    mResultDataModel.putResultData(BooklistStyle.BKEY_STYLE, mStyle);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
