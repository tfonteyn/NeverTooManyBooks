package com.hardbacknutter.nevertomanybooks;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertomanybooks.viewmodels.AuthorWorksModel;

/**
 * Hosting activity for showing an author.
 * <p>
 * <b>Note:</b> eventually these 'hosting' activities are meant to go. The idea is to have ONE
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

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(AuthorWorksFragment.TAG) == null) {
            Fragment frag = new AuthorWorksFragment();
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .replace(R.id.main_fragment, frag, AuthorWorksFragment.TAG)
              .commit();
        }
    }

    @Override
    public void onBackPressed() {
        AuthorWorksModel model = ViewModelProviders.of(this).get(AuthorWorksModel.class);

        if (model.isAtLeastOneBookDeleted()) {
            setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
        }
        super.onBackPressed();
    }
}
