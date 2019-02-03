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
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditAuthorDialog} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity
        extends EditObjectListActivity<Author> {

    /** Main screen Author name field. */
    private AutoCompleteTextView mAuthorNameView;

    /** AutoCompleteTextView for mAuthorNameView */
    @SuppressWarnings("FieldCanBeLocal")
    private ArrayAdapter<String> mAuthorAdapter;

    /** flag indicating global changes were made. Used in setResult. */
    private boolean mGlobalChangeMade;

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditAuthorListActivity() {
        super(R.layout.activity_edit_list_author, R.layout.row_edit_author_list,
              UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        mAuthorAdapter = new ArrayAdapter<>(this,
                                            android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAuthorsFormattedName());

        mAuthorNameView = this.findViewById(R.id.author);
        mAuthorNameView.setAdapter(mAuthorAdapter);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    /**
     * The user entered new data in the edit field and clicked 'save'.
     *
     * @param target The view that was clicked ('add' button).
     */
    protected void onAdd(@NonNull final View target) {
        String authorName = mAuthorNameView.getText().toString().trim();
        if (authorName.isEmpty()) {
            StandardDialogs.showUserMessage(this, R.string.warning_required_author);
            return;
        }

        Author newAuthor = Author.fromString(authorName);
        // see if it already exists
        newAuthor.fixupId(mDb);
        // and check it's not already in the list.
        for (Author author : mList) {
            if (author.equals(newAuthor)) {
                StandardDialogs.showUserMessage(this, R.string.warning_author_already_in_list);
                return;
            }
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        onListChanged();

        // and clear for next entry.
        mAuthorNameView.setText("");

    }

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     *
     * @param author to edit
     */
    private void edit(@NonNull final Author author) {

        // Build the base dialog
        final View root = getLayoutInflater().inflate(R.layout.dialog_edit_author, null);

        // the dialog fields != screen fields.
        final EditText familyView = root.findViewById(R.id.family_name);
        final EditText givenView = root.findViewById(R.id.given_names);

        //noinspection ConstantConditions
        familyView.setText(author.getFamilyName());
        //noinspection ConstantConditions
        givenView.setText(author.getGivenNames());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setTitle(R.string.title_edit_author)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(EditAuthorListActivity.this,
                                                    R.string.warning_required_author);
                    return;
                }
                String newGiven = givenView.getText().toString().trim();
                dialog.dismiss();

                // anything actually changed ?
                if (author.getFamilyName().equals(newFamily)
                        && author.getGivenNames().equals(newGiven)) {
                    return;
                }

                // At this point, we know changes were made.
                // Create a new Author as a holder for the changes.
                Author newAuthor = new Author(newFamily, newGiven, author.isComplete());

                processChanges(author, newAuthor);
            }
        });
        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void processChanges(@NonNull final Author author,
                                @NonNull final Author newAuthor) {

        // See if the old one is used by any other books.
        long nrOfReferences = mDb.countBooksByAuthor(author)
                + mDb.countTocEntryByAuthor(author);
        boolean usedByOthers = nrOfReferences > (mRowId == 0 ? 0 : 1);

        // if it's not, then we can simply re-use the old object.
        if (!usedByOthers) {
            // Use the original author, but update its fields
            author.copyFrom(newAuthor);
            Utils.pruneList(mDb, mList);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(
                        getString(R.string.confirm_apply_author_changed,
                                  author.getSortName(), newAuthor.getSortName(), allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 author.copyFrom(newAuthor);
                                 Utils.pruneList(mDb, mList);
                                 onListChanged();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks,
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 mGlobalChangeMade = mDb.globalReplaceAuthor(author, newAuthor);

                                 author.copyFrom(newAuthor);
                                 Utils.pruneList(mDb, mList);
                                 onListChanged();
                             }
                         });

        dialog.show();
    }

    /**
     * Called when user clicks the 'Save' button.
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            // no current edit, so we're good to go. Add the global flag.
            data.putExtra(UniqueId.BKEY_GLOBAL_CHANGES_MADE, mGlobalChangeMade);
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(
                this,
                /* run when user clicks 'exit' */
                new Runnable() {
                    @Override
                    public void run() {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                });
        return false;
    }

    protected SimpleListAdapter<Author> createListAdapter(@LayoutRes final int rowLayoutId,
                                                          @NonNull final ArrayList<Author> list) {
        return new AuthorListAdapter(this, rowLayoutId, list);
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        TextView rowAuthorView;
        TextView rowAuthorSortView;
    }

    protected class AuthorListAdapter
            extends SimpleListAdapter<Author> {

        AuthorListAdapter(@NonNull final Context context,
                          @LayoutRes final int rowLayoutId,
                          @NonNull final ArrayList<Author> items) {
            super(context, rowLayoutId, items);
        }

        @Override
        protected void onGetView(@NonNull final View convertView,
                                 @NonNull final Author item) {
            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.rowAuthorView = convertView.findViewById(R.id.row_author);
                holder.rowAuthorSortView = convertView.findViewById(R.id.row_author_sort);
                // Tag the parts that need it
                convertView.setTag(holder);
            }
            // Setup the variant fields in the holder.
            if (holder.rowAuthorView != null) {
                holder.rowAuthorView.setText(item.getDisplayName());
            }
            if (holder.rowAuthorSortView != null) {
                if (item.getDisplayName().equals(item.getSortName())) {
                    holder.rowAuthorSortView.setVisibility(View.GONE);
                } else {
                    holder.rowAuthorSortView.setVisibility(View.VISIBLE);
                    holder.rowAuthorSortView.setText(item.getSortName());
                }
            }
        }

        /**
         * edit the item we clicked on.
         */
        @Override
        protected void onRowClick(@NonNull final View target,
                                  @NonNull final Author item,
                                  final int position) {
            edit(item);
        }

        /**
         * delegate to the ListView host.
         */
        @Override
        protected void onListChanged() {
            EditAuthorListActivity.this.onListChanged();
        }
    }
}
