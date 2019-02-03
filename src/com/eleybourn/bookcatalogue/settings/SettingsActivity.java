package com.eleybourn.bookcatalogue.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Hosting activity for Preference editing.
 */
public class SettingsActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            String tag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
            // Create the fragment only when the activity is created for the first time.
            // i.e. not after orientation changes
            Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
            if (frag == null) {
                frag = getFragment(tag);
            }
            // forward any/all arguments to the actual fragment.
            frag.setArguments(getIntent().getExtras());
            Logger.info(this, "onCreate");

            FragmentManager.enableDebugLogging(true);

            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(R.id.main_fragment, frag, tag)
                    .commit();
        }
    }

    /**
     * create a new fragment instance from the tag.
     *
     * @param tag name of fragment to instantiate
     *
     * @return new instance
     */
    private Fragment getFragment(@NonNull final String tag) {
        Fragment frag;
        switch (tag) {
            case GlobalSettingsFragment.TAG:
                frag = new GlobalSettingsFragment();
                break;

            case FieldVisibilitySettingsFragment.TAG:
                frag = new FieldVisibilitySettingsFragment();
                break;

            case BooklistStyleSettingsFragment.TAG:
                frag = new BooklistStyleSettingsFragment();
                break;

            default:
                Logger.error("tag=" + tag);
                frag = new GlobalSettingsFragment();
        }
        return frag;
    }

    /**
     * If any of the child preference fragments have an xml configuration with nested
     * PreferenceScreen elements, then a click on those will trigger this method.
     *
     * @param caller the fragment
     * @param pref   the desired screen
     *
     * @return <tt>true</tt> if handled.
     */
    @Override
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                           @NonNull final PreferenceScreen pref) {

        // start a NEW copy of the same fragment
        //noinspection ConstantConditions
        Fragment frag = getFragment(caller.getTag());
        // and set it to start with the new root key (screen)
        Bundle args = new Bundle();
        args.putAll(caller.getArguments());
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        frag.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(pref.getKey())
                .replace(R.id.main_fragment, frag, pref.getKey())
                .commit();

        return true;
    }
}
