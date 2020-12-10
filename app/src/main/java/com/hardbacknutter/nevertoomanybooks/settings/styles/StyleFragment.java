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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.HostingActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.DetailScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostingActivity;

/**
 * Main fragment to edit a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleFragment
        extends StyleBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "StylePreferenceFragment";
    private static final String SIS_NAME_SET = TAG + ":nameSet";

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_SHOW_DETAILS = "psk_style_show_details";
    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_FILTERS = "psk_style_filters";
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    mStyleViewModel.updateOrInsertStyle();

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mStyleViewModel.getResultIntent());
                    getActivity().finish();
                }
            };

    /** Flag: prompt for the name of cloned styles. */
    private boolean mNameSet;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_style, rootKey);

        if (savedInstanceState != null) {
            mNameSet = savedInstanceState.getBoolean(SIS_NAME_SET);
        }

        // Cover on LIST screen
        final Preference thumbScale = findPreference(ListScreenBookFields.PK_COVER_SCALE);
        if (thumbScale != null) {
            thumbScale.setDependency(ListScreenBookFields.PK_COVERS);
        }

        // Covers on DETAIL screen
        // Setting cover 0 to false -> disable cover 1; also see onSharedPreferenceChanged
        final Preference cover = findPreference(DetailScreenBookFields.PK_COVER[1]);
        if (cover != null) {
            cover.setDependency(DetailScreenBookFields.PK_COVER[0]);
        }

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);
    }

    @Override
    public void onResume() {
        final PreferenceScreen screen = getPreferenceScreen();
        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        // Use the global style to get the groups.
        //noinspection ConstantConditions
        final UserStyle globalStyle = UserStyle.createGlobal(getContext());
        final Groups styleGroups = mStyleViewModel.getStyle().getGroups();

        for (final BooklistGroup group : BooklistGroup.getAllGroups(globalStyle)) {
            group.setPreferencesVisible(screen, styleGroups.contains(group.getId()));
        }

        super.onResume();

        // These keys are never physically present in the SharedPreferences; so handle explicitly.
        updateSummary(PSK_STYLE_SHOW_DETAILS);
        updateSummary(PSK_STYLE_FILTERS);

        // for new (i.e. cloned) styles, auto-popup the name field for the user to change it.
        if (mStyleViewModel.getStyle().getId() == 0) {
            //noinspection ConstantConditions
            findPreference(UserStyle.PK_STYLE_NAME).setViewId(R.id.STYLE_NAME_VIEW);
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


    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_NAME_SET, mNameSet);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences stylePrefs,
                                          @NonNull final String key) {

        // Covers on DETAIL screen
        // Setting cover 0 to false -> set cover 1 to false as well
        if (DetailScreenBookFields.PK_COVER[0].equals(key)
            && !stylePrefs.getBoolean(key, false)) {
            final SwitchPreference cover = findPreference(DetailScreenBookFields.PK_COVER[1]);
            // Sanity check
            if (cover != null) {
                cover.setChecked(false);
            }
        }

        super.onSharedPreferenceChanged(stylePrefs, key);
    }

    @Override
    protected void updateSummary(@NonNull final String key) {

        final UserStyle style = mStyleViewModel.getStyle();

        switch (key) {
            case TextScale.PK_TEXT_SCALE: {
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(style.getTextScale()
                                               .getFontScaleSummaryText(getContext()));
                }
                break;
            }

            case ListScreenBookFields.PK_COVER_SCALE: {
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(style.getListScreenBookFields()
                                               .getCoverScaleSummaryText(getContext()));
                }
                break;
            }

            case UserStyle.PK_LEVELS_EXPANSION: {
                final SeekBarPreference preference = findPreference(key);
                if (preference != null) {
                    preference.setMax(style.getGroups().size());
                    preference.setSummary(String.valueOf(style.getTopLevel()));
                }
                break;
            }

            case Groups.PK_STYLE_GROUPS: {
                // the 'groups' in use.
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(style.getGroups().getSummaryText(getContext()));
                }
                break;
            }

            case ListScreenBookFields.PK_COVERS:
            case PSK_STYLE_SHOW_DETAILS: {
                // the 'extra' fields in use.
                final Preference preference = findPreference(PSK_STYLE_SHOW_DETAILS);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(style.getListScreenBookFields()
                                               .getSummaryText(getContext()));
                }
                break;
            }
            case PSK_STYLE_FILTERS: {
                // the 'filters' in use (i.e. the actives ones)
                final Preference preference = findPreference(key);
                if (preference != null) {
                    //noinspection ConstantConditions
                    preference.setSummary(style.getFilters().getSummaryText(getContext(), false));
                }
                break;
            }

            default:
                super.updateSummary(key);
                break;
        }
    }

    public static class ResultContract
            extends ActivityResultContract<ResultContract.Input, ResultContract.Output> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final Input input) {
            return new Intent(context, SettingsHostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, StyleFragment.TAG)
                    .putExtra(StyleViewModel.BKEY_ACTION, input.action)
                    .putExtra(ListStyle.BKEY_STYLE_UUID, input.uuid)
                    .putExtra(StyleViewModel.BKEY_SET_AS_PREFERRED, input.setAsPreferred);
        }

        @Override
        @Nullable
        public Output parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return null;
            }

            final Bundle data = intent.getExtras();
            if (data == null) {
                // should not actually ever be the case...
                return null;
            }

            return new Output(
                    Objects.requireNonNull(data.getString(StyleViewModel.BKEY_TEMPLATE_UUID),
                                           "BKEY_TEMPLATE_UUID"),
                    data.getBoolean(StyleViewModel.BKEY_STYLE_MODIFIED, false),
                    data.getString(ListStyle.BKEY_STYLE_UUID));
        }

        public static class Input {

            @StyleViewModel.EditAction
            final int action;

            @NonNull
            final String uuid;

            /**
             * If set to {@code true} the edited/cloned style will be set to preferred.
             * If set to {@code false} the preferred state will not be touched.
             */
            final boolean setAsPreferred;

            public Input(@StyleViewModel.EditAction final int action,
                         @NonNull final String uuid,
                         final boolean setAsPreferred) {
                this.action = action;
                this.uuid = uuid;
                this.setAsPreferred = setAsPreferred;
            }
        }

        public static class Output {

            /**
             * Either a new UUID if we cloned a style, or the UUID of the style we edited.
             * Will be {@code null} if we edited the global style
             */
            @Nullable
            public final String uuid;

            /** The uuid which was passed into the {@link Input#uuid} for editing. */
            @NonNull
            final String templateUuid;
            final boolean modified;

            Output(@NonNull final String templateUuid,
                   final boolean modified,
                   @Nullable final String uuid) {
                this.templateUuid = templateUuid;
                this.modified = modified;
                this.uuid = uuid;
            }
        }
    }
}
