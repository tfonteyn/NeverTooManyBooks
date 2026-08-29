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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.HasDefaultViewModelProviderFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.lifecycle.viewmodel.CreationExtras;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditImageContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditImageExternalContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.TakePictureContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.STask;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class ImageHandlerViewModel
        extends ViewModel {

    /** Preference key: an angle to automatically apply after taking a photo. */
    public static final String PK_CAMERA_IMAGE_AUTOROTATE = "camera.image.autorotate";
    private static final String TAG = "ImageHandlerViewModel";

    private final MutableLiveData<LiveDataEvent<TransformationResult>> transformationResult =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Throwable>> onInvalidImage =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Throwable>> onError =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<EditImageExternalContract.Input>>
            onStartExternalEditor = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<EditImageContract.Input>> onStartEditor =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TakePictureContract.Input>> onStartTakePicture =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Boolean>> onRestore =
            new MutableLiveData<>();
    private final MutableLiveData<Void> onReloadImage =
            new MutableLiveData<>();
    @NonNull
    private final SavedStateHandle savedStateHandle;
    @NonNull
    private final String savedStateTempDestFilePath;

    /** Image index we're handling. */
    @IntRange(from = 0, to = 3)
    private final int cIdx;

    /**
     * Constructor.
     *
     * @param savedStateHandle handle
     * @param cIdx             0..n image index
     *
     * @see Factory
     */
    public ImageHandlerViewModel(@NonNull final SavedStateHandle savedStateHandle,
                                 final int cIdx) {
        this.savedStateHandle = savedStateHandle;
        this.cIdx = cIdx;
        savedStateTempDestFilePath = TAG + ":tdfp:" + cIdx;
    }

    @NonNull
    LiveData<LiveDataEvent<TransformationResult>> onTransformationResult() {
        return transformationResult;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onInvalidImage() {
        return onInvalidImage;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onError() {
        return onError;
    }

    @NonNull
    LiveData<LiveDataEvent<EditImageExternalContract.Input>> onStartExternalEditor() {
        return onStartExternalEditor;
    }

    @NonNull
    LiveData<LiveDataEvent<EditImageContract.Input>> onStartEditor() {
        return onStartEditor;
    }

    @NonNull
    LiveData<LiveDataEvent<TakePictureContract.Input>> onStartTakePicture() {
        return onStartTakePicture;
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onRestore() {
        return onRestore;
    }

    @NonNull
    LiveData<Void> onReloadImage() {
        return onReloadImage;
    }

    private void setInvalidImage(@Nullable final Throwable e) {
        if (e == null) {
            onInvalidImage.setValue(LiveDataEvent.ofNullable(null));
        } else if (e.getCause() != null) {
            onInvalidImage.setValue(LiveDataEvent.ofNullable(e.getCause()));
        } else {
            onInvalidImage.setValue(LiveDataEvent.ofNullable(e));
        }
    }

    /**
     * Prepare to start the external editor.
     * <p>
     * When completed, triggers {@link #onStartExternalEditor()}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param imageOwner from which we want to edit an image
     */
    void prepareExternalEditor(@NonNull final ImageOwner imageOwner) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    try {
                        final File srcFile = createSourceTempImageFile(context, imageOwner);
                        final File dstFile = createDestinationTempImageFile();
                        return EditImageExternalContract.Input.create(srcFile, dstFile);

                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                input -> onStartExternalEditor.setValue(LiveDataEvent.of(input)),
                this::setInvalidImage);
    }

    /**
     * Prepare to start the external editor.
     * <p>
     * When completed, triggers {@link #onStartExternalEditor()}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param srcFile to edit
     */
    void prepareExternalEditor(@NonNull final File srcFile) {
        STask.execute(
                ASyncExecutor.PARALLEL,
                () -> {
                    try {
                        final File dstFile = createDestinationTempImageFile();
                        return EditImageExternalContract.Input.create(srcFile, dstFile);

                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    }
                },
                input -> onStartExternalEditor.setValue(LiveDataEvent.of(input)),
                this::setInvalidImage);
    }

    /**
     * Prepare to start the internal editor.
     * <p>
     * When completed, triggers {@link #onStartExternalEditor()}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param imageOwner from which we want to edit an image
     */
    void prepareInternalEditor(@NonNull final ImageOwner imageOwner) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    try {
                        final File srcFile = createSourceTempImageFile(context, imageOwner);
                        final File dstFile = createDestinationTempImageFile();
                        return EditImageContract.Input.create(srcFile, dstFile);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                input -> onStartEditor.setValue(LiveDataEvent.of(input)),
                this::setInvalidImage);
    }

    /**
     * Prepare to start the internal editor.
     * <p>
     * When completed, triggers {@link #onStartExternalEditor()}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param srcFile to edit
     */
    void prepareInternalEditor(@NonNull final File srcFile) {
        STask.execute(
                ASyncExecutor.PARALLEL,
                () -> {
                    try {
                        final File dstFile = createDestinationTempImageFile();
                        return EditImageContract.Input.create(srcFile, dstFile);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    }
                },
                input -> onStartEditor.setValue(LiveDataEvent.of(input)),
                this::setInvalidImage);
    }

    /**
     * Prepare to start the camera.
     * <p>
     * When completed, triggers {@link #onStartTakePicture()}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     */
    void prepareTakePicture() {
        STask.execute(
                ASyncExecutor.PARALLEL,
                () -> {
                    try {
                        final File tempFile = createDestinationTempImageFile();
                        return TakePictureContract.Input.create(tempFile);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    }
                },
                input -> onStartTakePicture.setValue(LiveDataEvent.of(input)),
                this::setInvalidImage);

    }

    /**
     * Start a rotation.
     * <p>
     * Triggers {@link #onTransformationResult()} with the result.
     * If there is no result, triggers {@link #setInvalidImage} with a {@code null}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param imageOwner from which we want to edit an image
     * @param angle      to rotate
     */
    void startRotation(@NonNull final ImageOwner imageOwner,
                       final int angle) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    try {
                        final File srcFile = createSourceTempImageFile(context, imageOwner);
                        return transform(new Transformation()
                                                 .setSource(srcFile)
                                                 .setRotation(angle),
                                         srcFile,
                                         NextAction.Done);

                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    }
                },
                result -> {
                    if (result == null) {
                        setInvalidImage(null);
                    } else {
                        transformationResult.setValue(LiveDataEvent.of(result));
                    }
                },
                this::setInvalidImage);
    }

    /**
     * Process the image received from a camera (or other scanner-like device).
     * <p>
     * Triggers {@link #onTransformationResult()} with the result.
     * If there is no result, triggers {@link #setInvalidImage} with a {@code null}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param context Current context.
     */
    void onTakePictureResult(@NonNull final Context context) {

        final File file = getDestinationImageFile();
        // The device rotation if any.
        final int surfaceRotation = getSurfaceRotation(context);
        // Should we apply an additional/explicit rotation angle?
        final int explicitRotation =
                ServiceLocator.getInstance().getSharedPreferences()
                              .getIntFromString(PK_CAMERA_IMAGE_AUTOROTATE, 0);

        // What action (if any) should we take after we're done?
        final NextAction action = NextAction.getAction();

        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    if (!ServiceLocator.getInstance().getCoverStorage().isAcceptableSize(file)) {
                        return null;
                    }

                    try {
                        return transform(new Transformation()
                                                 .setSource(file)
                                                 .setScale(true)
                                                 .setSurfaceRotation(surfaceRotation)
                                                 .setRotation(explicitRotation),
                                         file,
                                         action);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                result -> {
                    if (result == null) {
                        setInvalidImage(null);
                    } else {
                        transformationResult.setValue(LiveDataEvent.of(result));
                    }
                },
                this::setInvalidImage);
    }

    private int getSurfaceRotation(@NonNull final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return context.getDisplay().getRotation();
        } else {
            //noinspection deprecation
            return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation();
        }
    }

    /**
     * Process the image after the user edited it.
     * <p>
     * Triggers {@link #onTransformationResult()} with the result.
     * If there is no result, triggers {@link #setInvalidImage} with a {@code null}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     */
    void onPictureResult() {

        final File file = getDestinationImageFile();
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    if (!ServiceLocator.getInstance().getCoverStorage().isAcceptableSize(file)) {
                        return null;
                    }

                    try {
                        return transform(new Transformation()
                                                 .setSource(file)
                                                 .setScale(true),
                                         file,
                                         NextAction.Done);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                result -> {
                    if (result == null) {
                        setInvalidImage(null);
                    } else {
                        transformationResult.setValue(LiveDataEvent.of(result));
                    }
                },
                // N/A as no Exceptions are thrown.
                this::setInvalidImage);
    }

    /**
     * Process the image picked by the user from storage.
     * <p>
     * Triggers {@link #onTransformationResult()} with the result.
     * If there is no result, triggers {@link #setInvalidImage} with a {@code null}.
     * In case of an error, triggers {@link #setInvalidImage(Throwable)}.
     *
     * @param uri image to process
     */
    void onPictureResult(@NonNull final Uri uri) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    /*
                     2025-11-05: bug report #197:
                     java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)
                      at android.database.DatabaseUtils.readExceptionWithFileNotFoundExceptionFromParcel(DatabaseUtils.java:162)
                      at android.content.ContentProviderProxy.openTypedAssetFile(ContentProviderProxy.java:814)
                      at android.content.ContentResolver.openTypedAssetFileDescriptor(ContentResolver.java:2045)
                      at android.content.ContentResolver.openAssetFileDescriptor(ContentResolver.java:1860)
                      at android.content.ContentResolver.openInputStream(ContentResolver.java:1530)
                      at com.hardbacknutter.nevertoomanybooks.covers.ImageHandler.onPictureResult(ImageHandler.java:577)

                     So the user picked a file from storage, and we get the Uri.
                     When we open the Uri, the systems tells us
                     the file does not exist ¯\_(ツ)_/¯
                    */

                    final ServiceLocator serviceLocator = ServiceLocator.getInstance();
                    try (InputStream is = serviceLocator.getAppContext()
                                                        .getContentResolver()
                                                        .openInputStream(uri)) {

                        final CoverStorage coverStorage = serviceLocator.getCoverStorage();
                        // copy the data to a temporary file
                        final File file = coverStorage.writeTempFile(is);
                        if (!coverStorage.isAcceptableSize(file)) {
                            return null;
                        }

                        return transform(new Transformation()
                                                 .setSource(file)
                                                 .setScale(true),
                                         file,
                                         NextAction.Done);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                result -> {
                    if (result == null) {
                        setInvalidImage(null);
                    } else {
                        transformationResult.setValue(LiveDataEvent.of(result));
                    }
                },
                this::setInvalidImage);
    }

    /**
     * Process the image picked by the user from the cover-browser.
     *
     * @param imageOwner for which we want to set an image
     * @param fileSpec   the selected image
     *
     * @throws IllegalArgumentException (debug) if the fileSpec is invalid
     */
    void onPictureSelected(@NonNull final ImageOwner imageOwner,
                           @NonNull final String fileSpec) {
        if (fileSpec.isEmpty()) {
            throw new IllegalArgumentException("fileSpec.isEmpty()");
        }

        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    final File file = new File(fileSpec);
                    if (ServiceLocator.getInstance().getCoverStorage().isAcceptableSize(file)) {
                        try {
                            imageOwner.setImage(context, cIdx, file);
                        } catch (@NonNull final StorageException | IOException ignore) {
                            // safe to ignore, we just checked existence...
                        }
                    } else {
                        imageOwner.removeImage(context, cIdx);
                        onInvalidImage.postValue(LiveDataEvent.ofNullable(null));
                    }
                    return (Void) null;
                },
                aVoid -> onReloadImage.setValue(null),
                e -> onInvalidImage.setValue(LiveDataEvent.of(e)));
    }

    void setImage(@NonNull final ImageOwner imageOwner,
                  @NonNull final File file) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    try {
                        imageOwner.setImage(context, cIdx, file);
                    } catch (@NonNull final StorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return (Void) null;
                },
                aVoid -> onReloadImage.setValue(null),
                e -> onError.setValue(LiveDataEvent.of(e)));
    }

    void removeImage(@NonNull final ImageOwner imageOwner) {
        STask.execute(
                ASyncExecutor.STORAGE_WRITES,
                () -> {
                    final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
                    imageOwner.removeImage(context, cIdx);
                    return (Void) null;
                },
                result -> onReloadImage.setValue(null),
                e -> onError.setValue(LiveDataEvent.of(e)));
    }

    void restore(@NonNull final String uuid) {
        STask.execute(
                ASyncExecutor.IMAGES,
                () -> {
                    try {
                        return ServiceLocator.getInstance()
                                             .getCoverStorage()
                                             .restore(uuid, cIdx);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                restored -> {
                    if (restored) {
                        onRestore.setValue(LiveDataEvent.of(true));
                    }
                },
                e -> onError.setValue(LiveDataEvent.of(e)));
    }

    /**
     * Create a temporary File for the given {@link ImageOwner}.
     * <p>
     * If there is a permanent image, we get a <strong>copy</strong>.
     * If there is no image, we get a <strong>new</strong> File object.
     * Either way, the File returned will have a new temporary name.
     *
     * @param context    Current context
     * @param imageOwner for which we want an image
     *
     * @return the File
     *
     * @throws CoverStorageException The images directory is not available
     * @throws IOException           on failure to make a copy of the permanent file
     */
    @WorkerThread
    @NonNull
    private File createSourceTempImageFile(@NonNull final Context context,
                                           @NonNull final ImageOwner imageOwner)
            throws CoverStorageException, IOException {

        // the temp file we'll return
        final File tmpFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();

        // If we have a permanent file, copy it into the temp location
        final Optional<File> uuidFile = imageOwner.getImage(context, cIdx);
        if (uuidFile.isPresent()) {
            FileUtils.copy(uuidFile.get(), tmpFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
            LoggerFactory.getLogger()
                         .e("TAG", new Throwable("createTempImageFile"),
                            "imageOwner.id=" + imageOwner.getId()
                            + "|cIdx=" + cIdx
                            + "|exists=" + tmpFile.exists()
                            + "|length=" + tmpFile.length()
                            + "|file=" + tmpFile.getAbsolutePath()
                         );
        }
        return tmpFile;
    }

    /**
     * Create a temporary destination File for use by an Intent Contract.
     *
     * @return file
     *
     * @throws CoverStorageException The images directory is not available
     * @see #getDestinationImageFile()
     */
    @SuppressLint("WrongThread")
    @AnyThread
    @NonNull
    private File createDestinationTempImageFile() throws CoverStorageException {
        final File tempFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();

        final String path = tempFile.getAbsolutePath();
        // Sigh... we want to be able to run createDestinationTempImageFile
        // both in UIThread and in WorkerThread
        // But to do this we MUST set the method to @AnyThread AND @SuppressLint("WrongThread")
        // AND we can't define the Runnable outside of the if/else...
        // The pox on Android...
        if (Looper.myLooper() == Looper.getMainLooper()) {
            savedStateHandle.set(savedStateTempDestFilePath, path);
        } else {
            new Handler(Looper.getMainLooper()).post(() ->
                    savedStateHandle.set(savedStateTempDestFilePath, path));
        }

        return tempFile;
    }

    /**
     * Get the previously create File.
     *
     * @return file
     *
     * @see #createDestinationTempImageFile()
     */
    @NonNull
    private File getDestinationImageFile() {
        final String path = savedStateHandle.get(savedStateTempDestFilePath);
        savedStateHandle.remove(savedStateTempDestFilePath);
        //noinspection DataFlowIssue
        return new File(path);
    }

    /**
     * Run the transformation and persist the resulting file.
     *
     * @param transformation to run
     * @param destFile       to write
     * @param nextAction     to take / pass on to the result
     *
     * @return TransformationResult
     *
     * @throws CoverStorageException The images directory is not available
     * @throws IOException           on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    private TransformationResult transform(@NonNull final Transformation transformation,
                                           @NonNull final File destFile,
                                           @NonNull final NextAction nextAction)
            throws CoverStorageException, IOException {

        final Optional<Bitmap> optBitmap = transformation.transform();
        if (optBitmap.isPresent()) {
            final Bitmap source = optBitmap.get();
            ServiceLocator.getInstance().getCoverStorage().persist(source, destFile);

            return new TransformationResult(destFile, nextAction);
        }

        return new TransformationResult(null, NextAction.Done);
    }

    public static final class Factory
            implements ViewModelProvider.Factory {

        /** Image index we're handling. */
        @IntRange(from = 0, to = 3)
        private final int cIdx;

        private Factory(final int cIdx) {
            this.cIdx = cIdx;
        }

        /**
         * Constructor.
         *
         * @param owner hosting Fragment or Activity
         * @param cIdx  0..n image index
         *
         * @return registered ViewModel
         */
        @NonNull
        public static ImageHandlerViewModel create(@NonNull final ViewModelStoreOwner owner,
                                                   final int cIdx) {
            final CreationExtras extras = ((HasDefaultViewModelProviderFactory) owner)
                    .getDefaultViewModelCreationExtras();

            return new ViewModelProvider(owner.getViewModelStore(), new Factory(cIdx), extras)
                    .get(String.valueOf(cIdx), ImageHandlerViewModel.class);
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull final Class<T> modelClass,
                                              @NonNull final CreationExtras extras) {
            final SavedStateHandle handle = SavedStateHandleSupport.createSavedStateHandle(extras);

            if (modelClass.isAssignableFrom(ImageHandlerViewModel.class)) {
                return (T) new ImageHandlerViewModel(handle, cIdx);
            }

            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}
