/*
 * @copyright 2013 Evan Leybourn
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
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.searches.isfdb.HandlesISFDB;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public class EditBookAnthologyFragment extends EditBookAbstractFragment implements HandlesISFDB {
    // context menu specific for Anthology
    private static final int MENU_POPULATE_ISFDB = 100;

    private EditText mTitleText;
    private EditText mPubDateText;
    private AutoCompleteTextView mAuthorText;
    private String mIsbn;
    private String mBookAuthor;
    private Button mAdd;
    private CheckBox mSame;
    private Integer mEditPosition = null;
    private ArrayList<AnthologyTitle> mList;

    /**
     * ISFDB editions (url's) of a book(isbn)
     * We'll try them one by one if the user asks for a re-try
     */
    private List<String> mEditions;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_anthology, container, false);
    }

    /**
     * Display the edit fields page
     */
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadPage();
    }

    /**
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - {@link #fillContentList};
     */
    private void loadPage() {

        final Book book = mEditManager.getBook();
        mBookAuthor = book.getString(UniqueId.KEY_AUTHOR_FORMATTED);
        mIsbn = book.getString(UniqueId.KEY_ISBN);

        // Setup the same author field
        mSame = getView().findViewById(R.id.same_author);
        mSame.setChecked(((book.getInt(UniqueId.KEY_ANTHOLOGY_MASK) & DatabaseDefinitions.DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS)
                == DatabaseDefinitions.DOM_ANTHOLOGY_NOT_AN_ANTHOLOGY));
        mSame.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState(mEditManager.getBook());
                loadPage();
            }
        });

        // Author AutoCompleteTextView
        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_dropdown_item_1line, mDb.getAuthors());
        mAuthorText = getView().findViewById(R.id.add_author);
        mAuthorText.setAdapter(author_adapter);
        mAuthorText.setVisibility(mSame.isChecked() ? View.GONE : View.VISIBLE);

        mTitleText = getView().findViewById(R.id.add_title);
        mPubDateText = getView().findViewById(R.id.add_year);

        mAdd = getView().findViewById(R.id.add_button);
        mAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String pubDate = mPubDateText.getText().toString().trim();
                String title = mTitleText.getText().toString().trim();
                String author = mAuthorText.getText().toString().trim();
                if (mSame.isChecked()) {
                    author = mBookAuthor;
                }
                AnthologyTitleListAdapterForEditing antAdapter = ((AnthologyTitleListAdapterForEditing)
                        EditBookAnthologyFragment.this.getListView().getAdapter());

                if (mEditPosition == null) {
                    AnthologyTitle anthologyTitle = new AnthologyTitle(new Author(author), title, pubDate);
                    anthologyTitle.setBookId(book.getBookId());
                    antAdapter.add(anthologyTitle);
                } else {
                    AnthologyTitle anthologyTitle = antAdapter.getItem(mEditPosition);
                    anthologyTitle.setAuthor(new Author(author));
                    anthologyTitle.setTitle(title);
                    anthologyTitle.setFirstPublication(pubDate);

                    antAdapter.notifyDataSetChanged();

                    mEditPosition = null;
                    mAdd.setText(R.string.anthology_add);
                }

                mPubDateText.setText("");
                mTitleText.setText("");
                mAuthorText.setText("");
                //fillContentList();
                mEditManager.setDirty(true);
            }
        });

        fillContentList();
    }

    /**
     * Populate the list view with the book content table
     */
    private void fillContentList() {

        final ListView listView = getListView();

        // Get all of the rows from the database and create the item list
        mList = mEditManager.getBook().getContentList();

        // Now create a simple cursor adapter and set it to display
        AnthologyTitleListAdapterForEditing adapter = new AnthologyTitleListAdapterForEditing(getActivity(), R.layout.row_edit_anthology, mList);
        listView.setAdapter(adapter);

        registerForContextMenu(listView);
        // click on a list entry, puts it in edit fields
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mEditPosition = position;
                AnthologyTitle anthologyTitle = mList.get(position);
                mPubDateText.setText(anthologyTitle.getFirstPublication());
                mTitleText.setText(anthologyTitle.getTitle());
                mAuthorText.setText(anthologyTitle.getAuthor().getDisplayName());
                mAdd.setText(R.string.anthology_save);
            }
        });
    }

    /**
     * Mimic ListActivity
     */
    private ListView getListView() {
        return (ListView) getView().findViewById(android.R.id.list);
    }

    /**
     * we got one or more editions from ISFDB
     */
    @Override
    public void onGotISFDBEditions(@NonNull final List<String> editions) {
        mEditions = editions;
        if (mEditions.size() > 0) {
            ISFDBManager.search(mEditions.get(0), this);
        }
    }

    /**
     * we got a book
     *
     * @param bookData our book from ISFDB. Any li
     */
    @Override
    public void onGotISFDBBook(@NonNull final Bundle bookData) {
        final List<AnthologyTitle> results = ArrayUtils.getAnthologyTitleFromBundle(bookData);

        if (results == null) {
            StandardDialogs.showQuickNotice(getActivity(), R.string.automatic_population_failed);
            return;
        }

        StringBuilder msg = new StringBuilder();
        if (!results.isEmpty()) {
            //FIXME: this is usually to much to display as a Message in the dialog
            for (AnthologyTitle t : results) {
                msg.append(t.getTitle()).append(", ");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(results.isEmpty() ? R.string.automatic_population_failed : R.string.anthology_confirm)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(msg)
                .create();

        if (!results.isEmpty()) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            // check if its all the same author or not
                            boolean sameAuthor = true;
                            if (results.size() > 1) {
                                Author author = results.get(0).getAuthor();
                                for (AnthologyTitle t : results) { // yes, we check 0 twice.. oh well.
                                    sameAuthor = author.equals(t.getAuthor());
                                    if (!sameAuthor) {
                                        break;
                                    }
                                }
                            }
                            mSame.setChecked(sameAuthor);
                            mList.addAll(results);
                            AnthologyTitleListAdapterForEditing adapter = ((AnthologyTitleListAdapterForEditing)
                                    EditBookAnthologyFragment.this.getListView().getAdapter());
                            adapter.notifyDataSetChanged();
                        }
                    });
        }

        // if we found multiple editions, allow a re-try with the next inline
        if (mEditions.size() > 1) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.try_again),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mEditions.remove(0);
                            ISFDBManager.search(mEditions.get(0), EditBookAnthologyFragment.this);
                        }
                    });
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();

    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, MENU_POPULATE_ISFDB, 0, R.string.populate_anthology_titles)
                .setIcon(R.drawable.ic_autorenew);
        super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_POPULATE_ISFDB:
                StandardDialogs.showQuickNotice(getActivity(), R.string.connecting_to_web_site);
                ISFDBManager.searchEditions(mIsbn, this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(@NonNull final ContextMenu menu, @NonNull final View v, @NonNull final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.MENU_DELETE_ANTHOLOGY, 0, R.string.menu_delete_anthology);
    }

    @Override
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE_ANTHOLOGY:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                AnthologyTitleListAdapterForEditing adapter = ((AnthologyTitleListAdapterForEditing) getListView().getAdapter());
                adapter.remove(adapter.getItem((int) info.id));
                mEditManager.setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(@NonNull final Book book) {
        book.setContentList(mList);
        // multiple authors is now automatically done during database access. The checkbox is only
        // a visual aid for hiding/showing the author EditText.
        // So while this command is 'correct', it does not stop (and does not bother) the user
        // setting it wrong. insert/update into the database will correctly set it.
        book.putInt(UniqueId.KEY_ANTHOLOGY_MASK,
                mSame.isChecked() ?
                        DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY
                        : DatabaseDefinitions.DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS ^ DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveState(mEditManager.getBook());
    }

    @Override
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        if (!setAllDone) {
            mFields.setAll(book);
        }
    }

    @Override
    protected void onSaveBookDetails(@NonNull final Book book) {
        super.onSaveBookDetails(book);
        saveState(book);
    }

    private class AnthologyTitleListAdapterForEditing extends AnthologyTitleListAdapter {

        AnthologyTitleListAdapterForEditing(@NonNull final Context context,
                                            @LayoutRes final int rowViewId,
                                            @NonNull final ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onRowClick(@NonNull final View v, @NonNull final AnthologyTitle item, final int position) {
            mPubDateText.setText(item.getFirstPublication());
            mTitleText.setText(item.getTitle());
            mAuthorText.setText(item.getAuthor().getDisplayName());
            mEditPosition = position;
            mAdd.setText(R.string.anthology_save);
        }

        @Override
        protected void onListChanged() {
            mEditManager.setDirty(true);
        }
    }
}
