package com.eleybourn.bookcatalogue.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.Csv;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleSettingsFragment
        extends BaseSettingsFragment {

    /** Fragment manager tag. */
    public static final String TAG = StyleSettingsFragment.class.getSimpleName();

    /** Request code for calling the Activity to edit the Groups of the style. */
    private static final int REQ_EDIT_GROUPS = 0;
    /** Style we are editing. */
    private BooklistStyle mStyle;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        Bundle args = requireArguments();

        mStyle = args.getParcelable(UniqueId.BKEY_STYLE);
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            //noinspection ConstantConditions
            Logger.debugEnter(this, "onCreatePreferences", mStyle.toString());
        }

        // We use the style UUID as the filename for the prefs.
        //noinspection ConstantConditions
        String uuid = mStyle.getUuid();
        if (!uuid.isEmpty()) {
            getPreferenceManager().setSharedPreferencesName(uuid);
        }
        // else if uuid.isEmpty(), use global SharedPreferences for editing global defaults

        setPreferencesFromResource(R.xml.preferences_book_style, rootKey);

        PreferenceScreen screen = getPreferenceScreen();

        // doing this in our base class. TODO: use this for all prefs instead of our own code
//        EditTextPreference np =
//            screen.findPreference(BookCatalogueApp.getResString(R.string.name));
//        np.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());

        // add the preferences from all groups:
        for (BooklistGroup group : mStyle.getGroups()) {
            group.addPreferencesTo(screen);
        }

        //noinspection ConstantConditions
        @NonNull
        Activity activity = getActivity();
        ActionBar bar = ((AppCompatActivity)activity).getSupportActionBar();
        //noinspection ConstantConditions
        bar.setSubtitle(mStyle.getLabel(activity));
        if (mStyle.getId() == 0) {
            bar.setTitle(R.string.title_clone_style);
        } else {
            bar.setTitle(R.string.title_edit_style);
        }

        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(),
                                    R.string.hint_booklist_style_properties, null);
        }

        // Set the summaries reflecting the current values for all basic Preferences.
        setSummary(screen);
        updateLocalSummaries();
    }

    /**
     * Update the local summaries after a change.
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        updateLocalSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();

        updateLocalSummaries();
    }

    @Override
    public void onPause() {

        // set the default response
        Intent data = new Intent()
                .putExtra(DBDefinitions.KEY_ID, mStyle.getId())
                .putExtra(UniqueId.BKEY_STYLE, (Parcelable) mStyle);
        //noinspection ConstantConditions
        getActivity().setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE, data);

        super.onPause();
    }

    /**
     * Update non-standard summary texts.
     *
     * <ul>
     * <li>hide/show the "Series" category</li>
     * <li>filter labels</li>
     * <li>extras labels</li>
     * <li>group labels + Adds an onClick to edit the groups for this style.
     * The groups are a PreferenceScreen of their own, here 'faked' with a new activity.</li>
     * </ul>
     *
     * Reminder: prefs lookups can return {@code null} as the screen swaps in and out sub screens.
     */
    private void updateLocalSummaries() {
        Preference preference;
        List<String> labels;

        // the 'extra' fields in use.
        preference = findPreference(getString(R.string.pg_bob_extra_book_details));
        if (preference != null) {
            //noinspection ConstantConditions
            labels = mStyle.getExtraFieldsLabels(getContext(), false);
            if (labels.isEmpty()) {
                preference.setSummary(getString(R.string.none));
            } else {
                preference.setSummary(Csv.join(", ", labels));
            }
        }

        // the 'filters' in use
        preference = findPreference(getString(R.string.pg_filters));
        if (preference != null) {
            //noinspection ConstantConditions
            labels = mStyle.getFilterLabels(getContext(), false);
            if (labels.isEmpty()) {
                preference.setSummary(getString(R.string.none));
            } else {
                preference.setSummary(Csv.join(", ", labels));
            }
        }

        // the 'groups' in use.
        preference = findPreference(getString(R.string.pg_groupings));
        if (preference != null) {
            //noinspection ConstantConditions
            preference.setSummary(mStyle.getGroupLabels(getContext()));
            preference.getIntent().putExtra(UniqueId.BKEY_STYLE, (Parcelable) mStyle);
            preference.setOnPreferenceClickListener(p -> {
                startActivityForResult(p.getIntent(), REQ_EDIT_GROUPS);
                return true;
            });
        }

        // The "Series" category has no settings of its own (in contrast to "Authors").
        // So unless the group is included, we hide the "Series" category.
        preference = findPreference(getString(R.string.lbl_series));
        if (preference != null) {
            preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.SERIES));
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case REQ_EDIT_GROUPS:
                if (resultCode == Activity.RESULT_OK) {
                    // replace the current style with the edited copy
                    //noinspection ConstantConditions
                    mStyle = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                    // sanity check
                    Objects.requireNonNull(mStyle);
                    // refresh summaries on screen
                    updateLocalSummaries();
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
