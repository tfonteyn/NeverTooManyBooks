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
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Activity where we can edit a Bookshelf (its name)
 *
 * TODO: overkill... make this a dialog
 */
public class EditBookshelfActivity extends BaseActivity {

    @Nullable
    private CatalogueDBAdapter mDb;

    private EditText mBookshelfText;
    private Button mConfirmButton;
    private long mRowId;

    @Override
    protected int getLayoutId() {
        return R.layout.bookshelf_edit;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.title_edit_bs);

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            mRowId = 0;
            if (savedInstanceState != null) {
                mRowId = savedInstanceState.getLong(UniqueId.KEY_ID);
            }

            Bundle extras = getIntent().getExtras();
            if ((mRowId == 0) && (extras != null)) {
                    mRowId = extras.getLong(UniqueId.KEY_ID);
            }

            mConfirmButton = findViewById(R.id.confirm);
            mConfirmButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    saveState();
                    setResult(RESULT_OK);
                    finish();
                }
            });

            findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    setResult(RESULT_OK);
                    finish();
                }
            });

            mBookshelfText = findViewById(R.id.bookshelf);
            populateFields();

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    private void populateFields() {
        if (mRowId > 0) {
            mBookshelfText.setText(mDb.getBookshelfName(mRowId));
            mConfirmButton.setText(R.string.save);
        } else {
            mConfirmButton.setText(R.string.add);
        }
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            outState.putLong(UniqueId.KEY_ID, mRowId);
        } catch (Exception ignore) {
        }
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        populateFields();
    }

    private void saveState() {
        String bookshelf = mBookshelfText.getText().toString().trim();
        if (mRowId == 0) {
            long id = mDb.insertBookshelf(bookshelf);
            if (id > 0) {
                mRowId = id;
            }
        } else {
            mDb.updateBookshelf(mRowId, bookshelf);
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
        super.onDestroy();
    }
}
