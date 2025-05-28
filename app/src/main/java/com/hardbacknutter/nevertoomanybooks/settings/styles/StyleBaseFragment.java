/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.GroupPrefs;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.MultiSelectListPreferenceSummaryProvider;

/**
 * Base fragment for editing a Style, or the Style defaults.
 */
public abstract class StyleBaseFragment
        extends BasePreferenceFragment {

    private static final String PSK_LIST_BOOK_LEVEL_FIELDS = "psk_style_book_level_fields";
    private static final String PSK_LIST_BOOK_LEVEL_SORTING = "psk_style_book_level_sorting";

    private static final String PSK_LIST_BOOK_SHOW_COVER_0 = "style.booklist.show.thumbnails";
    @NonNull
    private final SwitchPreference[] pShowCoversOnDetailsScreen = new SwitchPreference[2];
    StyleViewModel vm;
    EditTextPreference pName;
    SeekBarPreference pExpansionLevel;
    Preference pGroups;

    private Preference pCoverLongClick;

    private SeekBarPreference pCoverScale;
    private SeekBarPreference pTextScale;
    private Preference pListBookLevelSorting;
    private Preference pListBookLevelFields;
    private SwitchPreference pShowCovers;

    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), requireArguments());

        // redirect storage to the database
        // This MUST be done in onCreate/onCreatePreferences
        // and BEFORE we inflate the xml screen definition
        getPreferenceManager().setPreferenceDataStore(vm.getStyleDataStore());

        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_style, rootKey);

        pName = findPreference(StyleDataStore.PK_NAME);
        //noinspection DataFlowIssue
        pName.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.selectAll();
        });

        pGroups = findPreference(StyleDataStore.PK_GROUPS);
        pExpansionLevel = findPreference(StyleDataStore.PK_EXPANSION_LEVEL);

        // List layout
        final Preference pLayout = findPreference(StyleDataStore.PK_LAYOUT);
        //noinspection DataFlowIssue
        pLayout.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof String) {
                updateLayoutPrefs();
                return true;
            }
            return false;
        });

        // Used only in grid-mode, hidden in list-mode
        pCoverLongClick = findPreference(StyleDataStore.PK_COVER_LONG_CLICK_ACTION);

        // For simplicity, we always show this option even if the user hides all covers
        // with the "pShowCovers" option.
        pCoverScale = findPreference(StyleDataStore.PK_COVER_SCALE);

        pTextScale = findPreference(StyleDataStore.PK_TEXT_SCALE);

        //noinspection DataFlowIssue
        findPreference(StyleDataStore.PK_LIST_HEADER)
                .setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());

        pListBookLevelFields = findPreference(PSK_LIST_BOOK_LEVEL_FIELDS);
        pShowCovers = findPreference(PSK_LIST_BOOK_SHOW_COVER_0);

        pListBookLevelSorting = findPreference(PSK_LIST_BOOK_LEVEL_SORTING);

        // Book details page
        pShowCoversOnDetailsScreen[0] = findPreference(StyleDataStore.PK_DETAILS_SHOW_COVER[0]);
        pShowCoversOnDetailsScreen[1] = findPreference(StyleDataStore.PK_DETAILS_SHOW_COVER[1]);
        pShowCoversOnDetailsScreen[0].setOnPreferenceChangeListener((preference, newValue) -> {
            // Covers on DETAIL screen:
            // Setting cover 0 to false
            if (newValue instanceof Boolean && !(Boolean) newValue) {
                // ==> set cover 1 to false as well
                pShowCoversOnDetailsScreen[1].setChecked(false);
            }
            return true;
        });

        // Author
        //noinspection DataFlowIssue
        findPreference(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE)
                .setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());

        // First call sets the current situation before the screen is visible.
        updateLayoutPrefs();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm.onModified().observe(getViewLifecycleOwner(), aVoid -> {
            updateSummaries();
            updateLayoutPrefs();
        });
        vm.onNameNotUnique().observe(getViewLifecycleOwner(), this::onNameNotUnique);
    }

    @Override
    public void onResume() {
        super.onResume();

        updatePreferenceVisibility();
        updateSummaries();
        updateLayoutPrefs();
    }

    /**
     * Show the preferences for groups we have and hide for groups we don't/no longer have.
     * When one preference is visible, make the category visible.
     */
    private void updatePreferenceVisibility() {
        final WritableStyle style = vm.getStyle();

        final PreferenceScreen screen = getPreferenceScreen();
        BooklistGroup.getAllGroups(style).forEach(
                group -> {
                    final GroupPrefs groupPrefs = group.getGroupPrefs();
                    // Not all groups have preferences
                    if (groupPrefs == null) {
                        return;
                    }
                    final PreferenceCategory category =
                            screen.findPreference(groupPrefs.getCategory());
                    // We may implement plumbing, but not want to expose it
                    // to the user (yet)
                    if (category == null) {
                        return;
                    }

                    final boolean visible = style.hasGroup(group.getId());
                    for (final String key : groupPrefs.getKeys()) {
                        final Preference preference = category.findPreference(key);
                        if (preference != null) {
                            preference.setVisible(visible);
                        }
                    }

                    int i = 0;
                    while (i < category.getPreferenceCount()) {
                        if (category.getPreference(i).isVisible()) {
                            category.setVisible(true);
                            return;
                        }
                        i++;
                    }
                });
    }

    /**
     * The summary for these Preference's reflect what is selected on ANOTHER screen
     * or changes to ANOTHER KEY.
     * (this also means we cannot use SummaryProvider's for these)
     */
    private void updateSummaries() {
        final WritableStyle style = vm.getStyle();
        final Context context = getContext();

        // List

        //noinspection DataFlowIssue
        pGroups.setSummary(style.getGroupsSummaryText(context));
        // the 'level expansion' depends on the number of groups in use
        pExpansionLevel.setMax(style.getGroupCount());
        pExpansionLevel.setValue(style.getExpansionLevel());
        pListBookLevelSorting.setSummary(vm.getBookLevelSortingPreferenceSummary(context));


        // List layout
        pListBookLevelFields.setSummary(
                createVisibilitySummary(context, style, FieldVisibility.Screen.List));

        pCoverScale.setSummary(style.getCoverScale().getLabel(context));
        pTextScale.setSummary(style.getTextScale().getLabel(context));
    }

    @NonNull
    private String createVisibilitySummary(@NonNull final Context context,
                                           @NonNull final Style style,
                                           @NonNull final FieldVisibility.Screen screen) {
        final String labels = style.getFieldVisibilityKeys(screen, false)
                                   .stream()
                                   .map(key -> MapDBKey.getLabel(context, key)).sorted()
                                   .collect(Collectors.joining(", "));

        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return labels;
        }
    }

    /**
     * Use the given {@link ScreenLayout} to show/hide the applicable preferences.
     *
     * @throws IllegalArgumentException when there is a bug with the enums
     */
    private void updateLayoutPrefs() {
        final ScreenLayout layout = vm.getStyle().getLayout();
        switch (layout) {
            case List: {
                pShowCovers.setVisible(true);
                // N/A in list-mode
                pCoverLongClick.setVisible(false);
                break;
            }
            case Grid: {
                // The point of Grid is to show covers
                pShowCovers.setVisible(false);
                pShowCovers.setChecked(true);

                pCoverLongClick.setVisible(true);
                break;
            }
            default:
                throw new IllegalArgumentException(layout.toString());
        }
    }

    private void onNameNotUnique(@NonNull final CharSequence message) {
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    getPreferenceManager().showDialog(pName);
                })
                .create()
                .show();
    }
}
