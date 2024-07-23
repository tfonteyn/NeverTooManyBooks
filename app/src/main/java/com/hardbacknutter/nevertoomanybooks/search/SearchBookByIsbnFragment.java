/*
 * @Copyright 2018-2024 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;

import org.acra.ACRA;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 * <p>
 * ENHANCE: embedded scanner is UNDER DEVELOPMENT.
 * - need to double check the stop-logic: when is scanner.stop() needed?
 * -> see the DecoderResultListener#onResult in the library.
 * <p>
 * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
 * Each time we format the code, methods and variables jump around.
 * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "BookSearchByIsbnFrag";
    private static final String BKEY_SCANNER_ACTIVITY_STARTED = TAG + ":started";
    private static final char KEY_CLEAR_QUEUE = 'q';
    private static final char KEY_SEARCH = 's';
    private static final char KEY_DELETE = 'd';
    private static final char KEY_STOP_SCANNING = 'o';

    /** The user wants to import a list of ISBNs to the queue. */
    private final ActivityResultLauncher<String> openUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(),
                                      o -> o.ifPresent(this::onOpenUri));

    /** Scan barcodes using the scanner Activity. */
    private final ActivityResultLauncher<ScanOptions> scannerActivityLauncher =
            registerForActivityResult(new ScannerContract(), o -> {
                scannerActivityStarted = false;
                if (o.isPresent()) {
                    onBarcodeScanned(o.get());
                } else {
                    // something was wrong ; quit scanning
                    switchOffScanner();
                }
            });

    /** flag indicating the scanner Activity is already started. */
    private boolean scannerActivityStarted;

    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;
    private SearchBookByIsbnViewModel vm;

    @Nullable
    private BarcodeScanner scanner;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), coordinator.isStrictIsbn(), getArguments());
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
        // Effectively disable edge-to-edge for the root view.
        InsetsListenerBuilder.create()
                             .padding()
                             .sides(Side.Left, Side.Right, Side.Bottom)
                             .applyTo(view);

        if (savedInstanceState != null) {
            scannerActivityStarted = savedInstanceState
                    .getBoolean(BKEY_SCANNER_ACTIVITY_STARTED, false);
        }

        vm.onScanQueueUpdate().observe(getViewLifecycleOwner(), this::onQueueUpdated);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_isbn);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        vb.isbn.setText(coordinator.getIsbnSearchText());
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

        vb.keypad.btnSearch.setOnClickListener(v -> onKeyPad(v, KEY_SEARCH));
        vb.btnClearQueue.setOnClickListener(v -> onKeyPad(v, KEY_CLEAR_QUEUE));
        vb.btnStopScanning.setOnClickListener(v -> onKeyPad(v, KEY_STOP_SCANNING));
        vb.isbnDel.setOnClickListener(v -> onKeyPad(v, KEY_DELETE));

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

        if (savedInstanceState == null) {
            //noinspection DataFlowIssue
            EngineId.promptToRegister(getContext(), coordinator.getSiteList(),
                                      "searchByIsbn", this::afterOnViewCreated);
        } else {
            afterOnViewCreated();
        }
    }

    private void afterOnViewCreated() {
        if (vm.isAutoStart()) {
            startScanner();
        }
    }

    /**
     * Reroute a key press on the virtual keypad.
     *
     * @param view the key View
     * @param key  the key as a char
     */
    private void onKeyPad(@NonNull final View view,
                          final char key) {
        switch (key) {
            case KEY_SEARCH: {
                // The search button
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                //noinspection DataFlowIssue
                onBarcodeEntered(vb.isbn.getText().toString().trim());
                break;
            }
            case KEY_STOP_SCANNING: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT);
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                switchOffScanner();
                break;
            }
            case KEY_DELETE: {
                // The delete button
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                vb.isbn.onKey(KeyEvent.KEYCODE_DEL);
                break;
            }
            case KEY_CLEAR_QUEUE: {
                // The clear-queue button
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                //noinspection DataFlowIssue
                vm.clearQueue(getContext());
                break;
            }
            default:
                // any number pad key 0..9X
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                vb.isbn.onKey(key);
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection DataFlowIssue
        coordinator.setIsbnSearchText(vb.isbn.getText().toString().trim());
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
        if (BuildConfig.EMBEDDED_BARCODE_SCANNER) {
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
        if (BuildConfig.EMBEDDED_BARCODE_SCANNER) {
            if (scanner != null) {
                scanner.stop();
            }
            vb.barcodeScannerGroup.setVisibility(View.GONE);
        }
        vm.setScannerMode(ScanMode.Off);
    }

    /**
     * The user finished editing a book. Store results and continue scanning if applicable.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        vm.onBookEditingDone(data);
        if (vm.getScannerMode() == ScanMode.Continuous) {
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
        final boolean strictIsbn = coordinator.isStrictIsbn();
        final ISBN code = new ISBN(barCode, strictIsbn);

        final Context context = requireContext();

        if (code.isValid(strictIsbn)) {
            if (strictIsbn) {
                SoundManager.beepOnValidIsbn(context);
            } else {
                SoundManager.beepOnBarcodeFound(context);
            }

            if (vm.getScannerMode() == ScanMode.Batch) {
                // batch mode, queue the code, go scan next book
                vm.addToQueue(context, code);
                startScanner();

            } else {
                // Scan mode:
                // Manual: quit scanning after the search/edit.
                // Continuous: leave the scanner on, scanning again when the edit is done.
                if (vm.getScannerMode() == ScanMode.Manual) {
                    switchOffScanner();
                }
                // Put the code in the field; if the search fails, the user can manually edit it
                vb.isbn.setText(code.asText());
                // go search online for the book
                prepareSearch(code);
            }
        } else {
            SoundManager.beepOnInvalidIsbn(context);
            vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code,
                                          code.asText()));

            if (vm.getScannerMode() == ScanMode.Batch) {
                // invalid code but we're in batch mode.
                // Just ignore the bad code and scan the next book.
                startScanner();
            } else {
                // invalid code, always quit scanning and let the user edit the code
                switchOffScanner();
                vb.isbn.setText(code.asText());
            }
        }
    }

    /**
     * The user entered a barcode and clicked the search button.
     *
     * @param barCode as entered by the user.
     */
    private void onBarcodeEntered(@NonNull final String barCode) {
        final boolean strictIsbn = coordinator.isStrictIsbn();
        final ISBN code = new ISBN(barCode, strictIsbn);

        if (code.isValid(strictIsbn)) {
            prepareSearch(code);
        } else {
            vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code, barCode));
        }
    }

    /**
     * Prepare to search with ISBN or, if allowed, with a generic code.
     * If successful, {@link #startSearch()} will be called as the next step.
     *
     * @param code to search for
     */
    private void prepareSearch(@NonNull final ISBN code) {
        coordinator.setIsbnSearchText(code.asText());

        // See if ISBN already exists in our database, if not then start the search.
        final List<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            // Preserve the ISBN should we crash.
            // While a crash is seldom, it can happen if the website(s) returns
            // bad/unexpected data - OpenLibrary being notorious...
            // If a user reports this crash, we'll have the ISBN to try and reproduce.
            ACRA.getErrorReporter().putCustomData(DBKey.BOOK_ISBN, code.asText());
            startSearch();

        } else {
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
                    .setNegativeButton(android.R.string.cancel, (d, w) -> onClearSearchCriteria())
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w)
                            -> editBookLauncher.launch(new EditBookContract
                            .Input(firstFound, vm.getStyle())))
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> startSearch())
                    .create()
                    .show();
        }
    }

    @Override
    void onSearchResults(@NonNull final Book book) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = book.getString(DBKey.TITLE, null);
        if ((title == null || title.isEmpty()) && book.size() <= 2) {
            vb.lblIsbn.setError(getString(R.string.warning_no_matching_book_found));
            return;
        }

        editBookLauncher.launch(new EditBookContract.Input(book, vm.getStyle()));
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        //mVb.isbn.setText("");
    }

    /**
     * Import a list of ISBNs from the given {@link Uri}.
     *
     * @param uri as chosen by the user
     */
    private void onOpenUri(@NonNull final Uri uri) {
        //noinspection DataFlowIssue
        if (!vm.readQueue(getContext(), uri, coordinator.isStrictIsbn())) {
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
                prepareSearch(clickedCode);
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
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(coordinator.isStrictIsbn());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_BARCODE_SCAN) {
                vm.setScannerMode(ScanMode.getSingleScanMode(requireContext()));
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                vm.setScannerMode(ScanMode.Batch);
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_IMPORT) {
                // Using "*/*": see remarks in
                // {@link com.hardbacknutter.nevertoomanybooks.backup.ImportFragment}
                TipManager.getInstance().display(requireContext(), R.string.tip_import_isbn_list,
                                                 () -> openUriLauncher.launch(ANY_URI));
                return true;

            } else if (menuItemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
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
