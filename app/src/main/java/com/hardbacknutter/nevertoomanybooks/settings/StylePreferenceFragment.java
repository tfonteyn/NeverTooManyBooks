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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StylePreferenceFragment
        extends StyleBaseFragment {

    @Override
    @XmlRes
    protected int getLayoutId() {
        return R.xml.preferences_styles;
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        // add the preferences from all groups:
        PreferenceScreen screen = getPreferenceScreen();
        for (BooklistGroup group : mStyle.getGroups()) {
            group.addPreferencesTo(screen);
        }

        Preference thumbScale = findPreference(Prefs.pk_bob_thumbnail_scale);
        if (thumbScale != null) {
            thumbScale.setDependency(Prefs.pk_bob_show_thumbnails);
        }

        initListeners();

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        // the 'groups' in use.
        preference = findPreference(Prefs.pk_bob_groups);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), StyleGroupsActivity.class);
                intent.putExtra(UniqueId.BKEY_STYLE, mStyle);
                startActivityForResult(intent, UniqueId.REQ_EDIT_STYLE_GROUPS);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // handle custom summaries
        updateSummary(Prefs.psk_style_show_details);
        updateSummary(Prefs.psk_style_filters);
        updateSummary(Prefs.psk_style_series);
//        updateSummary(Prefs.psk_style_author);
    }

    /**
     * Update summary texts.
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
    @Override
    void updateSummary(@NonNull final String key) {

        switch (key) {
            case Prefs.pk_bob_font_scale: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    String summary = getResources()
                            .getStringArray(R.array.pe_bob_text_scale)[mStyle.getTextScale()];
                    preference.setSummary(summary);
                }
                break;
            }
            case Prefs.pk_bob_thumbnail_scale: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    String summary = getResources()
                            .getStringArray(R.array.pe_bob_thumbnail_scale)[mStyle
                            .getThumbnailScale()];
                    preference.setSummary(summary);
                }
                break;
            }

            case Prefs.pk_bob_levels_default: {
                SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    preference.setMax(mStyle.getGroups().size());
                    preference.setSummary(String.valueOf(mStyle.getTopLevel()));
                }
                break;
            }

            case Prefs.pk_bob_groups: {
                // the 'groups' in use.
                Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(mStyle.getGroupLabels(getContext()));
                }
                break;
            }

            case Prefs.pk_bob_show_thumbnails: {
                updateSummary(Prefs.psk_style_show_details);
                break;
            }

            case Prefs.psk_style_show_details: {
                // the 'extra' fields in use.
                Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    List<String> labels = mStyle.getExtraFieldsLabels(getContext());
                    if (labels.isEmpty()) {
                        preference.setSummary(getString(R.string.none));
                    } else {
                        preference.setSummary(TextUtils.join(", ", labels));
                    }
                }
                break;
            }
            case Prefs.psk_style_filters: {
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
            case Prefs.psk_style_series: {
                // The "Series" category has no settings of its own (in contrast to "Authors").
                // So unless the group is included, we hide the "Series" category.
                Preference preference = findPreference(key);
                if (preference != null) {
                    preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.SERIES));
                }
                break;
            }
//            case Prefs.psk_style_author: {
//                Preference preference = findPreference(key);
//                if (preference != null) {
//                    preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.AUTHOR));
//                }
//                break;
//            }

            default:
                super.updateSummary(key);
                break;
        }
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
                    mResultDataModel.putExtra(UniqueId.BKEY_STYLE_MODIFIED, true);
                    mResultDataModel.putExtra(UniqueId.BKEY_STYLE, mStyle);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
