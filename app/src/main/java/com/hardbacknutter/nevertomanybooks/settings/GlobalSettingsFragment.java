package com.hardbacknutter.nevertomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;

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

        // caching of cover images is only used in combination with a background task to fetch them.
        Preference p = findPreference(Prefs.pk_bob_thumbnails_cache_resized);
        if (p != null) {
            p.setDependency(Prefs.pk_bob_thumbnails_generating_mode);
        }

        prepareResult();
    }

    @Override
    void prepareResult() {
        // ENHANCE: make the response conditional, not all changes warrant a recreate!
        //noinspection ConstantConditions
        getActivity().setResult(UniqueId.ACTIVITY_RESULT_RECREATE_NEEDED);
    }
}
