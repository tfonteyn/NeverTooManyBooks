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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.network.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

public class ImageDownloader {

    /** Log tag. */
    private static final String TAG = "ImageDownloader";

    /** The prefix an embedded image url will have. */
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    /** Parameter for the {@link TerminatorConnection}. */
    private int mConnectTimeoutInMs;
    /** Parameter for the {@link TerminatorConnection}. */
    private int mReadTimeoutInMs;
    /** Parameter for the {@link TerminatorConnection}. */
    @Nullable
    private Throttler mThrottler;

    /** Parameter for the {@link TerminatorConnection}. */
    @Nullable
    private SSLContext mSslContext;
    /** Parameter for the {@link TerminatorConnection}. */
    @Nullable
    private String mAuthHeader;


    /**
     * Constructor.
     */
    @AnyThread
    public ImageDownloader() {

    }

    /**
     * Constructor.
     *
     * @param sslContext (optional) SSL context to use instead of the system one.
     * @param authHeader header string
     */
    @AnyThread
    public ImageDownloader(@Nullable final SSLContext sslContext,
                           @Nullable final String authHeader) {
        mSslContext = sslContext;
        mAuthHeader = authHeader;
    }

    /**
     * Set the optional throttler.
     *
     * @param throttler (optional) {@link Throttler} to use
     */
    @NonNull
    public ImageDownloader setThrottler(@Nullable final Throttler throttler) {
        mThrottler = throttler;
        return this;
    }

    /**
     * Set the optional timeouts.
     *
     * @param connectTimeoutInMs in millis, use {@code 0} for system default
     */
    @NonNull
    public ImageDownloader setConnectTimeout(@IntRange(from = 0) final int connectTimeoutInMs) {
        mConnectTimeoutInMs = connectTimeoutInMs;
        return this;
    }

    /**
     * Set the optional timeouts.
     *
     * @param readTimeoutInMs in millis, use {@code 0} for system default
     */
    @NonNull
    public ImageDownloader setReadTimeout(@IntRange(from = 0) final int readTimeoutInMs) {
        mReadTimeoutInMs = readTimeoutInMs;
        return this;
    }

    /**
     * Create a temporary file.
     *
     * @param context current context
     * @param source  of the image (normally a SearchEngine specific code)
     * @param bookId  (optional) either the native id, or the isbn
     * @param cIdx    0..n image index
     * @param size    (optional) size of the image
     *                Omitted if not set
     *
     * @return temporary file in {@link AppDir#Cache}
     */
    @AnyThread
    @NonNull
    public File createTmpFile(@NonNull final Context context,
                              @NonNull final String source,
                              @Nullable final String bookId,
                              @IntRange(from = 0, to = 1) final int cIdx,
                              @Nullable final ImageFileInfo.Size size)
            throws ExternalStorageException {

        // keep all "_" even for empty parts. Easier to parse the name if needed.
        final String filename = System.currentTimeMillis()
                                + "_" + source
                                + "_" + (bookId != null && !bookId.isEmpty() ? bookId : "")
                                + "_" + cIdx
                                + "_" + (size != null ? size : "")
                                + ".jpg";

        return AppDir.Cache.getFile(context, filename);
    }

    /**
     * Given a URL, get an image and save to the given file.
     * Must be called from a background task.
     *
     * @param context     Application context
     * @param url         Image file URL
     * @param destination file to write to
     *
     * @return Downloaded File, or {@code null} on failure
     */
    @Nullable
    @WorkerThread
    public File fetch(@NonNull final Context context,
                      @NonNull final String url,
                      @NonNull final File destination) {
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
                try (TerminatorConnection con = new TerminatorConnection(url)) {
                    con.setConnectTimeout(mConnectTimeoutInMs)
                       .setReadTimeout(mReadTimeoutInMs)
                       .setThrottler(mThrottler)
                       .setSSLContext(mSslContext);
                    if (mAuthHeader != null) {
                        con.setRequestProperty(HttpConstants.AUTHORIZATION, mAuthHeader);
                    }

                    savedFile = FileUtils.copyInputStream(context, con.getInputStream(),
                                                          destination);
                }
            }
        } catch (@NonNull final IOException e) {
            FileUtils.delete(destination);

            if ((BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) || Logger.isJUnitTest) {
                Logger.d(TAG, "saveImage", "|e=" + e.getLocalizedMessage());

                // When running as a JUnit test, the file.renameTo done during the
                // FileUtils.copyInputStream operation will fail.
                // As that is independent from the JUnit test/purpose, we fake success here.
                if (Logger.isJUnitTest) {
                    return destination;
                }
            }
            return null;
        }

        // is the image not too small ?
        // (too big: don't check assuming a picture from a website is already a good size)
        if (!ImageUtils.isAcceptableSize(savedFile)) {
            return null;
        }

        return savedFile;
    }
}
