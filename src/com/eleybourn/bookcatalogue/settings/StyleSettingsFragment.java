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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.TipManager;
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
    public static final String TAG = "StyleSettingsFragment";

    /** Style we are editing. */
    private BooklistStyle mStyle;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        mStyle = Objects.requireNonNull(requireArguments().getParcelable(UniqueId.BKEY_STYLE));
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

        // doing this in our base class. TODO: use this for all prefs instead of our own code
//        EditTextPreference np = screen.findPreference(getString(R.string.name));
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

        // Display hint if required
        if (savedInstanceState == null) {
            TipManager.display(getLayoutInflater(),
                               R.string.tip_booklist_style_properties, null);
        }

        // Set the summaries reflecting the current values for all basic Preferences.
        setSummary(screen);
        updateLocalSummaries();
        // set the default response
        prepareResult();
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

    /**
     * Put the style into the activity result.
     * <p>
     * Reminder: do NOT call this in onPause... as onBackPressed is called before (and does finish).
     */
    @Override
    void prepareResult() {
        Intent data = new Intent().putExtra(UniqueId.BKEY_STYLE, (Parcelable) mStyle);
        //noinspection ConstantConditions
        getActivity().setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE, data);

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
     * <p>
     * Reminder: prefs lookups can return {@code null} as the screen swaps in and out sub screens.
     */
    private void updateLocalSummaries() {
        Preference preference;
        List<String> labels;

        // the 'extra' fields in use.
        preference = findPreference(getString(R.string.pg_bob_extra_book_details));
        if (preference != null) {
            labels = getExtraFieldsLabels();
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
                startActivityForResult(p.getIntent(), UniqueId.REQ_EDIT_STYLE_GROUPS);
                return true;
            });
        }

        // The "Series" category has no settings of its own (in contrast to "Authors").
        // So unless the group is included, we hide the "Series" category.
        preference = findPreference(getString(R.string.lbl_series));
        if (preference != null) {
            preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.SERIES));
        }
        // always visible
//        preference = findPreference(getString(R.string.lbl_author));
//        if (preference != null) {
//            preference.setVisible(mStyle.hasGroupKind(BooklistGroup.RowKind.AUTHOR));
//        }
    }

    /**
     * @return the list of in-use extra-field names in a human readable format.
     */
    private List<String> getExtraFieldsLabels() {

        int extraFields = mStyle.getExtraFieldsStatus();

        List<String> labels = new ArrayList<>();
        if ((extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
            labels.add(getString(R.string.pt_bob_thumbnails_show));
        }
        if ((extraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
            labels.add(getString(R.string.lbl_bookshelves));
        }
        if ((extraFields & BooklistStyle.EXTRAS_LOCATION) != 0) {
            labels.add(getString(R.string.lbl_location));
        }
        if ((extraFields & BooklistStyle.EXTRAS_AUTHOR) != 0) {
            labels.add(getString(R.string.lbl_author));
        }
        if ((extraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
            labels.add(getString(R.string.lbl_publisher));
        }
        if ((extraFields & BooklistStyle.EXTRAS_ISBN) != 0) {
            labels.add(getString(R.string.lbl_isbn));
        }
        if ((extraFields & BooklistStyle.EXTRAS_FORMAT) != 0) {
            labels.add(getString(R.string.lbl_format));
        }
        Collections.sort(labels);
        return labels;
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
                    // refresh summaries on screen
                    updateLocalSummaries();
                    // and set the activity result with the new style object
                    prepareResult();
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
