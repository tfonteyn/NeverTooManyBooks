/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.security.cert.CertificateException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * A delegate class for handling a Calibre enabled Book.
 * <p>
 * Context/View dependent!
 */
public class CalibreHandler {

    /** Whether to show any sync menus at all. */
    public static final String PK_ENABLED = CalibreContentServer.PREF_KEY + ".enabled";

    @NonNull
    private final CalibreHandlerViewModel mVm;

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> mPickFolderLauncher;

    /** The host Window. */
    private Window mWindow;

    /** The host view; used for context, resources. */
    private View mView;

    /** Optionally set during initializing. */
    @Nullable
    private View mProgressFrame;
    /** Created only when actually needed. */
    @Nullable
    private ProgressDelegate mProgressDelegate;

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreHandler(@NonNull final Context context,
                          @NonNull final ViewModelStoreOwner viewModelStoreOwner)
            throws CertificateException {

        mVm = new ViewModelProvider(viewModelStoreOwner).get(CalibreHandlerViewModel.class);
        mVm.init(context);
    }

    /**
     * Check if SYNC menus should be shown at all.
     *
     * @param global Global preferences
     *
     * @return {@code true} if menus should be shown
     */
    @AnyThread
    public static boolean isSyncEnabled(@NonNull final SharedPreferences global) {
        return global.getBoolean(PK_ENABLED, false);
    }

    /**
     * Initializer for use from within an Activity.
     *
     * @param activity the hosting Activity
     * @param view     the root view of the Activity (e.g. mVb.getRoot())
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
        //noinspection ConstantConditions
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
        mWindow = window;
        mView = view;

        mPickFolderLauncher = caller.registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri != null) {
                        CalibreContentServer.setFolderUri(mView.getContext(), uri);
                        mVm.startDownload(mVm.getAndResetTempBook(), uri);
                    }
                });

        mVm.onFinished().observe(lifecycleOwner, this::onFinished);
        mVm.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mVm.onFailure().observe(lifecycleOwner, this::onFailure);
        mVm.onProgress().observe(lifecycleOwner, this::onProgress);
    }

    public void onCreateMenu(@NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_CALIBRE) == null) {
            inflater.inflate(R.menu.sm_calibre, menu);
        }
    }

    /**
     * Add a Calibre submenu with read/download/settings options as appropriate for the given Book.
     *
     * @param menu to add to
     * @param book to use
     */
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final Book book) {

        final boolean hasCalibreId = !book.getString(DBKey.KEY_CALIBRE_BOOK_UUID).isEmpty();

        menu.findItem(R.id.SUBMENU_CALIBRE).setVisible(hasCalibreId);
        if (hasCalibreId) {
            if (CalibreContentServer.getFolderUri(context).isPresent()) {
                // conditional
                menu.findItem(R.id.MENU_CALIBRE_READ)
                    .setVisible(mVm.existsLocally(context, book));

                // always shown
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD)
                    .setTitle(context.getString(
                            R.string.menu_download_ebook_format,
                            book.getString(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT)))
                    .setVisible(true);

                // don't show
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setVisible(false);

            } else {
                // Calibre is enabled, but the folder is not set
                menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setTitle(R.string.menu_set_download_folder)
                    .setVisible(true);
            }

        } else {
            menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_SETTINGS).setVisible(false);
        }
    }

    /**
     * Called from a list screen. i.e. the data comes from a row {@link DataHolder}.
     *
     * @param menuItem to check
     * @param rowData  data to use
     */
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final DataHolder rowData) {
        return onMenuItemSelected(context, menuItem, DataHolderUtils.requireBook(rowData));
    }

    /**
     * Called from a details screen. i.e. the data comes from a {@link Book}.
     *
     * @param menuItem to check
     * @param book     data to use
     */
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final Book book) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_CALIBRE_READ) {
            // Open the given book for reading.
            // This only works if the user has not renamed the file outside of this app.
            try {
                final Uri uri = mVm.getDocumentUri(context, book);
                openBookUri(context, uri);
            } catch (@NonNull final FileNotFoundException e) {
                Snackbar.make(mView, R.string.httpErrorFileNotFound, Snackbar.LENGTH_LONG).show();
            }
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_DOWNLOAD) {
            final Optional<Uri> optionalUri = CalibreContentServer.getFolderUri(context);
            if (optionalUri.isPresent()) {
                mVm.startDownload(book, optionalUri.get());
            } else {
                // ask for a download folder
                mVm.setTempBook(book);
                mPickFolderLauncher.launch(null);
            }
            return true;

        }
        return false;
    }

    private void openBookUri(@NonNull final Context context,
                             @NonNull final Uri uri) {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.whichViewApplication)));
    }

    private void onFinished(@NonNull final LiveDataEvent<TaskResult<Uri>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::requireResult).ifPresent(result -> Snackbar
                .make(mView, R.string.progress_end_download_successful, Snackbar.LENGTH_LONG)
                .setAction(R.string.lbl_read, v -> openBookUri(v.getContext(), result))
                .show());
    }

    private void onCancelled(@NonNull final LiveDataEvent<TaskResult<Uri>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> Snackbar
                .make(mView, R.string.cancelled, Snackbar.LENGTH_LONG).show());
    }

    private void onFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = mView.getContext();
            final String msg = ExMsg
                    .map(context, data.getResult())
                    .orElse(context.getString(R.string.error_network_site_access_failed,
                                              CalibreContentServer.getHostUrl()));

            Snackbar.make(mView, msg, Snackbar.LENGTH_LONG).show();
        });
    }

    public CalibreHandler setProgressFrame(@NonNull final View progressFrame) {
        mProgressFrame = progressFrame;
        return this;
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (mProgressFrame != null) {
                if (mProgressDelegate == null) {
                    mProgressDelegate = new ProgressDelegate(mProgressFrame)
                            .setTitle(R.string.progress_msg_downloading)
                            .setPreventSleep(true)
                            .setIndeterminate(true)
                            .setOnCancelListener(v -> mVm.cancelTask(data.taskId))
                            .show(() -> mWindow);
                }
                mProgressDelegate.onProgress(data);
            }
        });
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            mProgressDelegate.dismiss(mWindow);
            mProgressDelegate = null;
        }
    }
}
