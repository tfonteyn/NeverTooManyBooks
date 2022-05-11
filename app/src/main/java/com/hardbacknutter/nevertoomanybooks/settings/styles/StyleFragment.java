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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.DetailScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.MultiSelectListPreferenceSummaryProvider;

/**
 * Main fragment to edit a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleFragment
        extends StyleBaseFragment {

    /** Fragment manager tag. */
    private static final String TAG = "StylePreferenceFragment";
    private static final String SIS_NAME_SET = TAG + ":nameSet";

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_SHOW_DETAILS = "psk_style_show_details";
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    mVm.updateOrInsertStyle();

                    final Intent resultIntent = EditStyleContract
                            .createResultIntent(mVm.getTemplateUuid(),
                                                mVm.isModified(),
                                                mVm.getStyle().getUuid());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    /** Flag: prompt for the name of cloned styles. */
    private boolean mNameSet;

    private EditTextPreference pName;
    private Preference pCoverScale;
    private Preference pTextScale;
    private SeekBarPreference pExpansionLevel;
    private Preference pListHeader;
    private Preference pPrimaryAuthorType;
    private Preference pShowDetails;
    private Preference pGroups;

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_style, rootKey);

        if (savedInstanceState != null) {
            mNameSet = savedInstanceState.getBoolean(SIS_NAME_SET);
        }

        pName = findPreference(UserStyle.PK_STYLE_NAME);
        pExpansionLevel = findPreference(UserStyle.PK_LEVELS_EXPANSION);
        pCoverScale = findPreference(ListScreenBookFields.PK_COVER_SCALE);
        pTextScale = findPreference(TextScale.PK_TEXT_SCALE);
        pListHeader = findPreference(BooklistStyle.PK_LIST_HEADER);
        pPrimaryAuthorType = findPreference(AuthorBooklistGroup.PK_PRIMARY_TYPE);
        pShowDetails = findPreference(PSK_STYLE_SHOW_DETAILS);
        pGroups = findPreference(Groups.PK_STYLE_GROUPS);

        pName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        pListHeader.setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());
        pPrimaryAuthorType.setSummaryProvider(
                MultiSelectListPreferenceSummaryProvider.getInstance());

        pName.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        // Use the global style to get the groups.

        final UserStyle style = mVm.getStyle();
        final Groups styleGroups = style.getGroups();

        final PreferenceScreen screen = getPreferenceScreen();
        //noinspection ConstantConditions
        for (final BooklistGroup group
                : BooklistGroup.getAllGroups(UserStyle.createGlobal(getContext()))) {
            group.setPreferencesVisible(screen, styleGroups.contains(group.getId()));
        }

        updateSummaries();

        // for new (i.e. cloned) styles, auto-popup the name field for the user to change it.
        if (style.getId() == 0) {
            pName.setViewId(R.id.STYLE_NAME_VIEW);
            // We need this convoluted approach as the view we want to click
            // will only exist after the RecyclerView has bound it.
            getListView().addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@NonNull final View view) {
                            if (view.getId() == R.id.STYLE_NAME_VIEW && !mNameSet) {
                                // We only do this once. It IS legal to use the same name.
                                mNameSet = true;
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
     */
    private void updateSummaries() {
        final UserStyle style = mVm.getStyle();

        // the 'book details' fields in use.
        //noinspection ConstantConditions
        findPreference(PSK_STYLE_SHOW_DETAILS)
                .setSummary(style.getListScreenBookFields().getSummaryText(getContext()));

        // the 'groups' in use.
        //noinspection ConstantConditions
        findPreference(Groups.PK_STYLE_GROUPS)
                .setSummary(style.getGroups().getSummaryText(getContext()));

        //noinspection ConstantConditions
        findPreference(ListScreenBookFields.PK_COVER_SCALE)
                .setSummary(mVm.getStyle().getListScreenBookFields()
                               .getCoverScaleSummaryText(getContext()));

        pTextScale.setSummary(mVm.getStyle().getTextScale().getSummaryText(getContext()));

        // the 'level expansion' depends on the number of groups in use
        pExpansionLevel.setMax(style.getGroups().size());
        pExpansionLevel.setValue(style.getExpansionLevel());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_NAME_SET, mNameSet);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences stylePrefs,
                                          @NonNull final String key) {
        updateSummaries();

        if (DetailScreenBookFields.PK_COVER[0].equals(key)
            && !stylePrefs.getBoolean(key, false)) {
            // Covers on DETAIL screen:
            // Setting cover 0 to false -> set cover 1 to false as well
            final SwitchPreference cover = findPreference(DetailScreenBookFields.PK_COVER[1]);
            //noinspection ConstantConditions
            cover.setChecked(false);
        }

        super.onSharedPreferenceChanged(stylePrefs, key);
    }

}
