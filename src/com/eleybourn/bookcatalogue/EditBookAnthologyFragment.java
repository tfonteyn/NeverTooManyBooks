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
import android.widget.Toast;

import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditBookAnthologyFragment extends EditBookAbstractFragment {

    private static final int POPULATE_ISFDB = Menu.FIRST + 1;

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
     *
     * So that became:
     * private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";
     *
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*(\\-=_+]*$";
    /**
     * ISFDB
     *  find the publication year of a content entry
     *  pattern finds (1960), group 1 will then contain the pure 1960
     */
    private static final Pattern YEAR_FROM_ISFDB_STORY_LI = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");

    private EditText mTitleText;
    private EditText mYearText;
    private AutoCompleteTextView mAuthorText;
    private long mBookId;
    private String mIsbn;
    private String mBookAuthor;
    private String mBookTitle;
    private Button mAdd;
    private CheckBox mSame;
    private Integer mEditPosition = null;
    private ArrayList<AnthologyTitle> mList;

    /**
     * task queue for the searching/parsing of content (ant titles)
     */
    private SimpleTaskQueue mAntFetcher = null;

    /**
     * ISFDB
     *  book urls for all editions found in the isbn search. We'll try them one by one if the user asks for a re-try
     */
    private List<String> mISFDBUrls;

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
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadPage();
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
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - {@link #fillAnthology};
     */
    private void loadPage() {

        final BookData bookData = mEditManager.getBookData();
        mBookAuthor = bookData.getString(UniqueId.KEY_AUTHOR_FORMATTED);
        mBookTitle = bookData.getString(UniqueId.KEY_TITLE);
        mIsbn = bookData.getString(UniqueId.KEY_ISBN);
        mBookId = bookData.getRowId();

        // Setup the same author field
        mSame = getView().findViewById(R.id.same_author);
        mSame.setChecked(((bookData.getInt(UniqueId.KEY_ANTHOLOGY_MASK) & TableInfo.ColumnInfo.ANTHOLOGY_MULTIPLE_AUTHORS) == TableInfo.ColumnInfo.ANTHOLOGY_NO));
        mSame.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState(mEditManager.getBookData());
                loadPage();
            }
        });

        // AutoCompleteTextView
        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mDb.getAuthors());
        mAuthorText = getView().findViewById(R.id.add_author);
        mAuthorText.setAdapter(author_adapter);
        mAuthorText.setVisibility(mSame.isChecked() ? View.GONE : View.VISIBLE);

        mTitleText = getView().findViewById(R.id.add_title);
        mYearText = getView().findViewById(R.id.add_year);

        mAdd = getView().findViewById(R.id.add_button);
        mAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String year = mYearText.getText().toString();
                String title = mTitleText.getText().toString();
                String author = mAuthorText.getText().toString();
                if (mSame.isChecked()) {
                    author = mBookAuthor;
                }
                AnthologyTitleListAdapterForEditing adapter = ((AnthologyTitleListAdapterForEditing) EditBookAnthologyFragment.this.getListView().getAdapter());

                if (mEditPosition == null) {
                    AnthologyTitle anthology = new AnthologyTitle(new Author(author), title, year, bookData.getRowId());
                    adapter.add(anthology);
                } else {
                    AnthologyTitle anthology = adapter.getItem(mEditPosition);
                    anthology.setAuthor(new Author(author));
                    anthology.setTitle(title);
                    mEditPosition = null;
                    mAdd.setText(R.string.anthology_add);
                }

                mYearText.setText("");
                mTitleText.setText("");
                mAuthorText.setText("");
                //fillAnthology(currentPosition); don't fill here ? or do ?
                mEditManager.setDirty(true);
            }
        });

        fillAnthology();
    }

    /**
     * Populate the view
     */
    private void fillAnthology() {

        // Get all of the rows from the database and create the item list
        mList = mEditManager.getBookData().getAnthologyTitles();

        // Now create a simple cursor adapter and set it to display
        AnthologyTitleListAdapterForEditing adapter = new AnthologyTitleListAdapterForEditing(getActivity(), R.layout.row_edit_anthology, mList);
        getListView().setAdapter(adapter);

        registerForContextMenu(getListView());
        // click on a list entry, puts it in edit fields
        getListView().setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mEditPosition = position;
                AnthologyTitle anthology = mList.get(position);
                mYearText.setText(anthology.getPublicationDate());
                mTitleText.setText(anthology.getTitle());
                mAuthorText.setText(anthology.getAuthor().getDisplayName());
                mAdd.setText(R.string.anthology_save);
            }
        });
    }

    private ListView getListView() {
        return (ListView) getView().findViewById(android.R.id.list);
    }

    /**
     * First step, get all editions for the ISBN (or maybe just the one)
     */
    private void searchISFDB() {
        // Setup the background fetcher
        if (mAntFetcher == null) {
            mAntFetcher = new SimpleTaskQueue("isfdb-editions");
        }
        Toast.makeText(EditBookAnthologyFragment.this.getContext(), R.string.connecting_to_web_site, Toast.LENGTH_LONG).show();
        mAntFetcher.enqueue(new ISFDBEditionsTask(mIsbn));
    }

    /**
     * We now have all editions, see which one to use
     */
    private void handleISFDBBook(@NonNull final String bookUrl) {
        // Setup the background fetcher
        if (mAntFetcher == null) {
            mAntFetcher = new SimpleTaskQueue("isfdb-book");
        }
        mAntFetcher.enqueue(new ISFDBBookTask(bookUrl));
    }

    private String cleanUpName(@NonNull final String s) {
        return s.trim()
                .replace("\n", " ")
                .replaceAll(CLEANUP_REGEX, "")
                .trim();

    }

    private void showAnthologyConfirm(@NonNull final List<AnthologyTitle> results) {
        StringBuilder msg = new StringBuilder();
        if (results.isEmpty()) {
            msg.append(getString(R.string.automatic_population_failed));
        } else {
            //FIXME: this is usually to much to display as a Message in the dialog
            for (AnthologyTitle t : results) {
                msg.append(t.getTitle()).append(", ");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(getActivity()).setMessage(msg)
                .setTitle(R.string.anthology_confirm)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        if (!results.isEmpty()) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                    this.getResources().getString(android.R.string.ok),
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
                            AnthologyTitleListAdapterForEditing adapter = ((AnthologyTitleListAdapterForEditing) EditBookAnthologyFragment.this.getListView().getAdapter());
                            adapter.notifyDataSetChanged();
                        }
                    });
        }

        // if we found multiple editions, allow a re-try with the next inline
        if (mISFDBUrls.size() > 1) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                    this.getResources().getString(R.string.try_another),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mISFDBUrls.remove(0);
                            handleISFDBBook(mISFDBUrls.get(0));
                        }
                    });
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                this.getResources().getString(android.R.string.cancel),
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
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, POPULATE_ISFDB, 0, R.string.populate_anthology_titles)
                .setIcon(R.drawable.ic_autorenew);
        super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case POPULATE_ISFDB:
                searchISFDB();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.MENU_DELETE_ANTHOLOGY, 0, R.string.menu_delete_anthology);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE_ANTHOLOGY:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                AnthologyTitleListAdapterForEditing adapter = ((AnthologyTitleListAdapterForEditing) EditBookAnthologyFragment.this.getListView().getAdapter());
                adapter.remove(adapter.getItem((int) info.id));
                mEditManager.setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(@NonNull final BookData bookData) {
        bookData.setAnthologyTitles(mList);
        bookData.putInt(UniqueId.KEY_ANTHOLOGY_MASK,
                mSame.isChecked() ?
                        TableInfo.ColumnInfo.ANTHOLOGY_IS_ANTHOLOGY
                        : TableInfo.ColumnInfo.ANTHOLOGY_MULTIPLE_AUTHORS ^ TableInfo.ColumnInfo.ANTHOLOGY_IS_ANTHOLOGY);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAntFetcher != null) {
            mAntFetcher.finish();
            mAntFetcher = null;
        }
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

    private class ISFDBEditionsTask implements SimpleTaskQueue.SimpleTask {

        private static final String EDITIONS_URL = "http://www.isfdb.org/cgi-bin/se.cgi?arg=%s&type=ISBN";
        private final List<String> editions = new ArrayList<>();
        private String isbn;

        ISFDBEditionsTask(@NonNull final String isbn) {
            if (isbn.isEmpty()) {
                throw new RuntimeException("Can not get editions without an ISBN");
            }
            this.isbn = isbn;
        }

        @Override
        public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
            String path = String.format(EDITIONS_URL, isbn);
            try {
                Document doc = Jsoup.connect(path)
                        .userAgent("Mozilla")
                        .followRedirects(true)
                        .get();
                getEntries(doc, "tr.table0");
                getEntries(doc, "tr.table1");
                // if no editions, we were redirected to the book itself
                if (editions.size() == 0) {
                    editions.add(doc.location());
                }
            } catch (IOException e) {
                Logger.logError(e, path);
            }
        }

        private void getEntries(@NonNull final Document doc, @NonNull final String selector) {
            Elements entries = doc.select(selector);
            for (Element entry : entries) {
                Element edLink = entry.select("a").first(); // first column has the book link
                if (edLink != null) {
                    String url = edLink.attr("href");
                    if (url != null) {
                        editions.add(url);
                    }
                }
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            mISFDBUrls = editions;
            if (editions.size() > 0) {
                handleISFDBBook(editions.get(0));
            }
        }
    }

    private class ISFDBBookTask implements SimpleTaskQueue.SimpleTask {
        private final String bookUrl;
        private final List<AnthologyTitle> results = new ArrayList<>();

        ISFDBBookTask(@NonNull final String bookUrl) {
            this.bookUrl = bookUrl;
        }

        @Override
        public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
            try {
                Connection isfdb = Jsoup
                        .connect(bookUrl)
                        .userAgent("Mozilla")
                        .followRedirects(true);
                Document doc = isfdb.get();

                // <div class="ContentBox"> but there are two, so get last one
                Element contentbox = doc.select("div.contentbox").last();
                Elements lis = contentbox.select("li");
                for (Element li : lis) {

                    /* LI entries, 4 possibilities:

                    7 &#8226;
                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799" dir="ltr">Introduction (The Days of Perky Pat)</a>
                    &#8226; [
                    <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a>
                    &#8226; 4] &#8226; (1987) &#8226; essay by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>


                    11 &#8226;
                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a>
                    &#8226; (1955) &#8226; novelette by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613" dir="ltr">Beyond Lies the Wub</a>
                    &#8226; (1952) &#8226; short story by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803" dir="ltr">Introduction (Beyond Lies the Wub)</a>
                    &#8226; [
                    <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a>
                    &#8226; 1] &#8226; (1987) &#8226; essay by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69" dir="ltr">Roger Zelazny</a>

                    So the year is always previous from last, but there is a non-visible 'text' node at the end, hence 'len-3'
                     */
                    int len = li.childNodeSize();
                    Node y = li.childNode(len - 3);
                    Matcher matcher = YEAR_FROM_ISFDB_STORY_LI.matcher(y.toString());
                    String year = matcher.find() ? matcher.group(1) : "";
                    /*
                        See above for LI examples. The title is the first a element, the author is the last a element
                     */
                    Elements a = li.select("a");
                    String title = cleanUpName(a.get(0).text());
                    String author = cleanUpName(a.get(a.size() - 1).text());
                    results.add(new AnthologyTitle(new Author(author), title, year, mBookId));
                }
            } catch (IOException e) {
                Logger.logError(e, bookUrl);
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            showAnthologyConfirm(results);
        }
    }

    protected class AnthologyTitleListAdapterForEditing extends AnthologyTitleListAdapter {

        AnthologyTitleListAdapterForEditing(@NonNull final Context context,
                                            final int rowViewId,
                                            @NonNull final ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onRowClick(@NonNull final View v, @NonNull final AnthologyTitle item, final int position) {
            mYearText.setText(item.getPublicationDate());
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
