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

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBResultsListener;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsFromInternetTask}
 * as this would actually introduce the ManagedTask usage which we want to phase out.
 * The {@link ISFDBResultsListener} should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 */
public class EditBookTOCFragment
        extends BookBaseFragment
        implements ISFDBResultsListener {

    public static final String TAG = "EditBookTOCFragment";

    private EditText mTitleTextView;
    private EditText mPubDateTextView;
    private AutoCompleteTextView mAuthorTextView;
    private String mIsbn;
    private String mBookAuthor;
    private Button mAddButton;
    private CompoundButton mSingleAuthor;

    @Nullable
    private Integer mEditPosition;
    private ArrayList<TOCEntry> mList;
    private ListView mListView;

    /**
     * ISFDB editions (url's) of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    private List<String> mISFDBEditionUrls;

    /* ------------------------------------------------------------------------------------------ */
    @Override
    @NonNull
    protected BookManager getBookManager() {
        //noinspection ConstantConditions
        return ((EditBookFragment) this.getParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @NonNull
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_toc, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mPubDateTextView = getView().findViewById(R.id.add_year);
        mTitleTextView = getView().findViewById(R.id.add_title);

        // Author AutoCompleteTextView
        mAuthorTextView = getView().findViewById(R.id.add_author);
        ArrayAdapter<String> authorAdapter =
                new ArrayAdapter<>(requireActivity(),
                                   android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAuthorsFormattedName());
        mAuthorTextView.setAdapter(authorAdapter);

        // author to use if mSingleAuthor is set to true
        mBookAuthor = getBookManager().getBook().getString(UniqueId.KEY_AUTHOR_FORMATTED);

        // used to call Search sites to populate the TOC
        mIsbn = getBookManager().getBook().getString(UniqueId.KEY_BOOK_ISBN);

        mAddButton = getView().findViewById(R.id.add_button);
        mAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                String pubDate = mPubDateTextView.getText().toString().trim();
                String title = mTitleTextView.getText().toString().trim();
                String author = mAuthorTextView.getText().toString().trim();
                if (mSingleAuthor.isChecked()) {
                    author = mBookAuthor;
                }
                ArrayAdapter<TOCEntry> adapter = EditBookTOCFragment.this.getListAdapter();

                if (mEditPosition == null) {
                    // adding a new entry
                    adapter.add(new TOCEntry(0, new Author(author), title, pubDate));
                } else {
                    // editing an existing entry
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


    @Override
    protected void initFields() {
        super.initFields();
        /*
         * No real Field's but might as well do these here.
         */

        // mSingleAuthor checkbox
        //noinspection ConstantConditions
        mSingleAuthor = getView().findViewById(R.id.same_author);
        mSingleAuthor.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                mAuthorTextView.setVisibility(mSingleAuthor.isChecked() ? View.GONE : View.VISIBLE);
            }
        });

        mListView = getView().findViewById(android.R.id.list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id) {
                TOCEntry tocEntry = (TOCEntry) parent.getItemAtPosition(position);
                editEntry(tocEntry, position);
            }
        });
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
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

    private void populateSingleAuthorStatus(@NonNull final Book book) {
        int bitmask = book.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
        boolean singleAuthor = (bitmask & DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_AUTHORS) == 0;
        mSingleAuthor.setChecked(singleAuthor);
        mAuthorTextView.setVisibility(singleAuthor ? View.GONE : View.VISIBLE);
    }

    /**
     * Populate the list view with the book content table.
     */
    private void populateContentList() {
        // Get all of the rows from the database and create the item list
        mList = getBookManager().getBook().getTOC();

        // Now create a simple cursor adapter and set it to display
        ArrayAdapter<TOCEntry> adapter = new TOCListAdapterForEditing(requireActivity(),
                                                                      R.layout.row_edit_toc_entry,
                                                                      mList);
        mListView.setAdapter(adapter);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    @CallSuper
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getBookId());
        super.onSaveFieldsToBook(book);

        book.putTOC(mList);

        // multiple authors is now automatically done during database access.
        // The checkbox is only a visual aid for hiding/showing the author EditText.
        // So while this command is 'correct', it does not stop (and does not bother) the user
        // setting it wrong. insert/update into the database will correctly set it by
        // simply looking at the toc itself
        int type = DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_WORKS;
        if (!mSingleAuthor.isChecked()) {
            type |= DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_AUTHORS;
        }
        book.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, type);

        Tracker.exitOnSaveFieldsToBook(this, book.getBookId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu Handlers">


    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);
        // don't call super.
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB:
                StandardDialogs.showUserMessage(requireActivity(),
                                                R.string.progress_msg_connecting_to_web_site);
                ISFDBManager.searchEditions(mIsbn, this);
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * (yes, icons are not supported and won't show. Still leaving the setIcon calls in for now.)
     */
    @Override
    @CallSuper
    public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                    @NonNull final View v,
                                    @Nullable final ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, R.id.MENU_DELETE_TOC_ENTRY, 0, R.string.menu_delete_toc_entry)
            .setIcon(R.drawable.ic_delete);

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE_TOC_ENTRY:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                ArrayAdapter<TOCEntry> adapter = getListAdapter();
                adapter.remove(adapter.getItem((int) info.id));
                getBookManager().setDirty(true);
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="ISFDB interface">

    /**
     * we got one or more editions from ISFDB.
     * Store the url's locally as the user might want to try the next in line
     * <p>
     * ENHANCE: add the url's to the options menu for retry.
     * Remove from menu each time one is tried.
     */
    @Override
    public void onGotISFDBEditions(@NonNull final List<String> editions) {
        mISFDBEditionUrls = editions;
        if (mISFDBEditionUrls.size() > 0) {
            ISFDBManager.search(mISFDBEditionUrls, this);
        }
    }

    /**
     * we got a book.
     *
     * @param bookData our book from ISFDB.
     */
    @Override
    public void onGotISFDBBook(@NonNull final Bundle bookData) {
        // update the book with series information that was gathered from the TOC
        List<Series> series = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (series != null && !series.isEmpty()) {
            ArrayList<Series> inBook = getBookManager().getBook().getSeriesList();
            // add, weeding out duplicates
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
            if (getBookManager().getBook().getString(UniqueId.KEY_FIRST_PUBLICATION).isEmpty()) {
                getBookManager().getBook().putString(UniqueId.KEY_FIRST_PUBLICATION,
                                                     bookFirstPublication);
            }
        }

        // finally the TOC itself; not saved here but only put on display for the user to approve
        final int tocBitMask = bookData.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
        ArrayList<TOCEntry> tocEntries =
                bookData.getParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY);
        boolean hasTOC = tocEntries != null && !tocEntries.isEmpty();

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
        //API: 23 ?
        //content.setTextAppearance(android.R.style.TextAppearance_Small);
        //API: 26 ?
        //content.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setView(content)
                .create();

        if (hasTOC) {
            final List<TOCEntry> finalTOCEntries = tocEntries;
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                             new DialogInterface.OnClickListener() {
                                 public void onClick(@NonNull final DialogInterface dialog,
                                                     final int which) {
                                     commitISFDBData(tocBitMask, finalTOCEntries);
                                 }
                             });
        }

        // if we found multiple editions, allow a re-try with the next inline
        if (mISFDBEditionUrls.size() > 1) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                             new DialogInterface.OnClickListener() {
                                 public void onClick(@NonNull final DialogInterface dialog,
                                                     final int which) {
                                     // remove the top one, and try again
                                     mISFDBEditionUrls.remove(0);
                                     ISFDBManager.search(mISFDBEditionUrls,
                                                         EditBookTOCFragment.this);
                                 }
                             });
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });
        dialog.show();
    }

    /**
     * The user approved, so add the TOC to the list on screen (still not saved to database).
     */
    private void commitISFDBData(final int tocBitMask,
                                 @NonNull final List<TOCEntry> tocEntries) {
        if (tocBitMask != 0) {
            getBookManager().getBook().putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, tocBitMask);
            populateSingleAuthorStatus(getBookManager().getBook());
        }

        mList.addAll(tocEntries);
        getListAdapter().notifyDataSetChanged();
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    /**
     * copy the selected entry into the edit fields,
     * and set the confirm button to reflect a save (versus add).
     */
    private void editEntry(@NonNull final TOCEntry item,
                           final int position) {
        // temp. debug to see who really calls us while refactoring.
        Logger.debug("editEntry stacktrace");

        mPubDateTextView.setText(item.getFirstPublication());
        mTitleTextView.setText(item.getTitle());
        mAuthorTextView.setText(item.getAuthor().getDisplayName());
        mEditPosition = position;
        mAddButton.setText(R.string.btn_confirm_save);
    }

    @SuppressWarnings("unchecked")
    private <T extends ArrayAdapter<TOCEntry>> T getListAdapter() {
        return (T) mListView.getAdapter();
    }

    private class TOCListAdapterForEditing
            extends SimpleListAdapter<TOCEntry> {

        TOCListAdapterForEditing(@NonNull final Context context,
                                 @SuppressWarnings("SameParameterValue")
                                 @LayoutRes final int rowLayoutId,
                                 @NonNull final ArrayList<TOCEntry> items) {
            super(context, rowLayoutId, items);
        }

        @Override
        public void onRowClick(@NonNull final View target,
                               @NonNull final TOCEntry item,
                               final int position) {
            editEntry(item, position);
        }

        /**
         * We're dirty...
         */
        @Override
        public void onListChanged() {
            getBookManager().setDirty(true);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final TOCEntry item) {

            Holder holder = ViewTagger.getTag(convertView);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.titleView = convertView.findViewById(R.id.title);
                holder.authorView = convertView.findViewById(R.id.author);
                holder.firstPublicationView = convertView.findViewById(R.id.year);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
            }

            holder.titleView.setText(item.getTitle());
            // optional
            if (holder.authorView != null) {
                holder.authorView.setText(item.getAuthor().getDisplayName());
            }
            // optional
            if (holder.firstPublicationView != null) {
                String year = item.getFirstPublication();
                if (year.isEmpty()) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                    holder.firstPublicationView.setText(
                            getContext().getString(R.string.brackets, item.getFirstPublication()));
                }
            }
        }

        /**
         * Holder pattern for each row.
         */
        private class Holder {

            TextView titleView;
            TextView authorView;
            TextView firstPublicationView;
        }
    }
}
