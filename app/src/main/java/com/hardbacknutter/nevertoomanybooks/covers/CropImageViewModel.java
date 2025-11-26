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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CropImageContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.STask;

@SuppressWarnings("WeakerAccess")
public class CropImageViewModel
        extends ViewModel {

    /** Refuse work if we don't have this minimal space free. */
    private static final long MINIMUM_STORAGE_SPACE = 100_000L;

    private final MutableLiveData<LiveDataEvent<Bitmap>> onBitmap =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Intent>> onSaved =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Throwable>> onError =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Void>> onInsufficientStorage =
            new MutableLiveData<>();

    // do NOT delete the destination file in case source and destination was the same file
    private String destinationPath;

    @NonNull
    LiveData<LiveDataEvent<Bitmap>> onBitmap() {
        return onBitmap;
    }

    @NonNull
    LiveData<LiveDataEvent<Intent>> onSaved() {
        return onSaved;
    }

    @NonNull
    LiveData<LiveDataEvent<Void>> onInsufficientStorage() {
        return onInsufficientStorage;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onError() {
        return onError;
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        destinationPath = Objects.requireNonNull(args.getString(
                CropImageContract.BKEY_DESTINATION), CropImageContract.BKEY_DESTINATION);

        final String srcPath = Objects.requireNonNull(args.getString(
                CropImageContract.BKEY_SOURCE), CropImageContract.BKEY_SOURCE);

        STask.execute(
                ASyncExecutor.PARALLEL,
                () -> {
                    try {
                        final File coverDir = ServiceLocator.getInstance().getCoverStorage()
                                                            .getDir();
                        return FileUtils.getFreeSpace(coverDir) > MINIMUM_STORAGE_SPACE;
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                hasSpace -> {
                    if (!hasSpace) {
                        onInsufficientStorage.setValue(LiveDataEvent.ofNullable(null));
                        return;
                    }
                    // We've got storage space... prep the bitmap
                    STask.execute(
                            ASyncExecutor.PARALLEL,
                            () -> {
                                try (InputStream is = new FileInputStream(srcPath)) {
                                    return BitmapFactory.decodeStream(is);
                                } catch (@NonNull final IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            },
                            bitmap -> onBitmap.setValue(LiveDataEvent.of(bitmap)),
                            e -> onError.setValue(LiveDataEvent.of(e)));
                },
                e -> onError.setValue(LiveDataEvent.of(e)));
    }

    /**
     * Save the bitmap.
     *
     * @param bitmap to save
     */
    void save(@NonNull final Bitmap bitmap) {
        STask.execute(
                ASyncExecutor.SERIAL,
                () -> {
                    final File destination = new File(destinationPath);
                    try {
                        ServiceLocator.getInstance().getCoverStorage()
                                      .persist(bitmap, destination);
                    } catch (@NonNull final CoverStorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return CropImageContract.createResult(destinationPath);
                },
                resultIntent -> onSaved.setValue(LiveDataEvent.of(resultIntent)),
                e -> onError.setValue(LiveDataEvent.of(e))
        );
    }
}
