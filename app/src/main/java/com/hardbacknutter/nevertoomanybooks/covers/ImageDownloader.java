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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.util.Base64;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.DiskFullException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;

public class ImageDownloader {

    /** Log tag. */
    private static final String TAG = "ImageDownloader";

    /** The prefix an embedded image url would have. */
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    /**
     * DEBUG HACK... when running in JUnit, this variable is set to 'true'
     * by the test code.
     * <p>
     * When running as a JUnit test, the file.renameTo done during the
     * {@link ImageUtils#copy(InputStream, File)} operation will fail.
     * As that is independent from the JUnit test/purpose, we will fake success here.
     */
    public static boolean IGNORE_RENAME_FAILURE;

    @NonNull
    private final FutureHttpGet<File> futureHttpGet;

    public ImageDownloader(@NonNull final FutureHttpGet<File> futureHttpGet) {
        this.futureHttpGet = futureHttpGet;
    }

    /**
     * Get a temporary file.
     *
     * @param source of the image (normally a SearchEngine specific code)
     * @param bookId (optional) either the native id, or the isbn
     * @param cIdx   0..n image index
     * @param size   (optional) size of the image
     *               Omitted if not set
     *
     * @return file
     *
     * @throws StorageException The covers directory is not available
     */
    @AnyThread
    @NonNull
    public File getTempFile(@NonNull final String source,
                            @Nullable final String bookId,
                            @IntRange(from = 0, to = 1) final int cIdx,
                            @Nullable final Size size)
            throws StorageException {

        // keep all "_" even for empty parts. Easier to parse the name if needed.
        final String filename = System.currentTimeMillis()
                                + "_" + source
                                + "_" + (bookId != null && !bookId.isEmpty() ? bookId : "")
                                + "_" + cIdx
                                + "_" + (size != null ? size : "")
                                + ".jpg";

        return new File(CoverDir.getTemp(ServiceLocator.getAppContext()), filename);
    }

    /**
     * Given a URL, get an image and save to the given file.
     * Must be called from a background task.
     *
     * @param url         Image file URL
     * @param destination file to write to
     *
     * @return Downloaded File, or {@code null} on failure
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    @WorkerThread
    public Optional<File> fetch(@NonNull final String url,
                                @NonNull final File destination)
            throws StorageException {
        @Nullable
        final File savedFile;

        try {
            if (url.startsWith(DATA_IMAGE_JPEG_BASE_64)) {
                try (OutputStream os = new FileOutputStream(destination)) {
                    final byte[] image = Base64
                            .decode(url.substring(DATA_IMAGE_JPEG_BASE_64.length())
                                       .getBytes(StandardCharsets.UTF_8), 0);
                    os.write(image);
                }
                savedFile = destination;
            } else {
                savedFile = futureHttpGet.get(url, request -> {
                    try (BufferedInputStream bis = new BufferedInputStream(
                            request.getInputStream())) {
                        return ImageUtils.copy(bis, destination);
                    } catch (@NonNull final StorageException e) {
                        throw new UncheckedStorageException(e);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            // too small ? reject
            // too big: N/A as we assume a picture from a website is already a good size
            if (ImageUtils.isAcceptableSize(savedFile)) {
                return Optional.of(savedFile);
            }
            return Optional.empty();

        } catch (@NonNull final IOException e) {
            FileUtils.delete(destination);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS || IGNORE_RENAME_FAILURE) {
                LoggerFactory.getLogger().e(TAG, e, "saveImage");

                if (IGNORE_RENAME_FAILURE) {
                    return Optional.of(destination);
                }
            }

            // we swallow IOExceptions, **EXCEPT** when the disk is full.
            if (DiskFullException.isDiskFull(e)) {
                //noinspection ConstantConditions
                throw new DiskFullException(e.getCause());
            }
            return Optional.empty();
        }
    }

    public void cancel() {
        synchronized (futureHttpGet) {
            futureHttpGet.cancel();
        }
    }
}
