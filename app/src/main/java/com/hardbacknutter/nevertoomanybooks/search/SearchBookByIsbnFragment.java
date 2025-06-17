/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.Result;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "BookSearchByIsbnFrag";
    private static final String BKEY_SCANNER_ACTIVITY_STARTED = TAG + ":started";

    /**
     * Flag indicating the scanner Activity is already started so we don't
     * start it a second time after a device rotation.
     */
    private boolean scannerActivityStarted;
    @Nullable
    private BarcodeScanner scanner;
    private boolean embeddedBarcodeScanner;

    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;

    private SearchBookByIsbnViewModel vm;

    /** The user wants to import a list of ISBNs to the queue. */
    private ActivityResultLauncher<String> openUriLauncher;

    /** Scan barcodes using the scanner Activity. */
    private ActivityResultLauncher<ScanOptions> scannerActivityLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createActivityLaunchers();

        vm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        vm.init(getArguments());
    }

    private void createActivityLaunchers() {
        openUriLauncher = registerForActivityResult(new GetContentUriForReadingContract(),
                                                    o -> o.ifPresent(this::onOpenUri));

        scannerActivityLauncher = registerForActivityResult(new ScannerContract(), o -> {
            scannerActivityStarted = false;
            if (o.isPresent()) {
                onBarcodeScanned(o.get());
            } else {
                // the user hit 'back' (they are done) or there was something was wrong
                switchOffScanner();
            }
        });
    }

    /**
     * Check screen size and orientation to decide whether we use the embedded
     * scanner view, or the separate {@link ScannerContract}.
     */
    private void decideToUseEmbeddedScanner() {
        if (BuildConfig.EMBEDDED_BARCODE_SCANNER) {
            //noinspection DataFlowIssue
            final ScreenSize screenSize = ScreenSize.compute(getActivity());
            if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
                embeddedBarcodeScanner = screenSize.getHeight() == ScreenSize.Value.Expanded;
            } else {
                embeddedBarcodeScanner = screenSize.getWidth() == ScreenSize.Value.Expanded;
            }
        }
    }

    // Not sure this is really needed, but it does no harm.
    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        decideToUseEmbeddedScanner();
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
        InsetsListenerBuilder.fragmentRootView(view);

        decideToUseEmbeddedScanner();

        if (savedInstanceState != null) {
            scannerActivityStarted = savedInstanceState
                    .getBoolean(BKEY_SCANNER_ACTIVITY_STARTED, false);
        }

        vm.onScanQueueUpdate().observe(getViewLifecycleOwner(), this::onQueueUpdated);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_isbn);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        modelToView();

        autoRemoveError(vb.isbn, vb.lblIsbn);

        vb.keypad.key0.setOnClickListener(v -> onKeyPad(v, '0'));
        vb.keypad.key1.setOnClickListener(v -> onKeyPad(v, '1'));
        vb.keypad.key2.setOnClickListener(v -> onKeyPad(v, '2'));
        vb.keypad.key3.setOnClickListener(v -> onKeyPad(v, '3'));
        vb.keypad.key4.setOnClickListener(v -> onKeyPad(v, '4'));
        vb.keypad.key5.setOnClickListener(v -> onKeyPad(v, '5'));
        vb.keypad.key6.setOnClickListener(v -> onKeyPad(v, '6'));
        vb.keypad.key7.setOnClickListener(v -> onKeyPad(v, '7'));
        vb.keypad.key8.setOnClickListener(v -> onKeyPad(v, '8'));
        vb.keypad.key9.setOnClickListener(v -> onKeyPad(v, '9'));
        vb.keypad.keyX.setOnClickListener(v -> onKeyPad(v, 'X'));

        vb.isbnDel.setOnClickListener(v -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            vb.isbn.onKey(KeyEvent.KEYCODE_DEL);
        });
        vb.isbnDel.setOnLongClickListener(v -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            vb.isbn.setText("");
            return true;
        });

        vb.keypad.btnSearch.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            viewToModel();

            final boolean strictIsbn = BookSearchCriteria.isStrictIsbn(v.getContext());
            final ISBN code = new ISBN(vm.getIsbnText(), strictIsbn);
            if (!code.isValid(strictIsbn)) {
                vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code, code));
                return;
            }

            prepareCriteria(code);
        });

        vb.btnClearQueue.setOnClickListener(v -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            vm.clearQueueAndCancelSearches(v.getContext(), coordinator);
        });

        // embedded scanner only
        vb.btnStopScanning.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            switchOffScanner();
        });

        // The search preference determines the level here; NOT the 'edit book'
        //noinspection DataFlowIssue
        final ISBN.Validity isbnValidityCheck = BookSearchCriteria.isStrictIsbn(getContext())
                                                ? ISBN.Validity.Strict
                                                : ISBN.Validity.None;

        isbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnCleanupTextWatcher);

        isbnValidationTextWatcher = new ISBN.ValidationTextWatcher(vb.lblIsbn, vb.isbn,
                                                                   isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnValidationTextWatcher);

        vb.isbn.requestFocus();

        if (vm.isAutoStart()) {
            startScanner();
        }
    }

    /**
     * Handle any number pad key.
     *
     * @param view the key View
     * @param key  the key as a char; 0..9 and X
     */
    private void onKeyPad(@NonNull final View view,
                          final char key) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        vb.isbn.onKey(key);
    }

    protected void modelToView() {
        vb.isbn.setText(vm.getIsbnText());
    }

    protected void viewToModel() {
        //noinspection DataFlowIssue
        vm.setIsbnText(vb.isbn.getText().toString().strip());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_SCANNER_ACTIVITY_STARTED, scannerActivityStarted);
    }

    @Override
    @NonNull
    Intent createResultIntent() {
        return vm.createResultIntent();
    }

    private void startScanner() {
        setEnableProgressMessages(vm.getScannerMode() != Scanning.Batch);
        if (embeddedBarcodeScanner) {
            startScannerEmbedded();
        } else {
            startScannerActivity();
        }
    }

    /**
     * Start the embedded (in this Fragment) scanner view.
     */
    private void startScannerEmbedded() {
        vb.barcodeScannerGroup.setVisibility(View.VISIBLE);
        if (scanner == null) {
            // Use the default com. hardbacknutter.tinyzxingwrapper.scanner.ScanMode.Single
            //noinspection DataFlowIssue
            scanner = new BarcodeScanner.Builder()
                    .setBarcodeFormats(BarcodeFamily.PRODUCT)
                    .build(getContext());

            if (vb.cameraViewFinder.isShowResultPoints()) {
                scanner.setResultPointListener(vb.cameraViewFinder);
            }

            getLifecycle().addObserver(scanner);
        }

        scanner.start(getViewLifecycleOwner(),
                      vb.cameraPreview,
                      new DecoderResultListener() {
                          @Nullable
                          private String lastCode;

                          // com. hardbacknutter.tinyzxingwrapper.scanner.ScanMode.Single:
                          // the scanner is stopped when we enter this method.
                          @Override
                          public void onResult(@NonNull final Result result) {
                              final String barCode = result.getText();
                              if (!barCode.equals(lastCode)) {
                                  lastCode = barCode;
                                  onBarcodeScanned(barCode);
                              }
                          }

                          @Override
                          public void onError(@NonNull final Throwable e) {
                              // quit scanning, and destroy the scanner
                              switchOffScanner();
                              getLifecycle().removeObserver(scanner);
                              scanner = null;
                          }
                      });
    }

    /**
     * Start the standalone scanner activity.
     */
    private void startScannerActivity() {
        if (!scannerActivityStarted) {
            scannerActivityStarted = true;
            //noinspection DataFlowIssue
            scannerActivityLauncher.launch(ScannerContract.createDefaultOptions(getContext()));
        }
    }

    /**
     * Switch the scanner off.
     * <p>
     * Dev. note: this used to be called "stopScanning" but that was confusing as
     * the standalone scanner would already be stopped.
     */
    private void switchOffScanner() {
        if (embeddedBarcodeScanner) {
            if (scanner != null) {
                scanner.stop();
            }
            vb.barcodeScannerGroup.setVisibility(View.GONE);
        }
        vm.setScannerMode(Scanning.Off);
        setEnableProgressMessages(true);
    }

    /**
     * The user finished editing a book. Store results and continue scanning if applicable.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        onClearSearchCriteria();

        vm.onBookEditingDone(data);
        if (vm.getScannerMode() == Scanning.Continuous) {
            // scan another book until the user cancels
            startScanner();
        }
    }

    /**
     * The scanner returned a barcode.
     *
     * @param barCode as returned by the scanner
     */
    private void onBarcodeScanned(@NonNull final String barCode) {
        //noinspection DataFlowIssue
        final boolean strictIsbn = BookSearchCriteria.isStrictIsbn(getContext());
        final ISBN code = new ISBN(barCode, strictIsbn);

        final Context context = requireContext();

        if (code.isValid(strictIsbn)) {
            if (strictIsbn) {
                SoundManager.beepOnValidIsbn(context);
            } else {
                SoundManager.beepOnBarcodeFound(context);
            }

            if (vm.getScannerMode() == Scanning.Batch) {
                // batch mode, queue the code, go scan next book
                vm.addToQueue(context, code);
                startScanner();

            } else {
                // Scan mode:
                // Manual: quit scanning after the search/edit.
                // Continuous: leave the scanner on, scanning again when the edit is done.
                if (vm.getScannerMode() == Scanning.Manual) {
                    switchOffScanner();
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepareCriteria(code);
                    break;
                }
                case Continuous: {
                    // Continuous: leave the scanner on, scanning restarts when the edit is done.
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepareCriteria(code);
                    break;
                }
            }
        } else {
            SoundManager.beepOnInvalidIsbn(context);

            if (vm.getScannerMode() == Scanning.Batch) {
                // invalid code but we're in batch mode.
                // Just ignore the bad code and scan the next book.
                startScanner();
            } else {
                // invalid code, always quit scanning and let the user edit the code
                switchOffScanner();
                vm.setIsbnText(barCode);
                modelToView();
                vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code,
                                              code.asText()));
            }
        }
    }

    /**
     * Prepare the criteria object to use for the search.
     * This method can interact with the user, and can reject starting a search.
     * <p>
     * Used by either by the user typing in a code, or scanning one
     * in {@link Scanning#Manual}/{@link Scanning#Continuous} mode.
     *
     * @param code to search for
     */
    private void prepareCriteria(@NonNull final ISBN code) {
        // check if we have an active search, if so, quit silently.
        if (coordinator.isSearchActive()) {
            return;
        }

        // See if ISBN already exists in our database, if not then start the search.
        final List<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            startSearch(code);

        } else {
            onBookAlreadyPresent(code, existingIds, () -> startSearch(code));
        }
    }

    private void onBookAlreadyPresent(@NonNull final ISBN code,
                                      @NonNull final List<Pair<Long, String>> existingIds,
                                      @NonNull final Runnable onAdd) {
        // always quit scanning until the user manually starts it again
        switchOffScanner();

        // we always use the first one... really should offer the user a choice.
        final long firstFound = existingIds.get(0).first;
        // Show the "title (isbn)" with a caution message
        final String msg = getString(R.string.a_bracket_b_bracket,
                                     existingIds.get(0).second, code.asText())
                           + "\n\n" + getString(R.string.confirm_duplicate_book_message);

        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.lbl_duplicate_book)
                .setMessage(msg)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                // User aborts this isbn
                .setNegativeButton(R.string.cancel, (d, w) -> onClearSearchCriteria())
                // User wants to review the existing book
                .setNeutralButton(R.string.action_edit, (d, w)
                        -> editBook(firstFound, vm.getStyle()))
                // User wants to add regardless
                .setPositiveButton(R.string.action_add, (d, w) -> onAdd.run())
                .create()
                .show();
    }

    /**
     * Sits between {@link #prepareCriteria(ISBN)} and {@link #startSearch(BookSearchCriteria)}
     * to support {@link Scanning#Batch} mode.
     *
     * @param code to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     */
    private int startSearch(@NonNull final ISBN code) {
        //noinspection DataFlowIssue
        final BookSearchCriteria criteria = new BookSearchCriteria(getContext());
        criteria.setIsbn(code);

        return startSearch(criteria);
    }

    @Override
    void onSearchCancelled(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();
        //noinspection DataFlowIssue
        Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
    }

    @Override
    void onSearchResults(@NonNull final BookSearchResult bookSearchResult) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchResults", bookSearchResult);
        }
        final Book book = bookSearchResult.getBook();

        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field will be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = book.getString(DBKey.TITLE, null);
        if ((title == null || title.isEmpty()) && book.size() <= 2) {
            vb.lblIsbn.setError(getString(R.string.warning_no_matching_book_found));
            return;
        }

        editBook(book, vm.getStyle());
    }

    @Override
    void onClearSearchCriteria() {
        vm.setIsbnText(null);
        //vb.isbn.setText("");
    }

    /**
     * Import a list of ISBNs from the given {@link Uri}.
     *
     * @param uri as chosen by the user
     */
    private void onOpenUri(@NonNull final Uri uri) {
        //noinspection DataFlowIssue
        if (!vm.readQueue(getContext(), uri)) {
            Snackbar.make(vb.getRoot(), R.string.error_import_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Refresh the queue view(s) and show/hide the 'clear' button.
     *
     * @param list to display; can be empty
     */
    private void onQueueUpdated(@NonNull final List<ISBN> list) {
        if (vb.queue.getChildCount() > 0) {
            vb.queue.removeAllViews();
        }

        list.forEach(code -> {
            final Chip chip = new Chip(getContext(), null, R.attr.appChipInputStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setOnClickListener(v -> {
                final ISBN clickedCode = removeFromQueue(v);
                vb.isbn.setText(clickedCode.asText());
                viewToModel();
                startSearch(clickedCode);
            });
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setTag(code);
            chip.setText(code.asText());
            vb.queue.addView(chip);
        });

        updateQueueViewsVisibility();
    }

    @NonNull
    private ISBN removeFromQueue(@NonNull final View chip) {
        final ISBN code = (ISBN) chip.getTag();
        // remove and update view manually to avoid flicker
        //noinspection DataFlowIssue
        vm.removeFromQueue(getContext(), code);
        vb.queue.removeView(chip);
        updateQueueViewsVisibility();
        return code;
    }

    private void updateQueueViewsVisibility() {
        final int visibility = vb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        // The queue Chips and the 'clear queue' button
        vb.queueGroup.setVisibility(visibility);
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        private static final String ANY_URI = "*/*";

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.search_by_isbn, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            //noinspection DataFlowIssue
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(BookSearchCriteria.isStrictIsbn(getContext()));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_BARCODE_SCAN) {
                vm.setScannerMode(Scanning.getScannerModeSingle(requireContext()));
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                // don't clear
                vm.setScannerMode(Scanning.Batch);
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_IMPORT) {
                // Using "*/*": see remarks in
                // {@link com.hardbacknutter.nevertoomanybooks.backup.ImportFragment}
                TipManager.getInstance().show(requireContext(), Tip.IMPORT_ISBN_LIST,
                                              () -> openUriLauncher.launch(ANY_URI));
                return true;

            } else if (menuItemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                BookSearchCriteria.setStrictIsbnDefault(requireContext(), checked);

                final ISBN.Validity validity = checked ? ISBN.Validity.Strict : ISBN.Validity.None;
                isbnCleanupTextWatcher.setValidityLevel(validity);
                isbnValidationTextWatcher.setValidityLevel(validity);
                return true;
            }

            return false;
        }
    }
}
