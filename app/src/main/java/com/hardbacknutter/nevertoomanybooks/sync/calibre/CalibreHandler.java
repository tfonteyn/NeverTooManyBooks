/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.security.cert.CertificateException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetDirectoryUriContract;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;

/**
 * A delegate class for handling a Calibre enabled Book.
 * <p>
 * Context/View dependent!
 */
public class CalibreHandler {

    private static final String TAG = "CalibreHandler";

    /** Whether to show any sync menus at all. */
    public static final String PK_ENABLED = CalibreContentServer.PREF_KEY + ".enabled";

    @NonNull
    private final CalibreHandlerViewModel vm;

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> folderPickerLauncher;

    /** The host Window. */
    private Window hostWindow;

    /** The host view; used for context, resources. */
    private View hostView;

    /** Optionally set during initializing. */
    @Nullable
    private View progressFrame;
    /** Created only when actually needed. */
    @Nullable
    private ProgressDelegate progressDelegate;

    /**
     * Constructor.
     *
     * @param context             Current context
     * @param viewModelStoreOwner the object which will own the internal ViewModel in this class.
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreHandler(@NonNull final Context context,
                          @NonNull final ViewModelStoreOwner viewModelStoreOwner)
            throws CertificateException {

        vm = new ViewModelProvider(viewModelStoreOwner).get(CalibreHandlerViewModel.class);
        vm.init(context);
    }

    /**
     * Check if SYNC menus should be shown at all.
     *
     * @param context Current context
     *
     * @return {@code true} if menus should be shown
     */
    @AnyThread
    public static boolean isSyncEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PK_ENABLED, false);
    }

    /**
     * Check if the given book has all the Calibre keys needed.
     *
     * @param book to check
     *
     * @return {@code true} is this book <strong>is</strong> a CalibreBook
     */
    static boolean isCalibreBook(@NonNull final Book book) {
        return book.contains(DBKey.CALIBRE_BOOK_ID)
               && !book.getString(DBKey.CALIBRE_BOOK_UUID).isEmpty()
               && !book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT).isEmpty()
               && book.contains(DBKey.FK_CALIBRE_LIBRARY);
    }

    /**
     * Initializer for use from within an Activity.
     *
     * @param activity the hosting Activity
     * @param view     the root view of the Activity
     */
    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(activity.getWindow(),
                      view,
                      activity,
                      activity);
    }

    /**
     * Initializer for use from within an Fragment.
     *
     * @param fragment the hosting Fragment
     */
    public void onViewCreated(@NonNull final Fragment fragment) {
        //noinspection DataFlowIssue
        onViewCreated(fragment.getActivity().getWindow(),
                      fragment.getView(),
                      fragment.getViewLifecycleOwner(),
                      fragment);
    }

    /**
     * Host (Fragment/Activity) independent initializer.
     *
     * @param window the hosting component window
     * @param view   the hosting component root view
     */
    private void onViewCreated(@NonNull final Window window,
                               @NonNull final View view,
                               @NonNull final LifecycleOwner lifecycleOwner,
                               @NonNull final ActivityResultCaller caller) {
        hostWindow = window;
        hostView = view;

        folderPickerLauncher = caller.registerForActivityResult(
                new GetDirectoryUriContract(), o -> o.ifPresent(uri -> {
                    CalibreContentServer.setFolderUri(hostView.getContext(), uri);
                    vm.startDownload(vm.getAndResetTempBook(), uri);
                }));

        vm.onFinished().observe(lifecycleOwner, this::onFinished);
        vm.onCancelled().observe(lifecycleOwner, this::onCancelled);
        vm.onFailure().observe(lifecycleOwner, this::onFailure);
        vm.onProgress().observe(lifecycleOwner, this::onProgress);
    }

    /**
     * Inflate/create the menu.
     *
     * @param menu     The Menu to inflate into
     * @param inflater to use
     */
    public void onCreateMenu(@NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_CALIBRE) == null) {
            inflater.inflate(R.menu.sm_calibre, menu);
        }
    }

    /**
     * Add a Calibre submenu with read/download/settings options as appropriate for the given Book.
     *
     * @param context Current context
     * @param menu    to add to
     * @param book    to use
     */
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final Book book) {

        if (isCalibreBook(book)) {
            menu.findItem(R.id.SUBMENU_CALIBRE).setVisible(true);
            if (CalibreContentServer.getFolderUri(context).isPresent()) {
                // conditional
                menu.findItem(R.id.MENU_CALIBRE_READ)
                    .setVisible(vm.existsLocally(context, book));

                // always shown
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD)
                    .setTitle(context.getString(
                            R.string.option_download_ebook_format,
                            book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT)))
                    .setVisible(true);

                // don't show
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setVisible(false);

            } else {
                // Calibre is enabled, but the folder is not set
                menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setTitle(R.string.option_download_set_folder)
                    .setVisible(true);
            }

        } else {
            menu.findItem(R.id.SUBMENU_CALIBRE).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_SETTINGS).setVisible(false);
        }
    }

    /**
     * Handle the menu item selected.
     *
     * @param context  Current context
     * @param menuItem the user selected
     * @param book     data to use
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final Book book) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_CALIBRE_READ) {
            // Open the given book for reading.
            // This only works if the user has not renamed the file outside of this app.
            try {
                final Uri uri = vm.getDocumentUri(context, book);
                openBookUri(context, uri);
            } catch (@NonNull final FileNotFoundException e) {
                Snackbar.make(hostView, R.string.httpErrorFileNotFound,
                              Snackbar.LENGTH_LONG).show();
            }
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_DOWNLOAD) {
            final Optional<Uri> optionalUri = CalibreContentServer.getFolderUri(context);
            if (optionalUri.isPresent()) {
                vm.startDownload(book, optionalUri.get());
            } else {
                // ask for a download folder
                vm.setTempBook(book);
                folderPickerLauncher.launch(null);
            }
            return true;

        }
        return false;
    }

    private void openBookUri(@NonNull final Context context,
                             @NonNull final Uri uri) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.whichViewApplication)));
    }

    private void onFinished(@NonNull final LiveDataEvent<Uri> message) {
        closeProgressDialog();

        message.process(uri -> Snackbar
                .make(hostView, R.string.info_download_successful, Snackbar.LENGTH_LONG)
                .setAction(R.string.lbl_read, v -> openBookUri(v.getContext(), uri))
                .show());
    }

    private void onCancelled(@NonNull final LiveDataEvent<Uri> message) {
        closeProgressDialog();

        message.process(ignored -> Snackbar
                .make(hostView, R.string.cancelled, Snackbar.LENGTH_LONG).show());
    }

    private void onFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            final Context context = hostView.getContext();
            ErrorDialog.show(context, TAG, e,
                             context.getString(R.string.lbl_calibre_content_server),
                             context.getString(R.string.error_network_site_access_failed,
                                               CalibreContentServer.getHostUrl(context)));
        });
    }

    @NonNull
    public CalibreHandler setProgressFrame(@NonNull final View progressFrame) {
        this.progressFrame = progressFrame;
        return this;
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressFrame != null) {
                if (progressDelegate == null) {
                    progressDelegate = new ProgressDelegate(progressFrame)
                            .setTitle(R.string.progress_msg_downloading)
                            .setPreventSleep(true)
                            .setIndeterminate(true)
                            .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                            .show(() -> hostWindow);
                }
                progressDelegate.onProgress(progress);
            }
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            progressDelegate.dismiss(hostWindow);
            progressDelegate = null;
        }
    }
}
