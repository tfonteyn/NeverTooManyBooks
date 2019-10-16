/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Hosting activity for admin functions.
 */
public class AdminActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_administration);

        FragmentManager fm = getSupportFragmentManager();
        if (null == fm.findFragmentByTag(AdminFragment.TAG)) {
            Fragment frag = new AdminFragment();
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .replace(R.id.main_fragment, frag, AdminFragment.TAG)
              .commit();
        }
    }

    @Override
    public void onBackPressed() {
        ResultDataModel resultDataModel = new ViewModelProvider(this).get(ResultDataModel.class);
        Intent resultData = resultDataModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            setResult(Activity.RESULT_OK, resultData);
        }

        super.onBackPressed();
    }
}
