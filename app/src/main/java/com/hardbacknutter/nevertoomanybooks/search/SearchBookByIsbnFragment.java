/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.search;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "BookSearchByIsbnFrag";
    private static final String BKEY_STARTED = TAG + ":started";
    /** See remarks in {@link com.hardbacknutter.nevertoomanybooks.backup.ImportFragment}. */
    private static final String MIME_TYPES = "*/*";

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider searchSitesToolbarMenuProvider;
    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider toolbarMenuProvider;

    /** flag indicating the scanner is already started. */
    private boolean scannerStarted;

    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;
    private SearchBookByIsbnViewModel vm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        vm.init(getArguments());

        if (savedInstanceState != null) {
            scannerStarted = savedInstanceState.getBoolean(BKEY_STARTED, false);
        }
    }

    private void onBarcodeScanned(@NonNull String barCode) {
        scannerStarted = false;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.FAKE_BARCODE_SCANNER) {
            final String[] testCodes = {
                    // random == 0 -> cancel scanning
                    null,
                    "9780316310420",
                    "9781250762849",
                    "9781524763169",
                    "9780062868930",
                    "9781786892713",
                    "9780349701462",
                    "9781635574043",
                    "9781538719985",
                    "9780593230251",
                    "9780063032491",
                    };
            final int random = (int) Math.floor(Math.random() * 10);
            barCode = testCodes[random];
            Log.d(TAG, "onBarcodeScanned|Faking barcode=" + barCode);
        }

        if (barCode != null) {
            final boolean strictIsbn = coordinator.isStrictIsbn();
            final ISBN code = new ISBN(barCode, strictIsbn);

            if (code.isValid(strictIsbn)) {
                if (strictIsbn) {
                    //noinspection ConstantConditions
                    ScannerContract.onValidBarcodeBeep(getContext());
                }

                if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Batch) {
                    // batch mode, queue the code, and scan next book
                    vm.addToQueue(code);
                    scan();

                } else {
                    // single-scan mode, keep the scanner on and go edit the book
                    vb.isbn.setText(code.asText());
                    prepareSearch(code);
                }
            } else {
                //noinspection ConstantConditions
                ScannerContract.onInvalidBarcodeBeep(getContext());
                showError(vb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code,
                                                code.asText()));

                if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Batch) {
                    // batch mode, scan next book
                    scan();
                } else {
                    // single-scan mode, quit scanning, let the user edit the code
                    vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);
                    vb.isbn.setText(code.asText());
                }
            }

        } else if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Batch) {
            // no barcode received, batch mode, quit scanning and present the queue to the user
            vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);
            populateQueueView();

        } else {
            // no barcode received, single-scan mode, quit scanning
            vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);
        }
    }

    /** After a successful scan/search, the data is offered for editing. */
    private final ActivityResultLauncher<Long> editExistingBookLauncher =
            registerForActivityResult(new EditBookByIdContract(),
                                      o -> o.ifPresent(this::onBookEditingDone));

    @Override
    @NonNull
    protected Bundle getResultData() {
        return vm.getResultData();
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentBooksearchByIsbnBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        searchSitesToolbarMenuProvider = new SearchSitesToolbarMenuProvider();
        toolbar.addMenuProvider(searchSitesToolbarMenuProvider, getViewLifecycleOwner());
        toolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(toolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_search_isbn);

        vb.isbn.setText(coordinator.getIsbnSearchText());

        vb.key0.setOnClickListener(v -> vb.isbn.onKey('0'));
        vb.key1.setOnClickListener(v -> vb.isbn.onKey('1'));
        vb.key2.setOnClickListener(v -> vb.isbn.onKey('2'));
        vb.key3.setOnClickListener(v -> vb.isbn.onKey('3'));
        vb.key4.setOnClickListener(v -> vb.isbn.onKey('4'));
        vb.key5.setOnClickListener(v -> vb.isbn.onKey('5'));
        vb.key6.setOnClickListener(v -> vb.isbn.onKey('6'));
        vb.key7.setOnClickListener(v -> vb.isbn.onKey('7'));
        vb.key8.setOnClickListener(v -> vb.isbn.onKey('8'));
        vb.key9.setOnClickListener(v -> vb.isbn.onKey('9'));
        vb.keyX.setOnClickListener(v -> vb.isbn.onKey('X'));

        vb.isbnDel.setOnClickListener(v -> vb.isbn.onKey(KeyEvent.KEYCODE_DEL));
        vb.isbnDel.setOnLongClickListener(v -> {
            vb.isbn.setText("");
            return true;
        });

        // The search preference determines the level here; NOT the 'edit book'
        final ISBN.Validity isbnValidityCheck = coordinator.isStrictIsbn() ? ISBN.Validity.Strict
                                                                           : ISBN.Validity.None;

        isbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnCleanupTextWatcher);

        isbnValidationTextWatcher =
                new ISBN.ValidationTextWatcher(vb.lblIsbn, vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnValidationTextWatcher);

        vb.btnSearch.setOnClickListener(btn -> onBarcodeEntered());

        vb.btnClearQueue.setOnClickListener(v -> {
            vm.getScanQueue().clear();
            vb.queue.removeAllViews();
            vb.queueGroup.setVisibility(View.GONE);
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            Site.promptToRegister(getContext(), coordinator.getSiteList(),
                                  "searchByIsbn", this::afterOnViewCreated);
        } else {
            afterOnViewCreated();
        }
    }

    /** Importing a list of ISBN. */
    private final ActivityResultLauncher<String> openUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);

    private void afterOnViewCreated() {
        if (vm.isAutoStart()) {
            scan();
        } else {
            populateQueueView();
        }
    }

    @Override
    void onSearchCancelled(@NonNull final LiveDataEvent<TaskResult<Bundle>> message) {
        super.onSearchCancelled(message);
        // Quit scan mode until the user manually starts it again
        vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);
    }

    /** The scanner. */
    private final ActivityResultLauncher<Fragment> scannerLauncher =
            registerForActivityResult(new ScannerContract(),
                                      o -> o.ifPresent(this::onBarcodeScanned));

    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        super.onBookEditingDone(data);
        if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Single) {
            // scan another book until the user cancels
            scan();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        coordinator.setIsbnSearchText(vb.isbn.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_STARTED, scannerStarted);
    }

    /**
     * Start scanner activity.
     */
    public void scan() {
        if (!scannerStarted) {
            scannerStarted = true;
            scannerLauncher.launch(this);
        }
    }

    /**
     * Search with ISBN or, if allowed, with a generic code.
     *
     * @param code to search for
     */
    private void prepareSearch(@NonNull final ISBN code) {
        coordinator.setIsbnSearchText(code.asText());

        // See if ISBN already exists in our database, if not then start the search.
        final ArrayList<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            startSearch();

        } else {
            // always quit scanning as the safe option, the user can restart the scanner,
            // or restart the queue processing at will.
            vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);

            // we always use the first one... really should offer the user a choice.
            final long firstFound = existingIds.get(0).first;
            // Show the "title (isbn)" with a caution message
            final String msg = getString(R.string.a_bracket_b_bracket,
                                         existingIds.get(0).second, code.asText())
                               + "\n\n" + getString(R.string.confirm_duplicate_book_message);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(msg)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (d, w) -> onClearSearchCriteria())
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w)
                            -> editExistingBookLauncher.launch(firstFound))
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> startSearch())
                    .create()
                    .show();
        }
    }

    /**
     * The user entered a barcode and clicked the search button.
     */
    private void onBarcodeEntered() {
        //noinspection ConstantConditions
        final String userEntry = vb.isbn.getText().toString().trim();
        final boolean strictIsbn = coordinator.isStrictIsbn();
        final ISBN code = new ISBN(userEntry, strictIsbn);

        if (code.isValid(strictIsbn)) {
            prepareSearch(code);
        } else {
            showError(vb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code, userEntry));
        }
    }


    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBKey.TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            showError(vb.lblIsbn, R.string.warning_no_matching_book_found);
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }


    private void onOpenUri(@Nullable final Uri uri) {
        if (uri != null) {
            try {
                //noinspection ConstantConditions
                vm.readQueue(getContext(), uri, coordinator.isStrictIsbn());
            } catch (@NonNull final IOException ignore) {
                Snackbar.make(vb.getRoot(), R.string.error_import_failed,
                              Snackbar.LENGTH_LONG).show();
            }

            populateQueueView();
        }
    }

    private void populateQueueView() {
        if (vb.queue.getChildCount() > 0) {
            vb.queue.removeAllViews();
        }

        vm.getScanQueue().forEach(code -> {
            final Chip chip = new Chip(getContext(), null, R.attr.appChipInputStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setOnClickListener(v -> {
                final ISBN clickedCode = removeFromQueue(v);
                vb.isbn.setText(clickedCode.asText());
                prepareSearch(clickedCode);
            });
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setTag(code);
            chip.setText(code.asText());
            vb.queue.addView(chip);
        });

        vb.queueGroup.setVisibility(vb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private ISBN removeFromQueue(@NonNull final View chip) {
        final ISBN code = (ISBN) chip.getTag();
        vb.queue.removeView(chip);
        vb.queueGroup.setVisibility(vb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        vm.getScanQueue().remove(code);
        return code;
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        //mVb.isbn.setText("");
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.search_by_isbn, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(coordinator.isStrictIsbn());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_BARCODE_SCAN) {
                vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Single);
                scan();
                return true;

            } else if (itemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Batch);
                scan();
                return true;

            } else if (itemId == R.id.MENU_BARCODE_IMPORT) {
                openUriLauncher.launch(MIME_TYPES);
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                coordinator.setStrictIsbn(checked);

                final ISBN.Validity validity = checked ? ISBN.Validity.Strict : ISBN.Validity.None;
                isbnCleanupTextWatcher.setValidityLevel(validity);
                isbnValidationTextWatcher.setValidityLevel(validity);
                return true;
            }

            return false;
        }
    }

}
