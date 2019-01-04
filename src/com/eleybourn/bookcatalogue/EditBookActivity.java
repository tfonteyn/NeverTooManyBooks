/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

public class EditBookActivity
    extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        EditBookFragment frag = new EditBookFragment();
        frag.setArguments(extras);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.main_fragment, frag, EditBookFragment.TAG)
            .commit();
        Tracker.exitOnCreate(this);
    }

    @Override
    @CallSuper
    public void onBackPressed() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        super.onBackPressed();
    }
}
