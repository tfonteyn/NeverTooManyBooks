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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.MultiSelectListPreferenceSummaryProvider;

/**
 * Main settings editor for a single style.
 */
public class StyleFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    private static final String TAG = "StylePreferenceFragment";
    private static final String SIS_NAME_SET = TAG + ":nameSet";

    private static final String PSK_LIST_BOOK_LEVEL_FIELDS = "psk_style_book_level_fields";
    private static final String PSK_LIST_BOOK_LEVEL_SORTING = "psk_style_book_level_sorting";

    private static final String PSK_LIST_BOOK_SHOW_COVER_0 = "style.booklist.show.thumbnails";

    private StyleViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final boolean modified = vm.updateOrInsertStyle();

                    final Intent resultIntent = EditStyleContract
                            .createResult(vm.getTemplateUuid(),
                                          modified,
                                          vm.getStyle().getUuid());

                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    @NonNull
    private final SwitchPreference[] pShowCoversOnDetailsScreen = new SwitchPreference[2];
    /** Flag: prompt for the name of cloned styles. */
    private boolean nameSet;
    private EditTextPreference pName;
    private SeekBarPreference pCoverScale;
    private SeekBarPreference pTextScale;
    private SeekBarPreference pExpansionLevel;
    private Preference pListBookLevelSorting;
    private Preference pListBookLevelFields;
    private Preference pGroups;
    private SwitchPreference pShowCovers;

    @SuppressWarnings("DataFlowIssue")
    @Override
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

        if (savedInstanceState != null) {
            nameSet = savedInstanceState.getBoolean(SIS_NAME_SET);
        }

        pName = findPreference(StyleDataStore.PK_NAME);
        pName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        pName.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.selectAll();
        });

        // List
        findPreference(StyleDataStore.PK_LIST_HEADER)
                .setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());
        pGroups = findPreference(StyleDataStore.PK_GROUPS);
        pExpansionLevel = findPreference(StyleDataStore.PK_EXPANSION_LEVEL);
        pListBookLevelSorting = findPreference(PSK_LIST_BOOK_LEVEL_SORTING);

        // List layout
        final Preference pLayout = findPreference(StyleDataStore.PK_LAYOUT);
        pLayout.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        pLayout.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof String) {
                final Style.Layout layout = Style.Layout.byId(Integer.parseInt((String) newValue));
                updateLayoutPrefs(layout);
                return true;
            }
            return false;
        });

        findPreference(StyleDataStore.PK_COVER_CLICK_ACTION)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        pListBookLevelFields = findPreference(PSK_LIST_BOOK_LEVEL_FIELDS);
        pShowCovers = findPreference(PSK_LIST_BOOK_SHOW_COVER_0);
        pCoverScale = findPreference(StyleDataStore.PK_COVER_SCALE);
        pTextScale = findPreference(StyleDataStore.PK_TEXT_SCALE);


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
        findPreference(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE)
                .setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());

        // First call sets the current situation before the screen is visible.
        updateLayoutPrefs(vm.getStyle().getLayout());
    }


    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        final Style style = vm.getStyle();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }

        // Style name as the subtitle
        //noinspection DataFlowIssue
        toolbar.setSubtitle(style.getLabel(getContext()));

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onModified().observe(getViewLifecycleOwner(), aVoid -> {
            updateSummaries();
            updateLayoutPrefs(vm.getStyle().getLayout());
        });

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        final UserStyle style = vm.getStyle();

        final PreferenceScreen screen = getPreferenceScreen();
        for (final BooklistGroup group : BooklistGroup.getAllGroups(style)) {
            group.setPreferencesVisible(screen, style.hasGroup(group.getId()));
        }

        updateSummaries();
        updateLayoutPrefs(vm.getStyle().getLayout());

        // for new (i.e. cloned) styles, auto-popup the name field for the user to change it.
        if (style.getId() == 0) {
            pName.setViewId(R.id.STYLE_NAME_VIEW);
            // We need this convoluted approach as the view we want to click
            // will only exist after the RecyclerView has bound it.
            getListView().addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@NonNull final View view) {
                            if (view.getId() == R.id.STYLE_NAME_VIEW && !nameSet) {
                                // We only do this once. It IS legal to use the same name.
                                nameSet = true;
                                view.performClick();
                            }
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(@NonNull final View view) {
                        }
                    });
        }
    }

    /**
     * The summary for these Preference's reflect what is selected on ANOTHER screen
     * or changes to ANOTHER KEY.
     * (this also means we cannot use SummaryProvider's for these)
     */
    private void updateSummaries() {
        final UserStyle style = vm.getStyle();
        final Context context = getContext();

        // List

        //noinspection DataFlowIssue
        pGroups.setSummary(style.getGroupsSummaryText(context));
        // the 'level expansion' depends on the number of groups in use
        pExpansionLevel.setMax(style.getGroupCount());
        pExpansionLevel.setValue(style.getExpansionLevel());
        pListBookLevelSorting.setSummary(vm.getBookLevelSortingPreferenceSummary(context));


        // List layout
        pListBookLevelFields.setSummary(style.getFieldVisibility(Style.Screen.List)
                                             .getPreferencesSummaryText(context));
        pCoverScale.setSummary(context.getResources().getStringArray(
                R.array.pe_bob_thumbnail_scale)[style.getCoverScale()]);
        pTextScale.setSummary(context.getResources().getStringArray(
                R.array.pe_bob_text_scale)[style.getTextScale()]);
    }

    /**
     * Use the given {@link Style.Layout} to show/hide the applicable preferences.
     *
     * @param layout to match
     *
     * @throws IllegalArgumentException when there is a bug with the enums
     */
    private void updateLayoutPrefs(@NonNull final Style.Layout layout) {
        switch (layout) {
            case List: {
                pShowCovers.setVisible(true);
                break;
            }
            case Grid: {
                // The point of Grid is to show covers
                pShowCovers.setVisible(false);
                pShowCovers.setChecked(true);
                break;
            }
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_NAME_SET, nameSet);
    }
}
