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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import androidx.constraintlayout.widget.ConstraintLayout;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.entities.codes.Barcode;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeValidity;
import com.hardbacknutter.nevertoomanybooks.core.widgets.IsbnTextInputEditText;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
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
 *     <li>{@link #prepare(ProductCode)}</li>
 *     <li>{@link #preSearchInteractively(ProductCode)}</li>
 *     <li>{@link #startSearch(ProductCode)}</li>
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
 *     <li>{@link #onBarcodeScanned(Barcode)}</li>
 *     <li>{@link #prepare(ProductCode)}</li>
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
 *     <li>{@link #onBarcodeScanned(Barcode)}</li>
 *     <li>{@link #prepare(ProductCode)}</li>
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
 *     <li>{@link #onBarcodeScanned(Barcode)}</li>
 *     <li>{@link #preSearchBatch(ProductCode)}</li>
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
        extends QueueFragment {

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

    /** Handles {@link ProductCode} and fragment data. */
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
            dbgAddScanButtons(vb.contentBody, vb.isbn);
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
        explainSitesSupport();
        vb.isbn.requestFocus();
        if (vm.isStartScanner()) {
            startScanner();
        }
    }

    @Override
    protected void explainSitesSupport() {
        final Context context = getContext();

        final Set<SearchEngine.SearchBy> searchBy = Set.of(SearchEngine.SearchBy.Isbn,
                                                           SearchEngine.SearchBy.Issn,
                                                           SearchEngine.SearchBy.Barcode);

        //noinspection DataFlowIssue
        final List<String> engines = coordinator
                .getSiteList()
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .filter(engineId -> searchBy.stream().anyMatch(engineId::supports))
                .map(engineId -> engineId.getName(context))
                .collect(Collectors.toList());

        if (!engines.isEmpty()) {
            // Explicitly let the user known which sites will be searched.
            vb.keypad.btnSearch.setEnabled(true);
            final int textColor = AttrUtils
                    .getColorInt(context, com.google.android.material.R.attr.colorOnBackground);
            vb.txtLimitations.setTextColor(textColor);
            vb.txtLimitations.setText(getString(R.string.info_site_list,
                                                String.join(", ", engines)));
            return;
        }

        // There are no sites which support searching by Text
        vb.keypad.btnSearch.setEnabled(false);
        // don't use android.R.attr.colorError which is API 29+ only
        //noinspection DataFlowIssue
        final int textColor = AttrUtils
                .getColorInt(context, androidx.appcompat.R.attr.colorError);
        vb.txtLimitations.setTextColor(textColor);

        final String methods = new StringJoiner(", ")
            .add(getString(R.string.lbl_isbn))
            .add(getString(R.string.lbl_issn))
            .add(getString(R.string.lbl_barcode))
                .toString();
        vb.txtLimitations.setText(getString(R.string.warning_no_site_supports_this_method,
                                            methods));
        vb.txtLimitations.setVisibility(View.VISIBLE);
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
        final ProductCodeValidity validity = BookSearchCriteria.isStrictIsbnGlobal()
                                       ? ProductCodeValidity.Isbn
                                       : ProductCodeValidity.NoChecks;

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
            final ProductCode productCode = ISBN.parse(vm.getIsbnText(), strictIsbn);
            if (!productCode.isValid()) {
                final String text = productCode.asText();
                if (text.isEmpty()) {
                    vb.lblIsbn.setError(getString(R.string.vldt_non_blank_required));
                } else {
                    vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code, text));
                }
                return;
            }

            prepare(productCode);
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
                          private Result lastResult;

                          @Override
                          public void onResult(@NonNull final Result result) {
                              if (!Objects.equals(result, lastResult)) {
                                  lastResult = result;
                                  Barcode.from(result).ifPresent(
                                          SearchBookByIsbnFragment.this::onBarcodeScanned);
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
                .setBarcodeFormats(ScannerContract.BARCODES)
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
     * @param productCode to search for
     */
    private void prepare(@NonNull final ProductCode productCode) {
        if (isQueuePopulated() || vm.getScannerMode() == ScanMode.Batch) {
            preSearchBatch(productCode);
        } else {
            preSearchInteractively(productCode);
        }
    }

    /**
     * We're running in simple interactive mode.
     * Prepare to search.
     * Check for codes already existing locally, interact with the user as needed.
     *
     * @param productCode to search for
     *
     * @see #prepare(ProductCode)
     */
    private void preSearchInteractively(@NonNull final ProductCode productCode) {
        // paranoia: we should not be in the situation... flw
        // check if we have an active search, if so, quit silently.
        if (isSearchActive()) {
            return;
        }

        // Check if the product code already exists in our database,
        final List<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(productCode);
        if (!existingIds.isEmpty()) {
            onBookAlreadyPresent(productCode, existingIds, () -> startSearch(productCode));
            return;
        }

        setEnableProgressMessages(true);

        // Start the search
        final int searchId = startSearch(productCode);
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
     * @param productCode which was already present
     * @param existingIds the local books which use that code
     * @param onAdd       action to take when the user selects "add anyway"
     *
     * @see #preSearchInteractively(ProductCode)
     */
    private void onBookAlreadyPresent(@NonNull final ProductCode productCode,
                                      @NonNull final List<Pair<Long, String>> existingIds,
                                      @NonNull final Runnable onAdd) {
        // always quit scanning until the user manually starts it again
        onScanningFinished(true);

        // we always use the first one... really should offer the user a choice.
        final long firstFound = existingIds.get(0).first;
        // Show the "title (isbn)" with a caution message
        final String msg = getString(R.string.a_bracket_b_bracket,
                                     existingIds.get(0).second, productCode.asText())
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
     * @param productCode to search for
     */
    private void preSearchBatch(@NonNull final ProductCode productCode) {
        final int searchId = addToQueue(productCode);

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

    @Override
    protected int startSearch(@NonNull final ProductCode productCode) {
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setProductCodeFromScan(productCode, vm.getScannerMode());
        return coordinator.search(criteria);
    }

    /**
     * The scanner returned a barcode.
     *
     * @param barcode to dissect
     */
    private void onBarcodeScanned(@NonNull final Barcode barcode) {

        final boolean strictIsbn = BookSearchCriteria.isStrictIsbnGlobal();

        final ProductCode productCode = ISBN.parse(barcode, strictIsbn);
        if (productCode.isValid()) {
            if (strictIsbn) {
                SoundManager.beepOnValidIsbn();
            } else {
                SoundManager.beepOnBarcodeFound();
            }

            switch (vm.getScannerMode()) {
                case Batch: {
                    preSearchBatch(productCode);
                    break;
                }
                case Single: {
                    onScanningFinished(true);
                    vm.setIsbnText(productCode.asText());
                    modelToView();
                    prepare(productCode);
                    break;
                }
                case Continuous: {
                    onScanningFinished(false);
                    vm.setIsbnText(productCode.asText());
                    modelToView();
                    prepare(productCode);
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
                vm.setIsbnText(productCode.asText());
                modelToView();
                vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code,
                                              productCode.asText()));
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
    private void dbgAddScanButtons(@Nullable final ConstraintLayout container,
                                   @NonNull final IsbnTextInputEditText isbnView) {
        if (container != null) {
            int btnId;

            btnId = dbgAddScanButton(container, R.id.queue, ScanMode.Single, isbnView,
                                     getString(R.string.option_fab_add_book_by_barcode_scan));

            btnId = dbgAddScanButton(container, btnId, ScanMode.Batch, isbnView,
                                     getString(R.string.option_fab_add_book_by_barcode_scan_batch));

            // text='9781935098553', barcodeFormat=EAN_13, issueNumber=null,
            // suggestedPrice='$16.95', extension='51695'
            btnId = dbgAddScanButton(container, btnId, "b1", "barcode1_isbn_add_5");
            // text='9772652817008', barcodeFormat=EAN_13, issueNumber=1,
            // suggestedPrice='null', extension='01'
            btnId = dbgAddScanButton(container, btnId, "b2", "barcode2_issn13_add_2");
            // text='9771234567898', barcodeFormat=EAN_13, issueNumber=1,
            // suggestedPrice='null', extension='01'}
            btnId = dbgAddScanButton(container, btnId, "b3", "barcode3_issn13_add_2");
        }
    }

    private int dbgAddScanButton(@NonNull final ConstraintLayout container,
                                 final int anchor,
                                 @NonNull final ScanMode scanMode,
                                 @NonNull final IsbnTextInputEditText isbnView,
                                 @NonNull final CharSequence text) {
        final Button btn = new Button(getContext());
        final int btnId = View.generateViewId();
        btn.setId(btnId);
        btn.setText(text);
        btn.setOnClickListener(v -> {
            //noinspection DataFlowIssue
            final String s = isbnView.getText().toString().strip();
            isbnView.setText("");
            setScannerMode(scanMode);
            onBarcodeScanned(new Barcode(s, null,
                                         null, null, null));
        });

        container.addView(btn, 0);
        final ConstraintSet set = new ConstraintSet();
        set.clone(container);
        set.connect(btnId, ConstraintSet.TOP, anchor, ConstraintSet.BOTTOM);
        set.connect(btnId, ConstraintSet.START, anchor, ConstraintSet.START);
        set.applyTo(container);
        return btnId;
    }

    private int dbgAddScanButton(@NonNull final ConstraintLayout container,
                                 final int anchor,
                                 @NonNull final CharSequence text,
                                 @NonNull final String barcodeFile) {
        final Button btn = new Button(getContext());
        final int btnId = View.generateViewId();
        btn.setId(btnId);
        btn.setText(text);
        btn.setOnClickListener(v -> {
            final int resId = getResources().getIdentifier(barcodeFile, "drawable",
                                                           getContext().getPackageName());

            final Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
            if (bitmap != null) {
                final Optional<Barcode> scannerResult = BarcodeDecoder
                        .decodeBarcodeFromBitmap(bitmap);
                if (scannerResult.isPresent()) {
                    setScannerMode(ScanMode.Single);
                    onBarcodeScanned(scannerResult.get());
                } else {
                    Snackbar.make(v, "No barcode found in image.",
                                  Snackbar.LENGTH_SHORT).show();
                }
            } else {
                Snackbar.make(v, "Bitmap failure.",
                              Snackbar.LENGTH_SHORT).show();
            }
        });

        container.addView(btn, 0);
        final ConstraintSet set = new ConstraintSet();
        set.clone(container);
        set.connect(btnId, ConstraintSet.TOP, anchor, ConstraintSet.BOTTOM);
        set.connect(btnId, ConstraintSet.START, anchor, ConstraintSet.START);
        set.applyTo(container);

        return btnId;
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
            menu.findItem(R.id.MENU_PRODUCT_CODE_VALIDITY_STRICT)
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

            } else if (menuItemId == R.id.MENU_PRODUCT_CODE_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                BookSearchCriteria.setStrictIsbnDefault(checked);

                final ProductCodeValidity validity = checked ? ProductCodeValidity.Isbn
                                                             : ProductCodeValidity.NoChecks;
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
