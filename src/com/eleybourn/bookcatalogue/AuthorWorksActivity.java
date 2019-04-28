package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

/**
 * Hosting activity for showing an author.
 *
 * Note: eventually these 'hosting' activities are meant to go. The idea is to have ONE
 * hosting/main activity, which swaps in fragments as needed.
 */
public class AuthorWorksActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getSupportFragmentManager().findFragmentByTag(AuthorWorksFragment.TAG)) {
            Fragment frag = new AuthorWorksFragment();
            frag.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.main_fragment, frag, AuthorWorksFragment.TAG)
                    .commit();
        }
    }
}
