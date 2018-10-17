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
import android.support.annotation.CallSuper;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.isfdb.HandlesISFDB;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookActivity} and displays the Anthology (aka Content) Tab
 */
public class EditBookAnthologyFragment extends BookAbstractFragment implements HandlesISFDB {
    /** context menu specific for Anthology */
    private static final int MENU_POPULATE_ISFDB = 100;

    private EditText mTitleText;
    private EditText mPubDateText;
    private AutoCompleteTextView mAuthorText;
    private String mIsbn;
    private String mBookAuthor;
    private Button mAddButton;
    private CompoundButton mSingleAuthor;

    @Nullable
    private Integer mEditPosition = null;
    private ArrayList<AnthologyTitle> mList;
    private ListView mListView;

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
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - {@link #populateContentList};
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Book book = getBook();

        // Author AutoCompleteTextView
        //noinspection ConstantConditions
        mAuthorText = getView().findViewById(R.id.add_author);
        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, mDb.getAuthors());
        mAuthorText.setAdapter(author_adapter);

        // mSingleAuthor checkbox
        mSingleAuthor = getView().findViewById(R.id.same_author);
        mSingleAuthor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mAuthorText.setVisibility(mSingleAuthor.isChecked() ? View.GONE : View.VISIBLE);
            }
        });

        // Setup the same author field and makes the Author visible or not
        int bitmask = book.getInt(UniqueId.KEY_ANTHOLOGY_BITMASK);
        boolean singleAuthor = (1 == (bitmask & DatabaseDefinitions.DOM_ANTHOLOGY_SINGLE_AUTHOR));
        initSingleAuthorStatus(singleAuthor);

        mBookAuthor = book.getString(UniqueId.KEY_AUTHOR_FORMATTED);
        mIsbn = book.getString(UniqueId.KEY_BOOK_ISBN);
        mPubDateText = getView().findViewById(R.id.add_year);
        mTitleText = getView().findViewById(R.id.add_title);
        mListView = getView().findViewById(android.R.id.list);

        mAddButton = getView().findViewById(R.id.add_button);
        mAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String pubDate = mPubDateText.getText().toString().trim();
                String title = mTitleText.getText().toString().trim();
                String author = mAuthorText.getText().toString().trim();
                if (mSingleAuthor.isChecked()) {
                    author = mBookAuthor;
                }
                ArrayAdapter<AnthologyTitle> adapter = EditBookAnthologyFragment.this.getListAdapter();

                if (mEditPosition == null) {
                    AnthologyTitle anthologyTitle = new AnthologyTitle(new Author(author), title, pubDate);
                    anthologyTitle.setBookId(book.getBookId());
                    // not bothering with position, the insert to the database takes care of that
                    adapter.add(anthologyTitle);
                } else {
                    AnthologyTitle anthologyTitle = adapter.getItem(mEditPosition);
                    //noinspection ConstantConditions
                    anthologyTitle.setAuthor(new Author(author));
                    anthologyTitle.setTitle(title);
                    anthologyTitle.setFirstPublication(pubDate);

                    adapter.notifyDataSetChanged();

                    mEditPosition = null;
                    mAddButton.setText(R.string.anthology_add);
                }

                mPubDateText.setText("");
                mTitleText.setText("");
                mAuthorText.setText("");
                //populateContentList();
                getEditBookManager().setDirty(true);
            }
        });

        populateContentList();
    }

    public void initSingleAuthorStatus(final boolean singleAuthor) {
        mSingleAuthor.setChecked(singleAuthor);
        mAuthorText.setVisibility(singleAuthor ? View.GONE : View.VISIBLE);
    }

    /**
     * Populate the list view with the book content table
     */
    private void populateContentList() {
        // Get all of the rows from the database and create the item list
        mList = getBook().getContentList();

        // Now create a simple cursor adapter and set it to display
        ArrayAdapter<AnthologyTitle> adapter = new AnthologyTitleListAdapterForEditing(requireActivity(),
                R.layout.row_edit_anthology, mList);
        mListView.setAdapter(adapter);

        registerForContextMenu(mListView);
        // click on a list entry, puts it in edit fields
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mEditPosition = position;
                AnthologyTitle anthologyTitle = mList.get(position);
                mPubDateText.setText(anthologyTitle.getFirstPublication());
                mTitleText.setText(anthologyTitle.getTitle());
                mAuthorText.setText(anthologyTitle.getAuthor().getDisplayName());
                mAddButton.setText(R.string.anthology_save);
            }
        });
    }

    /**
     * Mimic ListActivity
     */
    @NonNull
    private ListView getListView() {
        return mListView;
    }

    @SuppressWarnings("unchecked")
    private <T extends ArrayAdapter<AnthologyTitle>> T getListAdapter() {
        return (T) getListView().getAdapter();
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
     * @param bookData our book from ISFDB.
     */
    @Override
    public void onGotISFDBBook(@NonNull final Bundle bookData) {
        String encoded_content_list = bookData.getString(UniqueId.BKEY_ANTHOLOGY_DETAILS);
        final List<AnthologyTitle> results = ArrayUtils.getAnthologyTitleUtils().decodeList(encoded_content_list, false);
        bookData.remove(UniqueId.BKEY_ANTHOLOGY_DETAILS);

        String encoded_series_list = bookData.getString(UniqueId.BKEY_SERIES_DETAILS);
        if (encoded_content_list != null) {
            ArrayList<Series> inBook = getBook().getSeriesList();
            List<Series> series = ArrayUtils.getSeriesUtils().decodeList(encoded_series_list, false);
            for (Series s : series) {
                if (!inBook.contains(s)) {
                    inBook.add(s);
                }
            }
            getBook().setSeriesList(inBook);
            //Logger.info("onGotISFDBBook: series=" + series);
        }

        String bookFirstPublication = bookData.getString(UniqueId.KEY_FIRST_PUBLICATION);
        if (bookFirstPublication != null) {
            //Logger.info("onGotISFDBBook: first pub=" + bookFirstPublication);
            if (getBook().getString(UniqueId.KEY_FIRST_PUBLICATION).isEmpty()) {
                getBook().putString(UniqueId.KEY_FIRST_PUBLICATION, bookFirstPublication);
            }
        }

        StringBuilder msg = new StringBuilder();
        if (!results.isEmpty()) {
            //FIXME: this is usually to much to display as a Message in the dialog
            for (AnthologyTitle t : results) {
                msg.append(t.getTitle()).append(", ");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(results.isEmpty() ? R.string.error_anthology_automatic_population_failed : R.string.anthology_confirm)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(msg)
                .create();

        if (!results.isEmpty()) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            initSingleAuthorStatus(AnthologyTitle.isSingleAuthor(results));
                            mList.addAll(results);
                            ArrayAdapter<AnthologyTitle> adapter = EditBookAnthologyFragment.this.getListAdapter();
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
                    public void onClick(@NonNull final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();

    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     * Need to use this as we want the menu cleared before.
     */
    @Override
    @CallSuper
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
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_POPULATE_ISFDB:
                StandardDialogs.showBriefMessage(requireActivity(), R.string.connecting_to_web_site);
                ISFDBManager.searchEditions(mIsbn, this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    public void onCreateContextMenu(@NonNull final ContextMenu menu, @NonNull final View v, @NonNull final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.MENU_DELETE_ANTHOLOGY, 0, R.string.menu_delete_anthology);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE_ANTHOLOGY:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                ArrayAdapter<AnthologyTitle> adapter = getListAdapter();
                adapter.remove(adapter.getItem((int) info.id));
                getEditBookManager().setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(@NonNull final Book book) {
        book.setContentList(mList);
        // multiple authors is now automatically done during database access. The checkbox is only
        // a visual aid for hiding/showing the author EditText.
        // So while this command is 'correct', it does not stop (and does not bother) the user
        // setting it wrong. insert/update into the database will correctly set it by simply looking at
        // at the toc itself
        book.putInt(UniqueId.KEY_ANTHOLOGY_BITMASK,
                mSingleAuthor.isChecked() ?
                        DatabaseDefinitions.DOM_ANTHOLOGY_SINGLE_AUTHOR
                        : DatabaseDefinitions.DOM_ANTHOLOGY_MULTIPLE_AUTHORS ^ DatabaseDefinitions.DOM_ANTHOLOGY_SINGLE_AUTHOR);
    }

    @Override
    @CallSuper
    public void onPause() {
        super.onPause();
        saveState(getBook());
    }

    @Override
    @CallSuper
    protected void onSaveBookDetails(@NonNull final Book book) {
        super.onSaveBookDetails(book);
        saveState(book);
    }

    private class AnthologyTitleListAdapterForEditing extends AnthologyTitleListAdapter {

        AnthologyTitleListAdapterForEditing(@NonNull final Context context,
                                            @SuppressWarnings("SameParameterValue") @LayoutRes final int rowViewId,
                                            @NonNull final ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onRowClick(@NonNull final View v, @NonNull final AnthologyTitle item, final int position) {
            mPubDateText.setText(item.getFirstPublication());
            mTitleText.setText(item.getTitle());
            mAuthorText.setText(item.getAuthor().getDisplayName());
            mEditPosition = position;
            mAddButton.setText(R.string.anthology_save);
        }

        @Override
        protected void onListChanged() {
            getEditBookManager().setDirty(true);
        }
    }
}
