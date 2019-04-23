package com.eleybourn.bookcatalogue.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

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

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is null, then we're editing the global defaults.
 */
public class BooklistStyleSettingsFragment
        extends BaseSettingsFragment {

    /** Fragment manager tag. */
    public static final String TAG = BooklistStyleSettingsFragment.class.getSimpleName();

    /** Parameter used to pass data to this activity. */
    public static final String REQUEST_BKEY_STYLE = "Style";

    /** Request code for calling the Activity to edit the Groups of the style. */
    private static final int REQ_EDIT_GROUPS = 0;
    /** Style we are editing. */
    private BooklistStyle mStyle;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        Bundle args = requireArguments();

        mStyle = args.getParcelable(REQUEST_BKEY_STYLE);
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            //noinspection ConstantConditions
            Logger.debugEnter(this, "onCreatePreferences", mStyle.toString());
        }

        // We use the style UUID as the filename for the prefs.
        //noinspection ConstantConditions
        String uuid = mStyle.getUuid();
        if (!uuid.isEmpty()) {
            getPreferenceManager().setSharedPreferencesName(uuid);
        } // else if uuid.isEmpty(), use global SharedPreferences for editing global defaults

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

        Activity activity = requireActivity();
        // Set the title (not the screen title)
        if (mStyle.getId() == 0) {
            activity.setTitle(
                    getString(R.string.title_clone_style_colon_name,
                              mStyle.getLabel(activity)));
        } else {
            activity.setTitle(
                    getString(R.string.title_edit_style_colon_name,
                              mStyle.getLabel(activity)));
        }
        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(),
                                    R.string.hint_booklist_style_properties, null);
        }

        // Set the summaries reflecting the current values for all basic Preferences.
        setSummary(screen);
        // specific groups treatment
        setupBooklistGroups();

        // set the default response
        Intent data = new Intent()
                .putExtra(DBDefinitions.KEY_ID, mStyle.getId())
                .putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
        activity.setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE, data);
    }

    /**
     * Update the summaries after a change.
     *
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        setupBooklistGroups();
    }

    /**
     * Update the summary with the group name(s).
     * <p>
     * Adds an onClick to edit the groups for this style.
     * The groups are a PreferenceScreen of their own, here 'faked' with a new activity.
     */
    private void setupBooklistGroups() {
        //noinspection ConstantConditions
        Preference preference = findPreference(getContext().getString(R.string.pg_groupings));
        if (preference != null) {
            preference.setSummary(mStyle.getGroupListDisplayNames(getContext()));

            preference.getIntent().putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
            preference.setOnPreferenceClickListener(preference1 -> {
                startActivityForResult(preference1.getIntent(), REQ_EDIT_GROUPS);
                return true;
            });
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
                    mStyle = data.getParcelableExtra(REQUEST_BKEY_STYLE);
                    // sanity check
                    Objects.requireNonNull(mStyle);
                    // refresh groupings on screen
                    setupBooklistGroups();
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
