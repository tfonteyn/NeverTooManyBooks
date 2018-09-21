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
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter.AnthologyTitleExistsException;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.searches.wikipedia.SearchWikipediaEntryHandler;
import com.eleybourn.bookcatalogue.searches.wikipedia.SearchWikipediaHandler;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class EditBookAnthologyFragment extends EditBookAbstractFragment {

    private static final int DELETE_ID = Menu.FIRST;
    private static final int POPULATE = Menu.FIRST + 1;

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors
     *
     * Original code had:
     * CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";
     *
     * Android Studio:
     * Reports character escapes that are replaceable with the unescaped character without a
     * change in meaning. Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * For example the regex [\.] is identical to [.]
     */
    private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";

    private EditText mTitleText;
    private AutoCompleteTextView mAuthorText;
    private String mBookAuthor;
    private String mBookTitle;
    private Button mAdd;
    private CheckBox mSame;
    private Integer mEditPosition = null;
    private ArrayList<AnthologyTitle> mList;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_anthology, container, false);
    }

    /**
     * Display the edit fields page
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadPage();
    }

    /**
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - {@link #fillAnthology};
     */
    private void loadPage() {

        final BookData book = mEditManager.getBookData();
        mBookAuthor = book.getString(UniqueId.KEY_AUTHOR_FORMATTED);
        mBookTitle = book.getString(UniqueId.KEY_TITLE);

        // Setup the same author field
        mSame = getView().findViewById(R.id.same_author);
        mSame.setChecked(((book.getInt(UniqueId.KEY_ANTHOLOGY_MASK) & TableInfo.ColumnInfo.ANTHOLOGY_MULTIPLE_AUTHORS) == TableInfo.ColumnInfo.ANTHOLOGY_NO));
        mSame.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState(mEditManager.getBookData());
                loadPage();
            }
        });

        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mDb.getAuthors());
        mAuthorText = getView().findViewById(R.id.add_author);
        mAuthorText.setAdapter(author_adapter);
        mAuthorText.setVisibility(mSame.isChecked() ? View.GONE : View.VISIBLE);

        mTitleText = getView().findViewById(R.id.add_title);

        mAdd = getView().findViewById(R.id.row_add);
        mAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    String title = mTitleText.getText().toString();
                    String author = mAuthorText.getText().toString();
                    if (mSame.isChecked()) {
                        author = mBookAuthor;
                    }
                    AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) EditBookAnthologyFragment.this.getListView().getAdapter());
                    if (mEditPosition == null) {
                        AnthologyTitle anthology = new AnthologyTitle(book.getRowId(), new Author(author), title);
                        adapter.add(anthology);
                    } else {
                        AnthologyTitle anthology = adapter.getItem(mEditPosition);
                        anthology.setAuthor(new Author(author));
                        anthology.setTitle(title);
                        mEditPosition = null;
                        mAdd.setText(R.string.anthology_add);
                    }
                    mTitleText.setText("");
                    mAuthorText.setText("");
                    //fillAnthology(currentPosition);
                    mEditManager.setDirty(true);
                } catch (AnthologyTitleExistsException e) {
                    Toast.makeText(getActivity(), R.string.the_title_already_exists, Toast.LENGTH_LONG).show();
                }
            }
        });

        fillAnthology();
    }

    /**
     * Populate the bookEditAnthology view
     */
    private void fillAnthology() {

        // Get all of the rows from the database and create the item list
        mList = mEditManager.getBookData().getAnthologyTitles();

        // Now create a simple cursor adapter and set it to display
        AnthologyTitleListAdapter adapter = new AnthologyTitleListAdapter(getActivity(), R.layout.row_edit_anthology, mList);
        getListView().setAdapter(adapter);

        registerForContextMenu(getListView());
        getListView().setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mEditPosition = position;
                AnthologyTitle anthology = mList.get(position);
                mTitleText.setText(anthology.getTitle());
                mAuthorText.setText(anthology.getAuthor().getDisplayName());
                mAdd.setText(R.string.anthology_save);
            }
        });
    }

    private ListView getListView() {
        return (ListView) getView().findViewById(android.R.id.list);
    }

//    /**
//     * Scroll to the current group
//     */
//    private void gotoTitle(int id) {
//        try {
//            ListView view = this.getListView();
//            view.setSelection(id);
//        } catch (Exception e) {
//            Logger.logError(e);
//        }
//        return;
//    }

    /**
     * FIXME: android.os.NetworkOnMainThreadException, use a task...
     */
    private void searchWikipedia() {
        String host = "https://en.wikipedia.org";
        String pathAuthor = mBookAuthor.replace(" ", "+");
        pathAuthor = pathAuthor.replace(",", "");
        // Strip everything past the , from the title
        String pathTitle = mBookTitle;
        int comma = pathTitle.indexOf(",");
        if (comma > 0) {
            pathTitle = pathTitle.substring(0, comma);
        }
        pathTitle = pathTitle.replace(" ", "+");
        String path = host + "/w/index.php?title=Special:Search&search=%22" + pathTitle + "%22+" + pathAuthor + "";


        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        SearchWikipediaHandler handler = new SearchWikipediaHandler();
        SearchWikipediaEntryHandler entryHandler = new SearchWikipediaEntryHandler();

        boolean found = false;
        try {
            parser = factory.newSAXParser();
            parser.parse(Utils.getInputStream(new URL(path)), handler);

            String[] links = handler.getLinks();
            for (String link : links) {
                if (link.isEmpty() || found) {
                    break;
                }
                parser = factory.newSAXParser();
                try {
                    parser.parse(Utils.getInputStream(new URL(host + link)), entryHandler);
                    ArrayList<String> titles = entryHandler.getList();
                    /* Display the confirm dialog */
                    if (titles.size() > 0) {
                        showAnthologyConfirm(titles);
                        found = true;
                    }
                } catch (RuntimeException e) {
                    Logger.logError(e);
                    Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                }
            }
            if (!found) {
                Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
            Logger.logError(e);
        }
        fillAnthology();
    }

    private void showAnthologyConfirm(final ArrayList<String> titles) {
        final BookData book = mEditManager.getBookData();
        StringBuilder anthology_title = new StringBuilder();
        for (int j = 0; j < titles.size(); j++) {
            anthology_title.append("* ").append(titles.get(j)).append("\n");
        }

        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setMessage(anthology_title).create();
        alertDialog.setTitle(R.string.anthology_confirm);
        alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                this.getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        for (int j = 0; j < titles.size(); j++) {
                            String anthology_title = titles.get(j);
                            anthology_title = anthology_title + ", ";
                            String anthology_author = mBookAuthor;
                            // Does the string look like "Hindsight by Jack Williamson"
                            int pos = anthology_title.indexOf(" by ");
                            if (pos > 0) {
                                anthology_author = anthology_title.substring(pos + 4);
                                anthology_title = anthology_title.substring(0, pos);
                            }
                            // Trim extraneous punctuation and whitespace from the titles and authors
                            anthology_author = anthology_author.trim()
                                    .replace("\n", " ")
                                    .replaceAll(CLEANUP_REGEX, "")
                                    .trim();
                            anthology_title = anthology_title.trim()
                                    .replace("\n", " ")
                                    .replaceAll(CLEANUP_REGEX, "")
                                    .trim();
                            AnthologyTitle anthology = new AnthologyTitle(book.getRowId(), new Author(anthology_author), anthology_title);
                            mList.add(anthology);
                        }
                        AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) EditBookAnthologyFragment.this.getListView().getAdapter());
                        adapter.notifyDataSetChanged();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                this.getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @SuppressWarnings("EmptyMethod")
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                    }
                });
        alertDialog.show();

    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        MenuItem populate = menu.add(0, POPULATE, 0, R.string.populate_anthology_titles);
        populate.setIcon(android.R.drawable.ic_menu_add);
        super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case POPULATE:
                searchWikipedia();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) EditBookAnthologyFragment.this.getListView().getAdapter());
                adapter.remove(adapter.getItem((int) info.id));
                mEditManager.setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(BookData book) {
        book.setAnthologyTitles(mList);
        book.putInt(UniqueId.KEY_ANTHOLOGY_MASK,
                mSame.isChecked() ?
                        TableInfo.ColumnInfo.ANTHOLOGY_IS_ANTHOLOGY
                        : TableInfo.ColumnInfo.ANTHOLOGY_MULTIPLE_AUTHORS ^ TableInfo.ColumnInfo.ANTHOLOGY_IS_ANTHOLOGY);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveState(mEditManager.getBookData());
    }

    @Override
    protected void onLoadBookDetails(@NonNull final BookData bookData, final boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(bookData);
    }

    @Override
    protected void onSaveBookDetails(@NonNull final BookData bookData) {
        super.onSaveBookDetails(bookData);
        saveState(bookData);
    }

    protected class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {

        AnthologyTitleListAdapter(@NonNull final Context context,
                                  final int rowViewId,
                                  @NonNull final ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(@NonNull final View convertView,
                                   @NonNull final AnthologyTitle item,
                                   final int position) {
            TextView author = convertView.findViewById(R.id.row_author);
            author.setText(item.getAuthor().getDisplayName());
            TextView title = convertView.findViewById(R.id.row_title);
            title.setText(item.getTitle());
        }

        @Override
        protected void onRowClick(@NonNull final View v, @NonNull final AnthologyTitle item, final int position) {
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
