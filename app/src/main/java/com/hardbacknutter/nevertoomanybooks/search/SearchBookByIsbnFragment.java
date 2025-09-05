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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
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
import android.view.animation.AnimationUtils;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
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
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Use-cases / logic flow....   might contain errors...
 * <p>
 * - start batch scan
 * - disable progress
 * - q items populated by scanner
 * - q can be cleared using button
 * - scanner delivers item to be added:
 * - search started, added to the queue (wil have searchId)
 * - starting search fails, ask user to:
 * - stop scanning
 * - restart scanner
 * <p>
 * - usr clicks on q item:
 * - item has searchId (always)
 * - dialog title/msg depending on what we can show
 * - result present:
 * - user can delete item
 * - user can edit book
 * - must be deleted from the q before edit starts
 * - no result
 * - user can delete item
 * <p>
 * - user stops scanning
 * - progress STILL disabled
 * <p>
 * - q keeps running
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
 * - populate q in one go + start all searches
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
 *     <li>{@link #preSearchAddToQueue(ISBN)}</li>
 *     <li>{@link SearchBookByIsbnViewModel#addToQueueAndStartSearch(Context, IsbnQueue.Item,
 *                                                                   Function)}</li>
 *     <li>scanner starts again</li>
 * </ol>
 * <ol>
 *     <li>{@link SearchBookBaseFragment}#onSearchFinished(LiveDataEvent)</li>
 *     <li>{@link #onSearchFinished(BookSearchResult)}</li>
 *     <li>{@link SearchBookByIsbnViewModel#onQueueSearchResults(BookSearchResult)} </li>
 *     <li>all this in the background as the scanner is still running</li>
 * </ol>
 * <p>
 * ============================================================
 * <p>
 * After a batch scan was stopped, we have a queue.
 * <p>
 * Manual Entry
 * <ol>
 *     <li>{@link SearchBookByIsbnViewModel#onQueueSearchResults(BookSearchResult)} </li>
 *     <li>done.</li>
 * </ol>
 * Single scan
 * <ol>
 *     <li>{@link SearchBookByIsbnViewModel#onQueueSearchResults(BookSearchResult)} </li>
 *     <li>done.</li>
 * </ol>
 * Continuous scan
 * <ol>
 *     <li>{@link SearchBookByIsbnViewModel#onQueueSearchResults(BookSearchResult)} </li>
 *     <li>scanner starts again</li>
 * </ol>
 * Batch scan
 * <ol>
 *     <li>{@link SearchBookByIsbnViewModel#onQueueSearchResults(BookSearchResult)} </li>
 *     <li>scanner starts again</li>
 * </ol>
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

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
    /** Embedded scanner usage: Does the device have a torch light. */
    private boolean hasTorch;
    /** Embedded scanner usage: Does the camera have a zoom. */
    private boolean hasZoom;

    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;

    private SearchBookByIsbnViewModel vm;

    /**
     * Intercept 'back' when there are items in the queue still being searched for.
     * By default disabled.
     *
     * @see #onQueueUpdated(Iterator)
     */
    private final OnBackPressedCallback backPressedWithActiveSearches =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    new MaterialAlertDialogBuilder(getContext())
                            .setTitle(R.string.confirm_leave_isbn_search)
                            .setSingleChoiceItems(R.array.lbl_leave_isbn_search_options,
                                                  -1, (d, option) -> {
                                        d.dismiss();
                                        // option 0: Save for later
                                        // option 1: Clear queue
                                        final boolean clear = option == 1;
                                        vm.clearQueueAndCancelSearches(getContext(), coordinator,
                                                                       clear);
                                        //noinspection DataFlowIssue
                                        getActivity().setResult(Activity.RESULT_OK,
                                                                createResultIntent());
                                        getActivity().finish();
                                    })
                            .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                            .create()
                            .show();
                }
            };

    /** The user wants to import a list of ISBNs to the queue. */
    private ActivityResultLauncher<String> openUriLauncher;

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
    }

    private void createActivityLaunchers() {
        //noinspection DataFlowIssue
        permissionRequester = new PermissionRequester(getActivity(), this);
        permissionRequester.addPermission(Manifest.permission.CAMERA,
                                          getString(R.string.warning_camera_permission_required),
                                          true);

        openUriLauncher = registerForActivityResult(new GetContentUriForReadingContract(),
                                                    o -> o.ifPresent(this::onOpenUri));

        scannerActivityLauncher = registerForActivityResult(new ScannerContract(), o -> {
            vm.setScannerActivityStarted(false);
            if (o.isPresent()) {
                onBarcodeScanned(o.get());
            } else {
                // the user hit 'back' (they are done) or there was something was wrong
                switchOffScanner();
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

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedWithActiveSearches);

        useEmbeddedScanner = maybeInitEmbeddedScanner();
        if (!useEmbeddedScanner) {
            // make sure to hide all!
            updateEmbeddedScannerViewsVisibility(false);
        }

        vm.onScanQueueUpdate().observe(getViewLifecycleOwner(), this::onQueueUpdated);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_isbn);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        modelToView();

        initInputField();
        initKeypad(view);

        vb.btnClearQueue.setOnClickListener(v -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            vm.clearQueueAndCancelSearches(v.getContext(), coordinator, true);
        });

        final Context context = getContext();
        //noinspection DataFlowIssue
        final List<IsbnQueue.Item> items = IsbnQueue.readFromPreferences(context);
        if (items.isEmpty()) {
            afterOnViewCreated();
        } else {
            final int size = items.size();
            final String msg = context.getResources().getQuantityString(
                    R.plurals.confirm_queue_has_x_items, size, size);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.lbl_search_isbn)
                    .setMessage(msg)
                    .setNeutralButton(R.string.action_delete, (d, w) -> {
                        IsbnQueue.clearPreferences(context);
                        afterOnViewCreated();
                        d.dismiss();
                    })
                    .setPositiveButton(R.string.action_search, (d, w) -> startSearch(items))
                    .create()
                    .show();
        }
    }

    /**
     * Called if there is no Queue.
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

    /**
     * The input field is not being limited in length. This is to allow entering UPC_A numbers.
     */
    private void initInputField() {
        autoRemoveError(vb.isbn, vb.lblIsbn);

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

            final boolean strictIsbn = BookSearchCriteria.isStrictIsbn(v.getContext());
            final ISBN code = new ISBN(vm.getIsbnText(), strictIsbn);
            if (!code.isValid(strictIsbn)) {
                vb.lblIsbn.setError(getString(R.string.warning_x_is_not_a_valid_code,
                                              code.asText()));
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

    protected void modelToView() {
        vb.isbn.setText(vm.getIsbnText());
    }

    protected void viewToModel() {
        //noinspection DataFlowIssue
        vm.setIsbnText(vb.isbn.getText().toString().strip());
    }

    @Override
    public void onResume() {
        super.onResume();
        onQueueUpdated(vm.getScanQueue());
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
        setEnableProgressMessages(!isBatchOrHasQueue());
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
            initEmbeddedScannerViews();
            createEmbeddedScanner();
        }

        updateEmbeddedScannerViewsVisibility(true);

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
                              // quit scanning, and destroy the scanner
                              switchOffScanner();
                              getLifecycle().removeObserver(scanner);
                              scanner = null;
                          }
                      });
    }

    private void initEmbeddedScannerViews() {
        vb.zoomSlider.setValue(vm.getCameraConfig().getZoomValue());
        vb.zoomSlider.addOnChangeListener((slider, zoomValue, fromUser) -> {
            if (fromUser) {
                vm.getCameraConfig().setZoomValue(zoomValue);
                //noinspection DataFlowIssue
                scanner.setLinearZoom(zoomValue);
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
            switchOffScanner();
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
        // color then makes things needlessly complicated.
        // Hence simply swap the icon manually here.
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

        getLifecycle().addObserver(scanner);
    }

    /**
     * Start the standalone scanner activity.
     *
     * @see #startScanner()
     */
    private void startScannerActivity() {
        if (!vm.isScannerActivityStarted()) {
            vm.setScannerActivityStarted(true);
            scannerActivityLauncher.launch(
                    ScannerContract.createDefaultOptions(vm.getCameraConfig()));
        }
    }

    /**
     * Switch the scanner off.
     * <p>
     * Dev. note: this used to be called "stopScanning" but that was confusing as
     * the standalone scanner would already be stopped.
     */
    private void switchOffScanner() {
        if (useEmbeddedScanner) {
            if (scanner != null) {
                scanner.stop();
            }
            updateEmbeddedScannerViewsVisibility(false);
        }
        vm.setScannerMode(ScanMode.Off);
    }

    /**
     * Prepare to search.
     *
     * @param code to search for
     */
    private void prepare(@NonNull final ISBN code) {
        if (isBatchOrHasQueue()) {
            // We're running with a queue, add it to the queue
            preSearchAddToQueue(code);
        } else {
            // We're not using a queue.
            preSearchInteractively(code);
        }
    }

    private boolean isBatchOrHasQueue() {
        return vb.queue.getChildCount() > 0 || vm.getScannerMode() == ScanMode.Batch;
    }

    /**
     * Prepare to search. We're NOT running with a queue.
     * Check for codes already existing locally, interact with the user as needed.
     *
     * @param code to search for
     */
    private void preSearchInteractively(@NonNull final ISBN code) {
        // paranoia: we should not be in the situation... flw
        // check if we have an active search, if so, quit silently.
        if (coordinator.isSearchActive()) {
            return;
        }

        setEnableProgressMessages(!isBatchOrHasQueue());

        // Check if the ISBN already exists in our database,
        final List<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            // not there
            final int searchId = startSearch(code);
            if (searchId == 0) {
                //noinspection DataFlowIssue
                Snackbar.make(getView(), R.string.error_book_search_failed,
                              Snackbar.LENGTH_LONG).show();
            }
        } else {
            onBookAlreadyPresent(code, existingIds, () -> startSearch(code));
        }
    }

    /**
     * Called <strong>before</strong> a search is started if the entered code
     * is already present in the local database.
     *
     * @param code        which was already present
     * @param existingIds the local books which use that code
     * @param onAdd       action to take when the user selects "add anyway"
     */
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
     * Prepare to search. We're running with a queue, add the code to the queue.
     * Only interact with the user when adding/starting a search failed.
     *
     * @param code to search for
     */
    private void preSearchAddToQueue(@NonNull final ISBN code) {
        final Context context = getContext();
        //noinspection DataFlowIssue
        final int searchId = vm.addToQueueAndStartSearch(context, new IsbnQueue.Item(code),
                                                         this::startSearch);

        // REMINDER: We have a queue, but we can get here for any of these:
        // - manual entry / single scan (duplicates n/a)
        // - continuous scan / batch mode

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

        // ScanMode.Continuous / ScanMode.Batch

        if (searchId > 0 || searchId == SearchBookByIsbnViewModel.SEARCH_DUPLICATE_ISBN) {
            // Search started; or it was a duplicate (silently rejected)
            // Go scan the next book.
            startScanner();
            return;
        }

        // ScanMode.Continuous / ScanMode.Batch
        // Starting a new search failed
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.progress_msg_searching)
                .setMessage(R.string.error_book_search_failed)
                .setNegativeButton(R.string.action_stop_scanning, (d, w) -> {
                    d.dismiss();
                    switchOffScanner();
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
    private int startSearch(@NonNull final ISBN code) {
        //noinspection DataFlowIssue
        final BookSearchCriteria criteria = new BookSearchCriteria(getContext());
        criteria.setIsbnFromScan(code, vm.getScannerMode());

        return startSearch(criteria);
    }

    /**
     * Start searching the given list (coming from prefs or a file).
     *
     * @param items to search for
     */
    private void startSearch(@NonNull final List<IsbnQueue.Item> items) {
        // Do NOT switch on the ScanMode.Batch or otherwise
        // but DO hide the progress
        setEnableProgressMessages(false);
        //noinspection DataFlowIssue
        final boolean searchStarted = vm.addToQueueAndStartSearch(
                getContext(), items, this::startSearch);

        if (searchStarted) {
            Snackbar.make(vb.getRoot(), R.string.progress_msg_searching,
                          Snackbar.LENGTH_SHORT).show();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_book_search_failed,
                          Snackbar.LENGTH_LONG).show();
        }
        vb.isbn.requestFocus();
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

            switch (vm.getScannerMode()) {
                case Batch: {
                    preSearchAddToQueue(code);
                    break;
                }
                case Single: {
                    switchOffScanner();
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepare(code);
                    break;
                }
                case Continuous: {
                    // Continuous: leave the scanner on, scanning restarts when the edit is done.
                    vm.setIsbnText(barCode);
                    modelToView();
                    prepare(code);
                    break;
                }
            }
        } else {
            SoundManager.beepOnInvalidIsbn(context);

            if (vm.getScannerMode() == ScanMode.Batch) {
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

    void onSearchFinished(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchFinished",
                                        vm.getScannerMode(),
                                        "queue=" + vb.queue.getChildCount(),
                                        result);
        }

        if (vb.queue.getChildCount() == 0 && result.getScanMode() == ScanMode.Off) {
            // user interactive; we'll end up in #onSearchResults
            super.onSearchFinished(result);
        } else {
            vm.onQueueSearchResults(result);
        }
    }

    /**
     * Handle the result interactively.
     *
     * @param result results of the search
     */
    @Override
    void onSearchResults(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchResults",
                                        vm.getScannerMode(),
                                        "queue=" + vb.queue.getChildCount(),
                                        result);
        }
        final Book book = result.getBook();

        if (!hasData(book)) {
            vb.lblIsbn.setError(getString(R.string.warning_no_matching_book_found));
            return;
        }

        editBook(book, vm.getStyle());
    }

    /**
     * Called <strong>after</strong> a search to check if there is a minimal amount
     * of useful data in the given book.
     *
     * @param book to check
     *
     * @return {@code true} if there is
     */
    private boolean hasData(@NonNull final Book book) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field will be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = book.getString(DBKey.TITLE, null);
        return title != null && !title.isEmpty() || book.size() > 2;
    }

    @Override
    void onClearSearchCriteria() {
        vm.setIsbnText(null);
        vb.isbn.setText("");
    }

    /**
     * Import a list of ISBNs from the given {@link Uri}.
     *
     * @param uri as chosen by the user
     */
    private void onOpenUri(@NonNull final Uri uri) {
        try {
            //noinspection DataFlowIssue
            final List<IsbnQueue.Item> items = IsbnQueue.readFromFile(getContext(), uri);
            if (!items.isEmpty()) {
                startSearch(items);
            }
        } catch (@NonNull final IOException e) {
            Snackbar.make(vb.getRoot(), R.string.error_import_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Refresh the queue view(s) and show/hide the 'clear' button.
     *
     * @param list to display; can be empty
     */
    private void onQueueUpdated(@NonNull final Iterator<IsbnQueue.Item> list) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onQueueUpdated");
        }

        // TODO: this can cause flicker if the updates comes too fast
        if (isBatchOrHasQueue()) {
            vb.queue.removeAllViews();
        }

        while (list.hasNext()) {
            final IsbnQueue.Item item = list.next();
            final ISBN code = item.getIsbn();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "onQueueUpdated",
                                            "code=" + code,
                                            "searchId=" + item.getSearchId(),
                                            "result=" + (item.getResult() != null));
            }

            final Chip chip = new Chip(getContext(), null, R.attr.appChipInputStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setTag(item);
            chip.setText(code.asText());
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setOnClickListener(this::onQueueItemClicked);
            // experimented with adding an icon depending on the below state,
            // but that becomes the 'checkable' icon and interferes with clicking.
            // And it all becomes a bit cluttered... using separate 'hourglass' now.

            final BookSearchResult result = item.getResult();
            if (result != null) {
                final Book book = result.getBook();
                if (result.getErrorMessage() != null) {
                    if (hasData(book)) {
                        // There was an error, but the book has at least some valid data
                        chip.setChipBackgroundColorResource(
                                R.color.isbn_queue_success_with_partial_errors);
                    } else {
                        // There was an error, and the book has no useful data
                        chip.setChipBackgroundColorResource(R.color.isbn_queue_failure);
                    }
                } else if (hasData(book)) {
                    // no error, book has at least some valid data
                    chip.setChipBackgroundColorResource(R.color.isbn_queue_success);
                }
            }
            vb.queue.addView(chip);
        }

        updateQueueViewsVisibility();
    }

    /**
     * The user tapped a queue chip.
     *
     * @param chip clicked
     */
    private void onQueueItemClicked(@NonNull final View chip) {
        final IsbnQueue.Item item = (IsbnQueue.Item) chip.getTag();
        @Nullable
        final BookSearchResult result = item.getResult();

        final Context context = getContext();
        final StringJoiner sj = new StringJoiner("\n");

        // If we have a result, show some book information, otherwise only the isbn.
        if (result != null) {
            final Book book = result.getBook();

            //noinspection DataFlowIssue
            book.getPrimarySeries()
                .map(s -> s.getLabel(context, Details.Normal, vm.getStyle()))
                .ifPresent(sj::add);

            sj.add(book.getTitle());

            final Author primaryAuthor = book.getPrimaryAuthor();
            if (primaryAuthor != null) {
                //noinspection DataFlowIssue
                sj.add(primaryAuthor.getLabel(context, Details.Normal, vm.getStyle()));
            }
        }

        final String dialogTitle;
        if (sj.length() == 0) {
            dialogTitle = item.getIsbn().asText();
        } else {
            dialogTitle = sj.toString();
        }

        //noinspection DataFlowIssue
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(dialogTitle);
        if (result != null) {
            final String errorMessage = result.getErrorMessage();
            if (errorMessage != null) {
                builder.setMessage(errorMessage);
            }
        }

        builder.setNeutralButton(R.string.action_discard, (d, w) -> {
                   d.dismiss();
                   removeFromQueue(chip);
               })
               .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss());

        if (item.isSearching()) {
            // delete this one?
            builder.create()
                   .show();
            return;
        }


        if (result != null) {
            // delete this one?, or edit book?
            builder.setPositiveButton(R.string.action_edit, (d, w) -> {
                       removeFromQueue(chip);
                       d.dismiss();
                       onSearchResults(result);
                   })
                   .create()
                   .show();
            return;
        }

        //        // no result, and not searching; allow a manual search to be started
        //        builder.setPositiveButton(R.string.action_search, (d, w) -> {
        //                   final ISBN code = item.getIsbn();
        //                   vm.setIsbnText(code.asText());
        //                   modelToView();
        //                   prepareCriteria(code);
        //               })
        //               .create()
        //               .show();
    }

    /**
     * Common code to remove the given chip and cancel any active searches it owns.
     *
     * @param chip to remove
     */
    private void removeFromQueue(@NonNull final View chip) {
        final IsbnQueue.Item item = (IsbnQueue.Item) chip.getTag();
        // remove but update the view manually to avoid flicker
        //noinspection DataFlowIssue
        vm.removeFromQueueAndCancelSearch(getContext(), coordinator, item);
        vb.queue.removeView(chip);
        updateQueueViewsVisibility();
    }

    /**
     * Common code to update the visibility of the UI queue.
     */
    private void updateQueueViewsVisibility() {
        vb.queueGroup.setVisibility(isBatchOrHasQueue() ? View.VISIBLE : View.GONE);

        final boolean searching = vm.isQueueSearching();
        if (searching) {
            vb.queueProgress.setVisibility(View.VISIBLE);
            vb.queueProgress.startAnimation(
                    AnimationUtils.loadAnimation(getContext(), R.anim.rotate));
        } else {
            vb.queueProgress.setVisibility(View.GONE);
            vb.queueProgress.clearAnimation();
        }

        // Enabled when there is a non-empty queue.
        // We don't look for 'searching' or 'results' items
        backPressedWithActiveSearches.setEnabled(vb.queue.getChildCount() > 0);
    }

    /**
     * The user finished editing a book. Store results and continue scanning if applicable.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        onClearSearchCriteria();
        onQueueUpdated(vm.getScanQueue());
        vm.onBookEditingDone(data);

        if (vm.getScannerMode() == ScanMode.Continuous) {
            // scan another book until the user cancels
            startScanner();
        }
    }

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
                vm.setScannerMode(ScanMode.Single);
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
                vm.setScannerMode(ScanMode.Batch);
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
                //noinspection DataFlowIssue
                vm.setScannerMode(ScanMode.getScannerModeSingle(getContext()));
                startScanner();
                return true;

            } else if (menuItemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                // don't clear
                vm.setScannerMode(ScanMode.Batch);
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

            } else if (menuItemId == R.id.MENU_SETTINGS) {
                final BarcodePreferenceFragment fragment =
                        new BarcodePreferenceFragment();

                getParentFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .addToBackStack(BarcodePreferenceFragment.TAG)
                        .replace(R.id.main_fragment, fragment, BarcodePreferenceFragment.TAG)
                        .commit();
            }

            return false;
        }
    }
}
