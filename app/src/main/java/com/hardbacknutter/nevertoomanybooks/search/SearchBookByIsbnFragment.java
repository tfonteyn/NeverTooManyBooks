/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.util.Pair;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.Result;

import java.util.List;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.CodeValidity;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.search.queue.QueueViewModel;
import com.hardbacknutter.nevertoomanybooks.search.queue.QueuedItem;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * Use-cases / logic flow....   might contain errors...
 * <p>
 * - start batch scan
 * - disable progress
 * - queue items populated by scanner
 * - queue can be cleared using button
 * - scanner delivers item to be added:
 * - search started, added to the queue (wil have searchId)
 * - starting search fails, ask user to:
 * - stop scanning
 * - restart scanner
 * <p>
 * - user clicks on queue item:
 * - item has searchId (always)
 * - dialog title/msg depending on what we can show
 * - result present:
 * - user can delete item
 * - user can edit book
 * - must be deleted from the queue before edit starts
 * - no result
 * - user can delete item
 * <p>
 * - user stops scanning
 * - progress STILL disabled
 * <p>
 * - queue keeps running
 * - user does a manual entry/scan
 * - prepareCriteria: we could check book existence before starting a search?
 * - to start the search, it MUST be added to the queue!
 * <p>
 * - user presses 'back'
 * - there can be items with results
 * - or items still being searched
 * - abandon/delete
 * - abandon, but keep in prefs
 * <p>
 * 2. enter screen, prefs contains a list of codes
 * or user imports from file
 * Offer to:
 * - delete list
 * - start search
 * - populate queue in one go + start all searches
 * - state: neutral, same as user did a batch scan, then stopped scanning
 * <p>
 * ============================================================
 * <p>
 * Manual entry:
 * <ol>
 *     <li>user enters code and clicks the search-button</li>
 *     <li>{@link #prepare(ISBN)}</li>
 *     <li>{@link #preSearchInteractively(ISBN)}</li>
 *     <li>{@link #startSearch(ISBN)}</li>
 *     <li>{@link SearchBookBaseFragment}#startSearch(Criteria)</li>
 *     <li>{@link SearchCoordinator}#startSearch(Criteria)</li>
 * </ol>
 * <ol>
 *     <li>{@link SearchBookBaseFragment}#onSearchFinished(LiveDataEvent)</li>
 *     <li>{@link #onSearchFinished(BookSearchResult)}</li>
 *     <li>{@link SearchBookBaseFragment#onSearchFinished(BookSearchResult)}</li>
 *     <li>{@link #onSearchResults(BookSearchResult)}</li>
 *     <li>{@link #editBook(long, Style)}</li>
 *     <li>{@link #onBookEditingDone(EditBookOutput)}</li>
 *     <li>done.</li>
 * </ol>
 * <p>
 * ============================================================
 * <p>
 * Single scan:
 * <ol>
 *     <li>User scans a single code</li>
 *     <li>{@link #onBarcodeScanned(String)}</li>
 *     <li>{@link #prepare(ISBN)}</li>
 *     <li>... see Manual Entry</li>
 * </ol>
 * <ol>
 *     <li>... see Manual Entry</li>
 * </ol>
 * <p>
 * ============================================================
 * <p>
 * Continuous scan:
 * <ol>
 *     <li>User scans a code</li>
 *     <li>{@link #onBarcodeScanned(String)}</li>
 *     <li>{@link #prepare(ISBN)}</li>
 *     <li>... see Manual Entry</li>
 * </ol>
 * <ol>
 *     <li>... see Manual Entry</li>
 *     <li>{@link #onBookEditingDone(EditBookOutput)}</li>
 *     <li>scanner starts again</li>
 * </ol>
 * <p>
 * ============================================================
 * <p>
 * Batch scan:
 * <ol>
 *     <li>User scans a code</li>
 *     <li>{@link #onBarcodeScanned(String)}</li>
 *     <li>{@link #preSearchBatch(ISBN)}</li>
 *     <li>{@link QueueViewModel#add(QueuedItem, Function)}</li>
 *     <li>scanner starts again</li>
 * </ol>
 * <ol>
 *     <li>{@link SearchBookBaseFragment}#onSearchFinished(LiveDataEvent)</li>
 *     <li>{@link #onSearchFinished(BookSearchResult)}</li>
 *     <li>{@link QueueViewModel#onResult(BookSearchResult)} </li>
 *     <li>all this in the background as the scanner is still running</li>
 * </ol>
 * <p>
 * ============================================================
 * <p>
 * After a batch scan was stopped, we have a queue.
 * <p>
 * Manual Entry
 * <ol>
 *     <li>{@link QueueViewModel#onResult(BookSearchResult)} </li>
 *     <li>done.</li>
 * </ol>
 * Single scan
 * <ol>
 *     <li>{@link QueueViewModel#onResult(BookSearchResult)} </li>
 *     <li>done.</li>
 * </ol>
 * Continuous scan
 * <ol>
 *     <li>{@link QueueViewModel#onResult(BookSearchResult)} </li>
 *     <li>scanner starts again</li>
 * </ol>
 * Batch scan
 * <ol>
 *     <li>{@link QueueViewModel#onResult(BookSearchResult)} </li>
 *     <li>scanner starts again</li>
 * </ol>
 */
public class SearchBookByIsbnFragment
        extends QueueFragment<ISBN> {

    /** Log tag. */
    private static final String TAG = "SearchBookByIsbnFrag";

    /**
     * The minimum height (portrait) or width (landscape) in dp the screen
     * must have to use the embedded scanner.
     * <p>
     * ScreenSize.Value.Expanded is 840 ... but that's too large;
     * Most 6-inch Android phones have a height of ~800 dp.
     * => use embedded
     * Most 5-inch Android phones have a height of ~640 dp.
     * => use full-screen
     */
    private static final int MINIMUM_SCREEN_SIZE_EMBEDDED_SCANNER = 790;

    /** The embedded scanner. */
    @Nullable
    private BarcodeScanner scanner;
    private boolean useEmbeddedScanner;
    /** Embedded scanner usage: Does the device have a torchlight. */
    private boolean hasTorch;
    /** Embedded scanner usage: Does the camera have a zoom. */
    private boolean hasZoom;

    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;

    /** Handles ISBN and fragment data. */
    private SearchBookByIsbnViewModel vm;

    private PermissionRequester permissionRequester;

    /** Scan barcodes using the scanner Activity. */
    private ActivityResultLauncher<ScanOptions> scannerActivityLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createActivityLaunchers();

        vm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());

        initQueue(IsbnQueueViewModel.class, vm.getStyle());
    }

    private void createActivityLaunchers() {
        //noinspection DataFlowIssue
        permissionRequester = new PermissionRequester(getActivity(), this);
        permissionRequester.addPermission(
                Manifest.permission.CAMERA, true,
                getString(R.string.warning_camera_permission_required),
                getString(R.string.warning_camera_permission_denied)
        );

        scannerActivityLauncher = registerForActivityResult(new ScannerContract(), o -> {
            if (o.isPresent()) {
                onBarcodeScanned(o.get());
            } else {
                // the user hit 'back' (they are done) or there was something wrong
                onScanningFinished(true);
            }
        });
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentBooksearchByIsbnBinding.inflate(inflater, container, false);
        if (BuildConfig.DEBUG /* always */) {
            dbgAddScanButtons();
        }
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.fragmentRootView(view);

        useEmbeddedScanner = maybeInitEmbeddedScanner();
        if (useEmbeddedScanner) {
            initEmbeddedScannerViews();
        } else {
            // Hide all related views
            updateEmbeddedScannerViewsVisibility(false);
        }

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_isbn);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        modelToView();

        initInputField();
        initKeypad(view);

        loadStoredQueue(R.string.lbl_search_isbn, R.plurals.confirm_queue_has_x_items,
                        this::afterOnViewCreated);
    }

    /**
     * Called if there is no Queue or if the user discarded a stored queue.
     *
     * @see #onViewCreated(View, Bundle)
     */
    private void afterOnViewCreated() {
        vb.isbn.requestFocus();
        if (vm.isStartScanner()) {
            startScanner();
        }
    }

    /**
     * Check screen size and orientation to decide whether we use the embedded
     * scanner view, or the separate {@link ScannerContract}.
     *
     * @return flag
     */
    private boolean maybeInitEmbeddedScanner() {
        //noinspection DataFlowIssue
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(getActivity());
        final float density = metrics.getDensity();
        final Rect bounds = metrics.getBounds();
        final float widthDp = bounds.width() / density;
        final float heightDp = bounds.height() / density;

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "widthDp=" + widthDp + "|heightDp=" + heightDp);
        }
        final Resources resources = getResources();
        if (resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return heightDp > MINIMUM_SCREEN_SIZE_EMBEDDED_SCANNER;
        } else {
            return widthDp > MINIMUM_SCREEN_SIZE_EMBEDDED_SCANNER;
        }
    }

    private void initEmbeddedScannerViews() {
        vb.zoomSlider.setValue(vm.getCameraConfig().getZoomValue());
        vb.zoomSlider.addOnChangeListener((slider, zoomValue, fromUser) -> {
            if (fromUser) {
                vm.getCameraConfig().setZoomValue(zoomValue);
                if (scanner != null) {
                    scanner.setLinearZoom(zoomValue);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    slider.performHapticFeedback(
                            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK);
                } else {
                    slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
        });

        updateTorchButtonIcon(vm.getCameraConfig().isTorchEnabled());
        vb.btnTorch.setOnClickListener(v -> {
            final CameraConfig cameraConfig = vm.getCameraConfig();
            // Flip the status
            final boolean enabled = !cameraConfig.isTorchEnabled();
            cameraConfig.setTorchEnabled(enabled);
            updateTorchButtonIcon(enabled);
            if (scanner != null) {
                scanner.setTorch(enabled);
            }
        });

        vb.btnStopScanning.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            onScanningFinished(true);
        });
    }

    /**
     * The input field is not being limited in length. This is to allow entering UPC_A numbers.
     */
    private void initInputField() {
        autoRemoveError(vb.isbn, vb.lblIsbn);

        // The search preference determines the level here; NOT the 'edit book'
        final CodeValidity validity = BookSearchCriteria.isStrictIsbnGlobal()
                                       ? CodeValidity.Isbn
                                       : CodeValidity.NoChecks;

        isbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(vb.isbn, validity);
        vb.isbn.addTextChangedListener(isbnCleanupTextWatcher);

        isbnValidationTextWatcher = new ISBN.ValidationTextWatcher(vb.lblIsbn, vb.isbn,
                                                                   validity);
        vb.isbn.addTextChangedListener(isbnValidationTextWatcher);

        vb.isbnDel.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            vb.isbn.onKey(KeyEvent.KEYCODE_DEL);
        });
        vb.isbnDel.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            vb.isbn.setText("");
            return true;
        });
    }

    @Override
    void inputFieldRequestFocus() {
        vb.isbn.requestFocus();
    }

    @Override
    void inputFieldSetError() {
        vb.lblIsbn.setError(getString(R.string.warning_no_matching_book_found));
    }

    private void initKeypad(@NonNull final View view) {
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
        vb.keypad.btnSearch.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            viewToModel();

            final boolean strictIsbn = BookSearchCriteria.isStrictIsbnGlobal();
            final ISBN code = ISBN.parse(vm.getIsbnText(), strictIsbn);
            if (!code.isValid()) {
                final String text = code.asText();
                if (text.isEmpty()) {
                    vb.lblIsbn.setError(getString(R.string.vldt_non_blank_required));
                } else {
                    vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code, text));
                }
                return;
            }

            prepare(code);
        });
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

    @Override
    protected void modelToView() {
        vb.isbn.setText(vm.getIsbnText());
    }

    @Override
    protected void viewToModel() {
        //noinspection DataFlowIssue
        vm.setIsbnText(vb.isbn.getText().toString().strip());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (vm.onResumeFromSettings()) {
            // We have just returned from the settings screen.
            // If the embedded scanner is in use and started, it needs
            // restarting to force it to pick up any new settings.
            if (useEmbeddedScanner && vm.isScannerStarted()
                // Paranoia, should never be null here
                && scanner != null) {
                scanner.stop();
                vm.setScannerStarted(false);
                startScanner();
            }
        }
    }

    @Override
    @NonNull
    Intent createResultIntent() {
        return vm.createResultIntent();
    }

    /**
     * Switch the scanner on.
     *
     * @see #startScannerActivity()
     */
    private void startScanner() {
        setEnableProgressMessages(!isQueuePopulated()
                                  && vm.getScannerMode() != ScanMode.Batch);

        if (useEmbeddedScanner) {
            // The embedded scanner must handle permissions locally
            permissionRequester.request(Manifest.permission.CAMERA, isGranted -> {
                if (isGranted) {
                    startEmbeddedScanner();
                }
            });
        } else {
            // The scanner Activity will take care of Camera permissions.
            startScannerActivity();
        }
    }

    // @RequiresPermission(Manifest.permission.CAMERA)
    private void startEmbeddedScanner() {
        if (scanner == null) {
            createEmbeddedScanner();
            getLifecycle().addObserver(scanner);
        }

        updateEmbeddedScannerViewsVisibility(true);

        vm.setScannerStarted(true);
        scanner.start(getViewLifecycleOwner(),
                      vb.cameraPreview,
                      new DecoderResultListener() {
                          /** Prevent duplicate results. */
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
                              onScanningFinished(true);
                          }
                      });
    }

    private void updateEmbeddedScannerViewsVisibility(final boolean visible) {
        vb.barcodeScanner.setVisibility(visible ? View.VISIBLE : View.GONE);
        vb.btnStopScanning.setVisibility(visible ? View.VISIBLE : View.GONE);

        vb.btnTorch.setVisibility(visible && hasTorch ? View.VISIBLE : View.GONE);

        vb.zoomSlider.setVisibility(
                visible && hasZoom && vm.getCameraConfig().isZoomControlEnabled()
                ? View.VISIBLE : View.GONE);
    }

    private void updateTorchButtonIcon(final boolean torchEnabled) {
        // We're not using checkable and StateLists as managing the background
        // colour then makes things needlessly complicated.
        // Hence, simply swap the icon manually here.
        vb.btnTorch.setIconResource(torchEnabled
                                    ? com.hardbacknutter.tinyzxingwrapper.R.drawable
                                            .tzw_ic_baseline_flashlight_off_24
                                    : com.hardbacknutter.tinyzxingwrapper.R.drawable
                                            .tzw_ic_baseline_flashlight_on_24);
    }

    /**
     * Build the scanner object, set the runtime parameters and apply user settings.
     */
    private void createEmbeddedScanner() {
        final Context context = getContext();

        final CameraConfig cameraConfig = vm.getCameraConfig();

        final BarcodeScanner.Builder builder = new BarcodeScanner.Builder()
                .setBarcodeFormats(BarcodeFamily.PRODUCT)
                .setAutoFocus(cameraConfig.isAutoFocus());

        // -1: no preference: do NOT set, otherwise set to 0 or 1
        final int lensFacing = cameraConfig.getLensFacing();
        if (lensFacing == CameraSelector.LENS_FACING_FRONT
            || lensFacing == CameraSelector.LENS_FACING_BACK) {
            builder.setCameraLensFacing(lensFacing);
        }

        if (vb.cameraViewFinder.isShowResultPoints()) {
            builder.setResultPointCallback(vb.cameraViewFinder);
        }

        //noinspection DataFlowIssue
        scanner = builder.build(context);

        hasZoom = scanner.hasZoom(context);
        hasTorch = scanner.hasTorch(context);

        scanner.setLinearZoom(cameraConfig.getZoomValue());
        scanner.setTorch(cameraConfig.isTorchEnabled());
    }

    /**
     * Start the standalone scanner activity.
     *
     * @see #startScanner()
     */
    private void startScannerActivity() {
        if (!vm.isScannerStarted()) {
            vm.setScannerStarted(true);
            scannerActivityLauncher.launch(
                    ScannerContract.createDefaultOptions(vm.getCameraConfig()));
        }
    }

    /**
     * Clean up after scanning.
     *
     * @param done flag, are we done scanning?
     */
    private void onScanningFinished(final boolean done) {
        if (useEmbeddedScanner) {
            if (scanner != null) {
                scanner.stop();
                getLifecycle().removeObserver(scanner);
                scanner = null;
            }
            updateEmbeddedScannerViewsVisibility(false);
        }
        vm.setScannerStarted(false);
        if (done) {
            setScannerMode(ScanMode.Off);
        }
    }

    private void setScannerMode(@NonNull final ScanMode scanMode) {
        vm.setScannerMode(scanMode);
        updateQueue();
    }

    /**
     * Prepare to search.
     *
     * @param code to search for
     */
    private void prepare(@NonNull final ISBN code) {
        if (isQueuePopulated() || vm.getScannerMode() == ScanMode.Batch) {
            preSearchBatch(code);
        } else {
            preSearchInteractively(code);
        }
    }

    /**
     * We're running in simple interactive mode.
     * Prepare to search.
     * Check for codes already existing locally, interact with the user as needed.
     *
     * @param code to search for
     *
     * @see #prepare(ISBN)
     */
    private void preSearchInteractively(@NonNull final ISBN code) {
        // paranoia: we should not be in the situation... flw
        // check if we have an active search, if so, quit silently.
        if (isSearchActive()) {
            return;
        }

        // Check if the ISBN already exists in our database,
        final List<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (!existingIds.isEmpty()) {
            onBookAlreadyPresent(code, existingIds, () -> startSearch(code));
            return;
        }

        setEnableProgressMessages(true);

        // Start the search
        final int searchId = startSearch(code);
        if (searchId == 0) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_book_search_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * We're running in simple interactive mode.
     * Called <strong>before</strong> a search is started if the entered code
     * is already present in the local database.
     *
     * @param code        which was already present
     * @param existingIds the local books which use that code
     * @param onAdd       action to take when the user selects "add anyway"
     *
     * @see #preSearchInteractively(ISBN)
     */
    @SuppressWarnings("TypeMayBeWeakened")
    private void onBookAlreadyPresent(@NonNull final ISBN code,
                                      @NonNull final List<Pair<Long, String>> existingIds,
                                      @NonNull final Runnable onAdd) {
        // always quit scanning until the user manually starts it again
        onScanningFinished(true);

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
     * Prepare to search. We're running with a queue, add the code to the queue.
     * Only interact with the user when adding/starting a search failed.
     * <p>
     * REMINDER: We have a queue, but we can get here from either of these:
     * <ul>
     *     <li>manual entry / single scan (duplicates n/a);
     *         <strong>while in queue/batch mode</strong></li>
     *     <li>continuous scan or queue/batch mode</li>
     * </ul>
     *
     * @param code to search for
     */
    private void preSearchBatch(@NonNull final ISBN code) {
        final int searchId = addToQueue(code);

        // Is this a manual search (user enters ISBN)
        // or a single scan (scan returns ISBN)
        if (vm.getScannerMode() == ScanMode.Off
            || vm.getScannerMode() == ScanMode.Single) {
            // duplicate check N/A
            if (searchId > 0) {
                // Search started, remove previous, and focus for new input
                vm.setIsbnText("");
                modelToView();
                vb.isbn.requestFocus();
            } else {
                // Starting a new search failed.
                // Show a quick/simple message.
                Snackbar.make(vb.getRoot(), R.string.error_book_search_failed,
                              Snackbar.LENGTH_SHORT)
                        .show();
                // allow user to change their previous input
                vb.isbn.requestFocus();
            }
            return;
        }

        // We're either in ScanMode.Continuous or ScanMode.Batch
        // Was a search started? or it was a duplicate (silently rejected)?
        if (searchId > 0 || searchId == QueueViewModel.SEARCH_DUPLICATE_ITEM) {
            // Go scan the next book.
            startScanner();
            return;
        }

        // We're either in ScanMode.Continuous or ScanMode.Batch
        // Starting a new search failed, ask the user what to do next.
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.progress_msg_searching)
                .setMessage(R.string.error_book_search_failed)
                .setNegativeButton(R.string.action_stop_scanning, (d, w) -> {
                    d.dismiss();
                    onScanningFinished(true);
                })
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    startScanner();
                })
                .create()
                .show();
    }

    /**
     * Sits between {@link #prepare(ISBN)} and {@link #startSearch(BookSearchCriteria)}
     * Needed to:
     * - support {@link ScanMode#Batch} mode.
     * - allow starting with or without calling {@link #onBookAlreadyPresent}
     *
     * @param code to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     */
    @Override
    protected int startSearch(@NonNull final ISBN code) {
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setIsbnFromScan(code, vm.getScannerMode());

        return startSearch(criteria);
    }

    /**
     * The scanner returned a barcode.
     *
     * @param barCode as returned by the scanner
     */
    private void onBarcodeScanned(@NonNull final String barCode) {
        final boolean strictIsbn = BookSearchCriteria.isStrictIsbnGlobal();

        final ISBN code = ISBN.parse(barCode, strictIsbn);
        if (code.isValid()) {
            if (strictIsbn) {
                SoundManager.beepOnValidIsbn();
            } else {
                SoundManager.beepOnBarcodeFound();
            }

            switch (vm.getScannerMode()) {
                case Batch: {
                    preSearchBatch(code);
                    break;
                }
                case Single: {
                    onScanningFinished(true);
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepare(code);
                    break;
                }
                case Continuous: {
                    onScanningFinished(false);
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepare(code);
                    break;
                }
            }
        } else {
            SoundManager.beepOnInvalidIsbn();

            if (vm.getScannerMode() == ScanMode.Batch) {
                // invalid code but we're in batch mode.
                // Just ignore the bad code and scan the next book.
                startScanner();
            } else {
                // invalid code, always quit scanning and let the user edit the code
                onScanningFinished(true);
                vm.setIsbnText(barCode);
                modelToView();
                vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code,
                                              code.asText()));
            }
        }
    }

    /**
     * Handle the result interactively.
     *
     * @param result results of the search
     */
    @Override
    void onSearchResults(@NonNull final BookSearchResult result) {
        // Do not check for result.hasBook() here,
        // we want to allow the user to edit an incomplete book search manually
        editBook(result.getBook(), vm.getStyle());
    }

    @Override
    protected void onSearchResultsSaveBook(@NonNull final Book book) {
        final Context context = getContext();
        try {
            //noinspection DataFlowIssue
            vm.onSaveBook(context, book);
        } catch (@NonNull final StorageException | DaoWriteException e) {
            // Should never get here unless disk-full.
            // If we do... the book result is discarded.
            ErrorDialog.show(context, TAG, e);
        }
    }

    @Override
    void onClearSearchCriteria() {
        vm.setIsbnText(null);
        vb.isbn.setText("");
    }

    /**
     * The user finished editing a book. Store results and continue scanning if applicable.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        onClearSearchCriteria();
        updateQueue();
        vm.onBookEditingDone(data);

        if (vm.getScannerMode() == ScanMode.Continuous) {
            // scan another book until the user cancels
            startScanner();
        }
    }

    @Override
    void importFromFile() {
        // Show the tip, then call the super for the actual import.
        TipManager.getInstance().show(requireContext(), Tip.IMPORT_ISBN_LIST,
                                      SearchBookByIsbnFragment.super::importFromFile);
    }

    /**
     * DEBUG only. Adds buttons to simulate the scanner in different modes.
     */
    private void dbgAddScanButtons() {
        if (vb.contentBody != null) {
            final Button single = new Button(getContext());
            final int singleButtonId = View.generateViewId();
            single.setId(singleButtonId);
            single.setText(R.string.option_fab_add_book_by_barcode_scan);
            single.setOnClickListener(v -> {
                //noinspection DataFlowIssue
                final String s = vb.isbn.getText().toString().strip();
                vb.isbn.setText("");
                setScannerMode(ScanMode.Single);
                onBarcodeScanned(s);
            });

            vb.contentBody.addView(single, 0);
            ConstraintSet set = new ConstraintSet();
            set.clone(vb.contentBody);
            set.connect(singleButtonId, ConstraintSet.TOP,
                        R.id.queue, ConstraintSet.BOTTOM);
            set.connect(singleButtonId, ConstraintSet.START,
                        R.id.queue, ConstraintSet.START);
            set.applyTo(vb.contentBody);

            final Button batch = new Button(getContext());
            final int batchButtonId = View.generateViewId();
            batch.setId(batchButtonId);
            batch.setText(R.string.option_fab_add_book_by_barcode_scan_batch);
            batch.setOnClickListener(v -> {
                //noinspection DataFlowIssue
                final String s = vb.isbn.getText().toString().strip();
                vb.isbn.setText("");
                setScannerMode(ScanMode.Batch);
                onBarcodeScanned(s);
            });

            vb.contentBody.addView(batch, 0);
            set = new ConstraintSet();
            set.clone(vb.contentBody);
            set.connect(batchButtonId, ConstraintSet.TOP,
                        singleButtonId, ConstraintSet.BOTTOM);
            set.connect(batchButtonId, ConstraintSet.START,
                        singleButtonId, ConstraintSet.START);
            set.applyTo(vb.contentBody);
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.search_by_isbn, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(BookSearchCriteria.isStrictIsbnGlobal());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_BARCODE_SCAN) {
                setScannerMode(ScanMode.getScannerModeSingle());
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                // don't clear
                setScannerMode(ScanMode.Batch);
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_IMPORT) {
                importFromFile();
                return true;

            } else if (menuItemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                BookSearchCriteria.setStrictIsbnDefault(checked);

                final CodeValidity validity = checked ? CodeValidity.Isbn
                                                      : CodeValidity.NoChecks;
                isbnCleanupTextWatcher.setValidityLevel(validity);
                isbnValidationTextWatcher.setValidityLevel(validity);
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_SETTINGS) {
                final BarcodePreferenceFragment fragment = new BarcodePreferenceFragment();
                vm.inSettings();
                getParentFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .addToBackStack(BarcodePreferenceFragment.TAG)
                        .replace(R.id.content_frame, fragment, BarcodePreferenceFragment.TAG)
                        .commit();
            }

            return false;
        }
    }
}
