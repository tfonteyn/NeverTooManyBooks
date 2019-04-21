package com.eleybourn.bookcatalogue.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

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
        setTitle(R.string.lbl_settings);

        Bundle extras = getIntent().getExtras();

        String tag = extras != null ? extras.getString(UniqueId.BKEY_FRAGMENT_TAG,
                                                       GlobalSettingsFragment.TAG)
                                    : GlobalSettingsFragment.TAG;

        if (null == getSupportFragmentManager().findFragmentByTag(tag)) {
            Fragment frag = createFragment(tag);
            frag.setArguments(getIntent().getExtras());
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
    private Fragment createFragment(@NonNull final String tag) {
        if (GlobalSettingsFragment.TAG.equals(tag)) {
            return new GlobalSettingsFragment();
        } else if (BooklistStyleSettingsFragment.TAG.equals(tag)) {
            return new BooklistStyleSettingsFragment();
        } else {
            throw new IllegalArgumentException("t=" + tag);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        App.getPrefs().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        App.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
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
        Fragment frag = createFragment(caller.getTag());

        // and set it to start with the new root key (screen)
        Bundle callerArgs = caller.getArguments();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        if (callerArgs != null) {
            args.putAll(callerArgs);
        }
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
