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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveReader;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;

/**
 * A test helper to copy raw test resources to the test environment.
 * <p>
 * To load the raw test resource, we MUST use the instrumentation context
 * and NOT the application (aka target) context:
 * {@code InstrumentationRegistry.getInstrumentation().getContext()}
 * <p>
 * but to write the files, we MUST use the application context obviously...
 */
public class DbPrep {
    public static final String[] COVER = {"0.jpg", "1.jpg"};

    private static final int[] coverResId = {
            com.hardbacknutter.nevertoomanybooks.test.R.raw.cover1,
            com.hardbacknutter.nevertoomanybooks.test.R.raw.cover2
    };
    private static final int BUFFER_SIZE = 32768;

    /**
     * Copy a file from the raw test resources to the temp picture directory.
     *
     * @param cIdx 0..n image index
     *
     * @return the created file
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    public File getFile(final int cIdx)
            throws StorageException, IOException {

        final File tempDir = ServiceLocator.getInstance().getCoverStorage().getTempDir();
        final File file = new File(tempDir, COVER[cIdx]);
        final int resId = coverResId[cIdx];

        final Context ic = InstrumentationRegistry.getInstrumentation().getContext();
        try (final InputStream is = ic.getResources().openRawResource(resId);
             final OutputStream os = new FileOutputStream(file)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                os.write(buffer, 0, nRead);
            }
            os.flush();
        }
        return file;
    }

    public long maybeInstallTestData(@NonNull final Context context)
            throws StorageException {
        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();

        final int count = bookDao.count();
        if (count > 10) {
            return count;
        }

        try {
            installTestData(context);
        } catch (IOException | DataReaderException e) {
            throw new RuntimeException(e);
        }

        return bookDao.count();
    }

    private void installTestData(@NonNull final Context context)
            throws IOException, DataReaderException, StorageException {

        final File file = new File(context.getCacheDir(), "testdata.json");
        final int resId = com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata;

        final Context ic = InstrumentationRegistry.getInstrumentation().getContext();
        try (final InputStream is = ic.getResources().openRawResource(resId);
             final OutputStream os = new FileOutputStream(file)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                os.write(buffer, 0, nRead);
            }
            os.flush();
        }

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        final ImportHelper helper = new ImportHelper(context, locale, Uri.fromFile(file));
        helper.setUpdateOption(DataReader.Updates.Overwrite);

        try (final JsonArchiveReader reader = new JsonArchiveReader(locale,
                                                                    helper.getUri(),
                                                                    helper.getUpdateOption(),
                                                                    helper.getRecordTypes())) {
            reader.validate(context);
            reader.read(context, new TestProgressListener("installTestData"));
        }
    }
}
