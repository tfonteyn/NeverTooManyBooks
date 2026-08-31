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

package com.hardbacknutter.nevertoomanybooks;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.VersionedFileService;
import com.hardbacknutter.nevertoomanybooks.covers.ImageStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ResultOfMethodCallIgnored")
class VersionedFileServiceTest
        extends BaseDBTest {

    private static final String TAG = "VersionedFileServiceTst";

    private static final String FILE_PREFIX = "BackupService";

    /** Subdirectory created under the tmp dir. */
    private static final String SUBDIR = "TestDir";

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        File dir = getTmpDir();
        makeEmpty(dir);

        dir = new File(getTmpDir(), SUBDIR);
        makeEmpty(dir);
        dir.delete();
    }

    @NonNull
    private File getTmpDir()
            throws ImageStorageException {
        return ServiceLocator.getInstance().getCoverStorage().getTempDir();
    }

    private void makeEmpty(@NonNull final File dir) {
        if (dir.exists()) {
            List<File> files = FileUtils.collectFiles(dir, pathname
                    -> pathname.getName().startsWith(FILE_PREFIX));

            files.forEach(File::delete);

            files = FileUtils.collectFiles(dir, pathname
                    -> pathname.getName().startsWith(FILE_PREFIX));
            assertTrue(files.isEmpty());
        }
    }

    @Test
    void subDir()
            throws IOException, ImageStorageException {
        final File backupDir = new File(getTmpDir(), SUBDIR);
        backupDir.mkdir();
        assertTrue(backupDir.exists());

        final VersionedFileService versionedFileService = new VersionedFileService(backupDir, 5);

        final String filename = FILE_PREFIX + "_subDir";

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, filename);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(backupDir, filename + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(0, files.size());
        Log.d(TAG, files.toString());

        files = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and previous one to ".2"
        versionedFileService.save(file);

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(backupDir, filename + ".1").exists());
        assertTrue(new File(backupDir, filename + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(0, files.size());
        Log.d(TAG, files.toString());

        files = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(2, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, rename ".2" to ".1"
        final boolean restored = versionedFileService.restore(file);
        assertTrue(restored);

        assertTrue(new File(dir, filename).exists());
        assertTrue(new File(backupDir, filename + ".1").exists());
        assertFalse(new File(backupDir, filename + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        List<File> files2 = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files2.size());
        Log.d(TAG, files2.toString());

        files2 = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files2.size());

        Log.d(TAG, files2.toString());

        backupDir.delete();
    }

    @Test
    void sameDir5()
            throws IOException, ImageStorageException {
        final VersionedFileService versionedFileService = new VersionedFileService(5);

        final String filename = FILE_PREFIX + "_sameDir5";

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, filename);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        Log.d(TAG, "Directory content after 1st save():");
        File[] dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(dir, filename + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and previous one to ".2"
        versionedFileService.save(file);

        Log.d(TAG, "Directory content after 2nd save():");
        dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(dir, filename + ".1").exists());
        assertTrue(new File(dir, filename + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(2, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, rename ".2" to ".1"
        final boolean restored = versionedFileService.restore(file);

        Log.d(TAG, "Directory content after restore():");
        dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertTrue(restored);

        assertTrue(new File(dir, filename).exists());
        assertTrue(new File(dir, filename + ".1").exists());
        assertFalse(new File(dir, filename + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        final List<File> files2 = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(2, files2.size());
        Log.d(TAG, files2.toString());
    }

    @Test
    void sameDir1()
            throws IOException, ImageStorageException {
        final VersionedFileService versionedFileService = new VersionedFileService(1);

        final String filename = FILE_PREFIX + "_sameDir1";

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, filename);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        Log.d(TAG, "Directory content after 1st save():");
        File[] dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(dir, filename + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and delete previous one
        versionedFileService.save(file);

        Log.d(TAG, "Directory content after 2nd save():");
        dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertFalse(new File(dir, filename).exists());
        assertTrue(new File(dir, filename + ".1").exists());
        assertFalse(new File(dir, filename + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, filename)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files.size());
        Log.d(TAG, files.toString());

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, no ".1" left
        final boolean restored = versionedFileService.restore(file);

        Log.d(TAG, "Directory content after restore():");
        dirList = dir.listFiles();
        assertNotNull(dirList);
        for (final File f : dirList) {
            Log.d(TAG, " - " + f.getName());
        }

        assertTrue(restored);

        assertTrue(new File(dir, filename).exists());
        assertFalse(new File(dir, filename + ".1").exists());
        assertFalse(new File(dir, filename + ".2").exists());
        assertFalse(versionedFileService.hasBackup(new File(dir, filename)));

        final List<File> files2 = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(filename));
        assertEquals(1, files2.size());
        Log.d(TAG, files2.toString());
    }
}