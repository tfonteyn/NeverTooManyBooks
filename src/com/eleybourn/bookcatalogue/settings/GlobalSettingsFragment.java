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

    /** Fragment manager tag. */
    public static final String TAG = "GlobalSettingsFragment";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        setSummary(screen);

        prepareResult();
    }

    @Override
    void prepareResult() {
        // ENHANCE: make the response conditional, not all changes warrant a recreate!
        //noinspection ConstantConditions
        getActivity().setResult(UniqueId.ACTIVITY_RESULT_RECREATE_NEEDED);
    }
}
