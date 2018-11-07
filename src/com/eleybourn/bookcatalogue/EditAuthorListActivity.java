/*
 * @copyright 2011 Philip Warner
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditAuthorDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and return an updated list.
 *
 * Calling point is a Book; see {@link EditAuthorDialog} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity extends EditObjectListActivity<Author> {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_AUTHORS;

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditAuthorListActivity() {
        super(R.layout.activity_edit_list_author, R.layout.row_edit_author_list, UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        // Setup AutoCompleteTextView for author name
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mDb.getAuthorsFormattedName());
        ((AutoCompleteTextView) this.findViewById(R.id.author)).setAdapter(adapter);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    /**
     * Do the work of the onClickListener for the 'Add' button.
     */
    protected void onAdd(final @NonNull View target) {
        AutoCompleteTextView authorField = findViewById(R.id.author);
        String authorName = authorField.getText().toString().trim();
        if (!authorName.isEmpty()) {
            // Get an author and try to find in DB.
            Author author = new Author(authorField.getText().toString().trim());
            author.id = mDb.getAuthorIdByName(author.familyName, author.givenNames);
            for (Author s : mList) {
                if (s.equals(author)) {
                    // Snackbar.make(target, R.string.author_already_in_list, Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showUserMessage(this, R.string.warning_author_already_in_list);
                    return;
                }
            }
            mList.add(author);
            mListAdapter.notifyDataSetChanged();
            authorField.setText("");
        } else {
            //Snackbar.make(target, R.string.author_is_blank, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(this, R.string.warning_blank_author);
        }
    }


    /** TOMF: TODO: almost duplicate code in {@link EditAuthorDialog} */
    private void edit(final @NonNull Author author) {
        // Build the base dialog
        final View root = EditAuthorListActivity.this.getLayoutInflater().inflate(R.layout.dialog_edit_author, null);

        final EditText familyView = root.findViewById(R.id.family_name);
        //noinspection ConstantConditions
        familyView.setText(author.familyName);
        final EditText givenView = root.findViewById(R.id.given_names);
        //noinspection ConstantConditions
        givenView.setText(author.givenNames);

        final AlertDialog dialog = new AlertDialog.Builder(EditAuthorListActivity.this)
                .setView(root)
                .setTitle(R.string.dialog_title_edit_author)
                .create();

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(EditAuthorListActivity.this, R.string.warning_blank_author);
                    return;
                }

                String newGiven = givenView.getText().toString().trim();
                Author newAuthor = new Author(newFamily, newGiven);
                dialog.dismiss();
                confirmEdit(author, newAuthor);
            }
        });
        //noinspection ConstantConditions
        dialog.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(final @NonNull Author from, final @NonNull Author to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's
        from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames); //TODO: this call is not needed I think
        to.id = mDb.getAuthorIdByName(to.familyName, to.givenNames);

        // See if the old author is used in any other books; allows us to skip a global replace
        long nRefs = mDb.countAuthorBooks(from) + mDb.countAuthorAnthologies(from);
        boolean fromHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // author is the same (but maybe different case), or is only used in this book
        if (to.id == from.id || !fromHasOthers) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            Utils.pruneList(mDb, mList);
            if (from.id == 0) {
                from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames);
            }
            mDb.insertOrUpdateAuthor(from);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author is used in more than one place.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.changed_author_how_apply, from.getSortName(), to.getSortName(), allBooks))
                .setTitle(R.string.scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.this_book), new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                mListAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                mDb.globalReplaceAuthor(from, to);
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                mListAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    @Override
    protected boolean onSave(final @NonNull Intent intent) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            return true;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.unsaved_edits))
                .setTitle(R.string.unsaved_edits_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.no),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                    }
                });

        dialog.show();
        return false;
    }

    protected SimpleListAdapter<Author> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<Author> list) {
        return new AuthorListAdapter(this,rowViewId,list);
    }

    protected class AuthorListAdapter extends SimpleListAdapter<Author> implements SimpleListAdapterRowActionListener<Author> {
        AuthorListAdapter(final @NonNull Context context, final @LayoutRes int rowViewId, final @NonNull ArrayList<Author> items) {
            super(context, rowViewId, items);
        }

        @Override
        public void onGetView(final @NonNull View target, final @NonNull Author object) {
            TextView at = target.findViewById(R.id.row_author);
            if (at != null) {
                at.setText(object.getDisplayName());
            }
            at = target.findViewById(R.id.row_author_sort);
            if (at != null) {
                at.setText(object.getSortName());
            }
        }

        @Override
        public void onRowClick(final @NonNull View target, final @NonNull Author author, final int position) {
            edit(author);
        }
    }

}
