package com.eleybourn.bookcatalogue.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

/**
 * Hosting activity for Preference editing.
 */
public class SettingsActivity
        extends BaseActivity {

    /** {@link GlobalSettingsFragment}. */
    public static final int FRAGMENT_GLOBAL_SETTINGS = 0;

    /** {@link FieldVisibilitySettingsFragment}. */
    public static final int FRAGMENT_FIELD_VISIBILITY = 1;

    /** {@link GlobalSettingsFragment}. */
    public static final int FRAGMENT_BOOKLIST_SETTINGS = 2;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            int fragmentId = getIntent()
                    .getIntExtra(UniqueId.FRAGMENT_ID, FRAGMENT_GLOBAL_SETTINGS);
            Fragment frag;
            switch (fragmentId) {
                case FRAGMENT_GLOBAL_SETTINGS:
                    frag = new GlobalSettingsFragment();
                    break;

                case FRAGMENT_FIELD_VISIBILITY:
                    frag = new FieldVisibilitySettingsFragment();
                    break;

                case FRAGMENT_BOOKLIST_SETTINGS:
                    frag = new BooklistStyleSettingsFragment();
                    break;

                default:
                    throw new IllegalArgumentException("fragmentId=" + fragmentId);
            }

            // forward any/all arguments to the actual fragment.
            frag.setArguments(getIntent().getExtras());

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_fragment, frag)
                    .addToBackStack(null)
                    .commit();
        }
    }
}
