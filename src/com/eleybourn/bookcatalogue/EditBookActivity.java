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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

public class EditBookActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK;

    /**
     * Load with the provided book id. Also open to the provided tab.
     *
     * @param activity the caller
     * @param id       The id of the book to edit
     * @param tab      Which tab to open first
     */
    public static void startActivityForResult(final @NonNull Activity activity,
                                              final long id,
                                              final int tab) {
        Intent intent = new Intent(activity, EditBookActivity.class);
        intent.putExtra(UniqueId.KEY_ID, id);
        intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, tab);
        activity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();

        EditBookFragment frag = new EditBookFragment();
        frag.setArguments(extras);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment, frag, EditBookFragment.TAG)
                .commit();
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: forwarding to fragment - requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        Fragment frag = getSupportFragmentManager().findFragmentByTag(EditBookFragment.TAG);
        frag.onActivityResult(requestCode, resultCode, data);
    }
}
