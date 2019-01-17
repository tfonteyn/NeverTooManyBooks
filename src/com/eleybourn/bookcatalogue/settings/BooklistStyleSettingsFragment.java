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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;

import java.util.Objects;

/**
 * Settings editor for a Style.
 */
public class BooklistStyleSettingsFragment
        extends BaseSettingsFragment
        implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    /** Parameter used to pass data to this activity. */
    public static final String REQUEST_BKEY_STYLE = "Style";
    /** Request code for calling the Activity to edit the Groups of the style. */
    private static final int REQ_EDIT_GROUPS = 0;
    /** Style we are editing. */
    private BooklistStyle mStyle;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        mStyle = getArguments().getParcelable(REQUEST_BKEY_STYLE);
        Objects.requireNonNull(mStyle);
        if (BuildConfig.DEBUG) {
            Logger.info(this, "after de-parceling\n" + mStyle);
        }

        getPreferenceManager().setSharedPreferencesName(mStyle.getUuid());

        setPreferencesFromResource(R.xml.preferences_book_style, rootKey);
        PreferenceScreen screen = getPreferenceScreen();

        // doing this in our base class. TODO: use this for all prefs instead of our own code.
//        EditTextPreference np = screen.findPreference(BookCatalogueApp.getResString(R.string.name));
//        np.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());

        // add the preferences from all groups:
        for (BooklistGroup group : mStyle.getGroups()) {
            group.addPreferences(screen);
        }

        // Set the title (not the screen title)
        if (mStyle.getId() == 0) {
            requireActivity().setTitle(
                    getString(R.string.title_clone_style_colon_name, mStyle.getDisplayName()));
        } else {
            requireActivity().setTitle(
                    getString(R.string.title_edit_style_colon_name, mStyle.getDisplayName()));
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
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, mStyle.getId());
        data.putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
        requireActivity()
                .setResult(UniqueId.ACTIVITY_RESULT_OK_BooklistStylePropertiesActivity, data);
    }

    /**
     * Update the summary after a change.
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
        Preference preference =
                findPreference(BookCatalogueApp.getResString(R.string.pg_groupings));
        if (preference != null) {
            preference.setSummary(mStyle.getGroupListDisplayNames());

            preference.getIntent().putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull final Preference preference) {
                    startActivityForResult(preference.getIntent(), REQ_EDIT_GROUPS);
                    return true;
                }
            });
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        switch (requestCode) {
            case REQ_EDIT_GROUPS:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    // replace the current style with the edited copy
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
