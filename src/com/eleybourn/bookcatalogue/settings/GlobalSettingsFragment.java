package com.eleybourn.bookcatalogue.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * Global settings page.
 */
public class GlobalSettingsFragment
        extends BaseSettingsFragment {

    /** Fragment manager t. */
    public static final String TAG = GlobalSettingsFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        setSummary(screen);

        // ENHANCE: make the response conditional, not all changes warrant a recreate!
        requireActivity().setResult(UniqueId.ACTIVITY_RESULT_RECREATE_NEEDED);
    }
}
