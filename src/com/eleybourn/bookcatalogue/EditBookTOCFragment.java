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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBGetBookTask;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBGetEditionsTask;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBResultsListener;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsFromInternetTask}
 * as this would actually introduce the ManagedTask usage which we want to phase out.
 * The {@link ISFDBResultsListener} should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 */
public class EditBookTOCFragment
        extends EditBookBaseFragment
        implements ISFDBResultsListener {

    /** Fragment manager tag. */
    public static final String TAG = EditBookTOCFragment.class.getSimpleName();

    private EditText mTitleTextView;
    private EditText mPubDateTextView;
    private AutoCompleteTextView mAuthorTextView;
    /** the book. */
    private String mIsbn;
    /** primary author of the book. */
    private String mBookAuthor;
    /** add the edited info to the list. */
    private Button mAddButton;
    /** checkbox to hide/show the author edit field. */
    private CompoundButton mSingleAuthor;

    /** position of row we're currently editing. */
    @Nullable
    private Integer mEditPosition;

    private ArrayList<TocEntry> mList;
    private ListView mListView;

    /**
     * ISFDB editions (url's) of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    private ArrayList<String> mISFDBEditionUrls;

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    /* ------------------------------------------------------------------------------------------ */
    @Override
    @NonNull
    protected BookManager getBookManager() {
        return ((EditBookFragment) requireParentFragment()).getBookManager();
    }

    @Override
    @Nullable
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
        super.onActivityCreated(savedInstanceState);

        View view = requireView();
        mPubDateTextView = view.findViewById(R.id.first_publication);
        mTitleTextView = view.findViewById(R.id.title);

        // Author AutoCompleteTextView
        mAuthorTextView = view.findViewById(R.id.author);
        ArrayAdapter<String> authorAdapter =
                new ArrayAdapter<>(requireActivity(),
                                   android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAuthorsFormattedName());
        mAuthorTextView.setAdapter(authorAdapter);

        // author to use if mSingleAuthor is set to true
        mBookAuthor = getBookManager().getBook().getString(UniqueId.KEY_AUTHOR_FORMATTED);

        // used to call Search sites to populate the TOC
        mIsbn = getBookManager().getBook().getString(UniqueId.KEY_ISBN);

        mAddButton = view.findViewById(R.id.btn_add);
        mAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                addOrUpdateEntry();
            }
        });
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    @Override
    protected void initFields() {
        super.initFields();

        /* Anthology is provided as a boolean, see {@link Book#initValidators()}*/
        mFields.add(R.id.is_anthology, Book.HAS_MULTIPLE_WORKS);
//               .getView().setOnClickListener(
//                new View.OnClickListener() {
//                    public void onClick(@NonNull final View v) {
//                        Checkable cb = (Checkable) v;
//
//                    }
//                });

        /*
         * No real Field's but might as well do these here.
         */
        View view = requireView();
        // mSingleAuthor checkbox
        mSingleAuthor = view.findViewById(R.id.same_author);
        mSingleAuthor.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                mAuthorTextView.setVisibility(mSingleAuthor.isChecked() ? View.GONE : View.VISIBLE);
            }
        });

        mListView = view.findViewById(android.R.id.list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id) {
                TocEntry tocEntry = (TocEntry) parent.getItemAtPosition(position);
                Logger.debug("calling editEntry from onItemClick");
                editEntry(tocEntry, position);
            }
        });
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        boolean isAnt = book.isBitSet(UniqueId.KEY_TOC_BITMASK,
                                      TocEntry.Type.MULTIPLE_WORKS);
        mFields.getField(R.id.is_anthology).setValue(isAnt ? "1" : "0");

        // populateFields
        populateSingleAuthorStatus(book);
        populateContentList();

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    /**
     * Show or hide the author field.
     */
    private void populateSingleAuthorStatus(@NonNull final Book book) {
        boolean singleAuthor = !book.isBitSet(UniqueId.KEY_TOC_BITMASK,
                                              TocEntry.Type.MULTIPLE_AUTHORS);
        mSingleAuthor.setChecked(singleAuthor);
        mAuthorTextView.setVisibility(singleAuthor ? View.GONE : View.VISIBLE);
    }

    /**
     * Populate the list view with the book content table.
     */
    private void populateContentList() {
        // Get all of the rows and create the item list
        mList = getBookManager().getBook().getList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // Create a simple array adapter and set it to display
        ArrayAdapter<TocEntry> adapter = new TOCListAdapterForEditing(requireActivity(), mList);
        mListView.setAdapter(adapter);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    @CallSuper
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getId());
        super.onSaveFieldsToBook(book);

        // as a reminder....
        book.putList(UniqueId.BKEY_TOC_ENTRY_ARRAY, mList);

        // multiple authors is now automatically done during database access.
        // The checkbox is only a visual aid for hiding/showing the author EditText.
        // So while this command is 'correct', it does not stop (and does not bother) the user
        // setting it wrong. insert/update into the database will correctly set it by
        // simply looking at the toc itself
        int type = TocEntry.Type.MULTIPLE_WORKS;
        if (!mSingleAuthor.isChecked()) {
            type |= TocEntry.Type.MULTIPLE_AUTHORS;
        }
        book.putLong(UniqueId.KEY_TOC_BITMASK, type);

        Tracker.exitOnSaveFieldsToBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu Handlers">

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);
        // don't call super. We don't want the clutter in this tab.
    }

    /**
     * Called when a menu item is selected.
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
                UserMessage.showUserMessage(requireActivity(),
                                            R.string.progress_msg_connecting_to_web_site);
                new ISFDBGetEditionsTask(mIsbn, this).execute();
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
                ArrayAdapter<TocEntry> adapter = getListAdapter();
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
    public void onGotISFDBEditions(@Nullable final ArrayList<String> editions) {
        mISFDBEditionUrls = editions != null ? editions : new ArrayList<String>();
        if (!mISFDBEditionUrls.isEmpty()) {
            new ISFDBGetBookTask(mISFDBEditionUrls, false, this).execute();
        }
    }

    /**
     * we got a book.
     *
     * @param bookData our book from ISFDB.
     */
    @Override
    public void onGotISFDBBook(@Nullable final Bundle bookData) {
        if (bookData == null) {
            return;
        }

        // update the book with series information that was gathered from the TOC
        List<Series> series = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (series != null && !series.isEmpty()) {
            ArrayList<Series> inBook = getBookManager().getBook()
                                                       .getList(UniqueId.BKEY_SERIES_ARRAY);
            // add, weeding out duplicates
            for (Series s : series) {
                if (!inBook.contains(s)) {
                    inBook.add(s);
                }
            }
            getBookManager().getBook().putList(UniqueId.BKEY_SERIES_ARRAY, inBook);
        }

        // update the book with the first publication date that was gathered from the TOC
        final String bookFirstPublication = bookData.getString(UniqueId.KEY_DATE_FIRST_PUBLISHED);
        if (bookFirstPublication != null) {
            if (getBookManager().getBook().getString(UniqueId.KEY_DATE_FIRST_PUBLISHED).isEmpty()) {
                getBookManager().getBook().putString(UniqueId.KEY_DATE_FIRST_PUBLISHED,
                                                     bookFirstPublication);
            }
        }

        // finally the TOC itself; not saved here but only put on display for the user to approve
        FragmentManager fm = requireFragmentManager();
        if (fm.findFragmentByTag(ConfirmTOC.TAG) == null) {
            ConfirmTOC.newInstance(this, bookData, mISFDBEditionUrls.size() > 1)
                      .show(fm, ConfirmTOC.TAG);
        }
    }

    /**
     * The user approved, so add the TOC to the list on screen (still not saved to database).
     */
    private void commitISFDBData(final long tocBitMask,
                                 @NonNull final List<TocEntry> tocEntries) {
        if (tocBitMask != 0) {
            getBookManager().getBook().putLong(UniqueId.KEY_TOC_BITMASK, tocBitMask);
            populateSingleAuthorStatus(getBookManager().getBook());
        }

        mList.addAll(tocEntries);
        getListAdapter().notifyDataSetChanged();
    }

    /**
     * Start a task to get the next edition of this book (that we know of).
     */
    private void getNextEdition() {
        // remove the top one, and try again
        mISFDBEditionUrls.remove(0);
        new ISFDBGetBookTask(mISFDBEditionUrls, false, this).execute();
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    /**
     * copy the selected entry into the edit fields,
     * and set the confirm button to reflect a save (versus add).
     */
    private void editEntry(@NonNull final TocEntry item,
                           final int position) {

        mPubDateTextView.setText(item.getFirstPublication());
        mTitleTextView.setText(item.getTitle());
        mAuthorTextView.setText(item.getAuthor().getDisplayName());
        mEditPosition = position;
        mAddButton.setText(R.string.btn_confirm_save);
    }

    /**
     * Add the author/title from the edit fields as a new row in the TOC list.
     */
    private void addOrUpdateEntry() {
        ArrayAdapter<TocEntry> adapter = getListAdapter();
        String pubDate = mPubDateTextView.getText().toString().trim();
        String title = mTitleTextView.getText().toString().trim();
        String author = mAuthorTextView.getText().toString().trim();

        if (mSingleAuthor.isChecked()) {
            author = mBookAuthor;
        }

        if (mEditPosition == null) {
            // add the new entry
            adapter.add(new TocEntry(Author.fromString(author), title, pubDate));
        } else {
            // editing an existing entry
            TocEntry tocEntry = adapter.getItem(mEditPosition);
            //noinspection ConstantConditions
            tocEntry.setAuthor(Author.fromString(author));
            tocEntry.setTitle(title);
            tocEntry.setFirstPublication(pubDate);

            adapter.notifyDataSetChanged();

            mEditPosition = null;
            // revert to the default 'add' action
            mAddButton.setText(R.string.btn_confirm_add);
        }

        // done adding, clear fields for the next one.
        mPubDateTextView.setText("");
        mTitleTextView.setText("");
        mAuthorTextView.setText("");
        getBookManager().setDirty(true);
    }


    @SuppressWarnings("unchecked")
    private <T extends ArrayAdapter<TocEntry>> T getListAdapter() {
        return (T) mListView.getAdapter();
    }

    /**
     * Will survive a rotation, but not a killed activity.
     * <p>
     * Uses setTargetFragment/getTargetFragment with type {@link EditBookTOCFragment}.
     */
    public static class ConfirmTOC
            extends DialogFragment {

        public static final String TAG = ConfirmTOC.class.getSimpleName();
        private static final String BKEY_HAS_OTHER_EDITIONS = "hasOtherEditions";

        /**
         * Constructor.
         *
         * @return the instance
         */
        public static ConfirmTOC newInstance(@NonNull final Fragment target,
                                             @NonNull final Bundle bookData,
                                             final boolean hasOtherEditions) {
            ConfirmTOC frag = new ConfirmTOC();
            bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);
            frag.setTargetFragment(target, 0);
            frag.setArguments(bookData);
            return frag;
        }

        /**
         * Get the textSize attribute of the standard "TextAppearance_Small" style.
         * API 23 required to use it directly :(
         *
         * @param context of caller
         *
         * @return the size
         */
        private static int getTextAppearanceSmallTextSize(@NonNull final Context context) {
            // The attributes you want retrieved
            int[] attrs = {android.R.attr.textSize};
            TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_Small,
                                                           attrs);
            int size = ta.getIndex(0);
            ta.recycle();
            return size;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final EditBookTOCFragment targetFragment = (EditBookTOCFragment) getTargetFragment();
            Bundle args = requireArguments();
            boolean hasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS);
            final long tocBitMask = args.getLong(UniqueId.KEY_TOC_BITMASK);
            ArrayList<TocEntry> tocEntries =
                    args.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            boolean hasTOC = tocEntries != null && !tocEntries.isEmpty();

            StringBuilder msg = new StringBuilder();
            if (hasTOC) {
                msg.append(getString(R.string.warning_toc_confirm)).append("\n\n");
                for (TocEntry t : tocEntries) {
                    msg.append(t.getTitle()).append(", ");
                }
            } else {
                msg.append(getString(R.string.error_auto_toc_population_failed));
            }

            TextView content = new TextView(getContext());
            content.setText(msg);
            //noinspection ConstantConditions
            content.setTextSize(getTextAppearanceSmallTextSize(getContext()));
            //API: 23:
            //content.setTextAppearance(android.R.style.TextAppearance_Small);
            //API: 26 ?
            //content.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);

            final AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setView(content)
                    .create();

            if (hasTOC) {
                final List<TocEntry> finalTocEntryList = tocEntries;
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 new DialogInterface.OnClickListener() {
                                     public void onClick(@NonNull final DialogInterface dialog,
                                                         final int which) {
                                         //noinspection ConstantConditions
                                         targetFragment.commitISFDBData(tocBitMask,
                                                                        finalTocEntryList);
                                     }
                                 });
            }

            // if we found multiple editions, allow a re-try with the next inline
            if (hasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                                 new DialogInterface.OnClickListener() {
                                     public void onClick(@NonNull final DialogInterface dialog,
                                                         final int which) {
                                         //noinspection ConstantConditions
                                         targetFragment.getNextEdition();
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
            return dialog;
        }
    }

    private class TOCListAdapterForEditing
            extends SimpleListAdapter<TocEntry> {

        /**
         * Constructor.
         */
        TOCListAdapterForEditing(@NonNull final Context context,
                                 @NonNull final ArrayList<TocEntry> items) {
            super(context, R.layout.row_edit_toc_entry, items);
        }

        @Override
        public void onRowClick(@NonNull final View target,
                               @NonNull final TocEntry item,
                               final int position) {
            Logger.debug("calling editEntry from onRowClick");
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
                              @NonNull final TocEntry item) {

            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                holder = new Holder(convertView);
            }

            holder.titleView.setText(item.getTitle());
            holder.authorView.setText(item.getAuthor().getDisplayName());

            String year = item.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(getString(R.string.brackets, year));
            }
        }

        /**
         * Holder pattern for each row.
         */
        private class Holder {

            @NonNull
            final TextView titleView;
            @NonNull
            final TextView authorView;
            @NonNull
            final TextView firstPublicationView;

            Holder(@NonNull final View rowView) {
                titleView = rowView.findViewById(R.id.title);
                authorView = rowView.findViewById(R.id.author);
                firstPublicationView = rowView.findViewById(R.id.year);

                rowView.setTag(this);
            }
        }
    }
}
