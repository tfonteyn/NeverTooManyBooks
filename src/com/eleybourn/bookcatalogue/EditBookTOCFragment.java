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
import android.view.MenuInflater;
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
import android.widget.TextView;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.searches.isfdb.HandlesISFDB;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.BundleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab
 */
public class EditBookTOCFragment extends BookBaseFragment implements HandlesISFDB {

    public static final String TAG = "EditBookTOCFragment";

    private EditText mTitleTextView;
    private EditText mPubDateTextView;
    private AutoCompleteTextView mAuthorTextView;
    private String mIsbn;
    private String mBookAuthor;
    private Button mAddButton;
    private CompoundButton mSingleAuthor;

    @Nullable
    private Integer mEditPosition = null;
    private ArrayList<TOCEntry> mList;
    private ListView mListView;

    /**
     * ISFDB editions (url's) of a book(isbn)
     * We'll try them one by one if the user asks for a re-try
     */
    private List<String> mISFDBEditionUrls;

    /* ------------------------------------------------------------------------------------------ */
    @NonNull
    protected BookManager getBookManager() {
        //noinspection ConstantConditions
        return ((EditBookFragment)this.getParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

//    /**
//     * Ensure activity supports interface
//     */
//    @Override
//    @CallSuper
//    public void onAttach(final @NonNull Context context) {
//        super.onAttach(context);
//    }

//    @Override
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_toc, container, false);
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mPubDateTextView = getView().findViewById(R.id.add_year);
        mTitleTextView = getView().findViewById(R.id.add_title);

        // Author AutoCompleteTextView
        mAuthorTextView = getView().findViewById(R.id.add_author);
        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, mDb.getAuthorsFormattedName());
        mAuthorTextView.setAdapter(author_adapter);

        // author to use if mSingleAuthor is set to true
        mBookAuthor = getBookManager().getBook().getString(UniqueId.KEY_AUTHOR_FORMATTED);

        // used to call Search sites to populate the TOC
        mIsbn = getBookManager().getBook().getString(UniqueId.KEY_BOOK_ISBN);

        mAddButton = getView().findViewById(R.id.add_button);
        mAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String pubDate = mPubDateTextView.getText().toString().trim();
                String title = mTitleTextView.getText().toString().trim();
                String author = mAuthorTextView.getText().toString().trim();
                if (mSingleAuthor.isChecked()) {
                    author = mBookAuthor;
                }
                ArrayAdapter<TOCEntry> adapter = EditBookTOCFragment.this.getListAdapter();

                if (mEditPosition == null) {
                    adapter.add(new TOCEntry(new Author(author), title, pubDate));
                } else {
                    TOCEntry tocEntry = adapter.getItem(mEditPosition);
                    //noinspection ConstantConditions
                    tocEntry.setAuthor(new Author(author));
                    tocEntry.setTitle(title);
                    tocEntry.setFirstPublication(pubDate);

                    adapter.notifyDataSetChanged();

                    mEditPosition = null;
                    // revert to the default 'add' action
                    mAddButton.setText(R.string.btn_confirm_add);
                }

                mPubDateTextView.setText("");
                mTitleTextView.setText("");
                mAuthorTextView.setText("");
                getBookManager().setDirty(true);
            }
        });
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * No real Field's - commenting this out, but leaving as a reminder
     */
    @Override
    protected void initFields() {
        super.initFields();

        // mSingleAuthor checkbox
        //noinspection ConstantConditions
        mSingleAuthor = getView().findViewById(R.id.same_author);
        mSingleAuthor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mAuthorTextView.setVisibility(mSingleAuthor.isChecked() ? View.GONE : View.VISIBLE);
            }
        });

        mListView = getView().findViewById(android.R.id.list);
        mListView.setOnCreateContextMenuListener(this);

        // clicking on a list entry, puts it in edit fields
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mEditPosition = position;

                TOCEntry tocEntry = mList.get(position);
                mPubDateTextView.setText(tocEntry.getFirstPublication());
                mTitleTextView.setText(tocEntry.getTitle());
                mAuthorTextView.setText(tocEntry.getAuthor().getDisplayName());

                mAddButton.setText(R.string.btn_confirm_save);
            }
        });
    }

//    @CallSuper
//    @Override
//    public void onResume() {
//        super.onResume();
//    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getBookId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        // populateFields
        populateSingleAuthorStatus(book);
        populateContentList();

        // Restore default visibility
        //showHideFields(false);

        Tracker.exitOnLoadFieldsFromBook(this, book.getBookId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    private void populateSingleAuthorStatus(final @NonNull Book book) {
        int bitmask = book.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
        boolean singleAuthor = (DatabaseDefinitions.DOM_IS_ANTHOLOGY == (bitmask & DatabaseDefinitions.DOM_IS_ANTHOLOGY));
        mSingleAuthor.setChecked(singleAuthor);
        mAuthorTextView.setVisibility(singleAuthor ? View.GONE : View.VISIBLE);
    }

    /**
     * Populate the list view with the book content table
     */
    private void populateContentList() {
        // Get all of the rows from the database and create the item list
        mList = getBookManager().getBook().getTOC();

        // Now create a simple cursor adapter and set it to display
        ArrayAdapter<TOCEntry> adapter = new TOCListAdapterForEditing(requireActivity(),
                R.layout.row_edit_toc_entry, mList);
        mListView.setAdapter(adapter);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

//    @Override
//    @CallSuper
//    public void onPause() {
//        super.onPause();
//    }

    @Override
    @CallSuper
    protected void onSaveFieldsToBook(final @NonNull Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getBookId());
        super.onSaveFieldsToBook(book);

        book.putTOC(mList);

        // multiple authors is now automatically done during database access. The checkbox is only
        // a visual aid for hiding/showing the author EditText.
        // So while this command is 'correct', it does not stop (and does not bother) the user
        // setting it wrong. insert/update into the database will correctly set it by simply looking at
        // at the toc itself
        book.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                mSingleAuthor.isChecked() ?
                        DatabaseDefinitions.DOM_IS_ANTHOLOGY
                        : DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_AUTHORS ^ DatabaseDefinitions.DOM_IS_ANTHOLOGY);

        Tracker.exitOnSaveFieldsToBook(this, book.getBookId());
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu Handlers">


    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
                .setIcon(R.drawable.ic_autorenew);
        // don't call super.
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param menuItem The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB:
                StandardDialogs.showUserMessage(requireActivity(), R.string.progress_msg_connecting_to_web_site);
                ISFDBManager.searchEditions(mIsbn, this);
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    /**
     * (yes, icons are not supported and won't show. Still leaving the setIcon calls in for now.)
     */
    @Override
    @CallSuper
    public void onCreateContextMenu(final @NonNull ContextMenu menu,
                                    final @NonNull View v,
                                    final @NonNull ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, R.id.MENU_DELETE_TOC_ENTRY, 0, R.string.menu_delete_toc_entry)
                .setIcon(R.drawable.ic_delete);

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE_TOC_ENTRY:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                ArrayAdapter<TOCEntry> adapter = getListAdapter();
                adapter.remove(adapter.getItem((int) info.id));
                getBookManager().setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="ISFDB interface">
    /**
     * we got one or more editions from ISFDB
     * Store the url's locally as the user might want to try the next in line
     *
     * ENHANCE: add the url's to the options menu for retry. Remove from menu each time one is tried.
     */
    @Override
    public void onGotISFDBEditions(final @NonNull List<String> editions) {
        mISFDBEditionUrls = editions;
        if (mISFDBEditionUrls.size() > 0) {
            ISFDBManager.search(mISFDBEditionUrls.get(0), this);
        }
    }

    /**
     * we got a book
     *
     * @param bookData our book from ISFDB.
     */
    @Override
    public void onGotISFDBBook(final @NonNull Bundle bookData) {

        // update the book with series information that was gathered from the TOC
        String encoded_series_list = bookData.getString(UniqueId.BKEY_SERIES_STRING_LIST);
        if (encoded_series_list != null) {
            ArrayList<Series> inBook = getBookManager().getBook().getSeriesList();
            List<Series> series = StringList.getSeriesUtils().decode(encoded_series_list, false);
            for (Series s : series) {
                if (!inBook.contains(s)) {
                    inBook.add(s);
                }
            }
            getBookManager().getBook().putSeriesList(inBook);
        }

        // update the book with the first publication date that was gathered from the TOC
        final String bookFirstPublication = bookData.getString(UniqueId.KEY_FIRST_PUBLICATION);
        if (bookFirstPublication != null) {
            //Logger.info(this, " onGotISFDBBook: first pub=" + bookFirstPublication);
            if (getBookManager().getBook().getString(UniqueId.KEY_FIRST_PUBLICATION).isEmpty()) {
                getBookManager().getBook().putString(UniqueId.KEY_FIRST_PUBLICATION, bookFirstPublication);
            }
        }

        // finally the TOC itself; not saved here but only put on display for the user to approve
        final int tocBitMask = bookData.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
        ArrayList<TOCEntry> tocEntries = null;
        // preferably from the array.
        if (bookData.containsKey(UniqueId.BKEY_TOC_TITLES_ARRAY)) {
            tocEntries = BundleUtils.getListFromBundle(UniqueId.BKEY_TOC_TITLES_ARRAY, bookData);
            bookData.remove(UniqueId.BKEY_TOC_TITLES_ARRAY);
        } else {
            String encoded_content_list = bookData.getString(UniqueId.BKEY_TOC_STRING_LIST);
            if (encoded_content_list != null) {
                tocEntries = StringList.getTOCUtils().decode(encoded_content_list, false);
                bookData.remove(UniqueId.BKEY_TOC_STRING_LIST);
            }
        }

        boolean hasTOC = (tocEntries != null && !tocEntries.isEmpty());

        StringBuilder msg = new StringBuilder();
        if (hasTOC) {
            msg.append(getString(R.string.toc_confirm)).append("\n\n");
            for (TOCEntry t : tocEntries) {
                msg.append(t.getTitle()).append(", ");
            }
        } else {
            msg.append(getString(R.string.error_automatic_toc_population_failed));
        }

        TextView content = new TextView(this.getContext());
        content.setText(msg);
        // Not ideal but works
        content.setTextSize(14);
        //ENHANCE API 23 ?
        //content.setTextAppearance(android.R.style.TextAppearance_Small);
        //ENHANCE API 26 ?
        //content.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setView(content)
                .create();

        if (hasTOC) {
            final List<TOCEntry> finalTOCEntries = tocEntries;
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            commitISFDBData(tocBitMask, finalTOCEntries);
                        }
                    });
        }

        // if we found multiple editions, allow a re-try with the next inline
        if (mISFDBEditionUrls.size() > 1) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mISFDBEditionUrls.remove(0);
                            ISFDBManager.search(mISFDBEditionUrls.get(0), EditBookTOCFragment.this);
                        }
                    });
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final @NonNull DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    /**
     * The user approved, so add the TOC to the list on screen (still not saved to database)
     */
    private void commitISFDBData(int tocBitMask, final @NonNull List<TOCEntry> tocEntries) {
        if (tocBitMask > 0) {
            getBookManager().getBook().putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                    tocBitMask | getBookManager().getBook().getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK));
            populateSingleAuthorStatus(getBookManager().getBook());
        }

        mList.addAll(tocEntries);
        getListAdapter().notifyDataSetChanged();
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private <T extends ArrayAdapter<TOCEntry>> T getListAdapter() {
        return (T) mListView.getAdapter();
    }

    private class TOCListAdapterForEditing extends TOCListAdapter {

        TOCListAdapterForEditing(final @NonNull Context context,
                                 @SuppressWarnings("SameParameterValue") final @LayoutRes int rowViewId,
                                 final @NonNull ArrayList<TOCEntry> items) {
            super(context, rowViewId, items);
        }

        /**
         * copies the selected entry into the edit fields + sets the confirm button to reflect a save (versus add)
         */
        @Override
        public void onRowClick(final @NonNull View v, final @NonNull TOCEntry item, final int position) {
            mPubDateTextView.setText(item.getFirstPublication());
            mTitleTextView.setText(item.getTitle());
            mAuthorTextView.setText(item.getAuthor().getDisplayName());
            mEditPosition = position;
            mAddButton.setText(R.string.btn_confirm_save);
        }

        @Override
        public void onListChanged() {
            getBookManager().setDirty(true);
        }
    }
}
