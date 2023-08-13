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

package com.hardbacknutter.nevertoomanybooks.core.storage;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedFileServiceTest {

    private static final String FILE = "BackupServiceTestFile.txt";
    private static final String DIR = "BackupServiceTestDir";

    @NonNull
    protected static File getTmpDir() {
        //noinspection DataFlowIssue
        return new File(System.getProperty("java.io.tmpdir"));
    }

    @BeforeEach
    void setup() {
        File dir = getTmpDir();
        makeEmpty(dir);

        dir = new File(getTmpDir(), DIR);
        makeEmpty(dir);
        dir.delete();
    }

    private static void makeEmpty(@NonNull final File dir) {
        if (dir.exists()) {
            List<File> files = FileUtils.collectFiles(dir, pathname
                    -> pathname.getName().startsWith(FILE));

            files.forEach(File::delete);

            files = FileUtils.collectFiles(dir, pathname
                    -> pathname.getName().startsWith(FILE));
            assertTrue(files.isEmpty());
        }
    }

    @Test
    void subDir()
            throws IOException {
        final File backupDir = new File(getTmpDir(), DIR);
        backupDir.mkdir();
        assertTrue(backupDir.exists());

        final VersionedFileService versionedFileService = new VersionedFileService(backupDir, 5);

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, FILE);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(backupDir, FILE + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(0, files.size());
        System.out.println(files);

        files = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and previous one to ".2"
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(backupDir, FILE + ".1").exists());
        assertTrue(new File(backupDir, FILE + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(0, files.size());
        System.out.println(files);

        files = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(2, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, rename ".2" to ".1"
        versionedFileService.restore(file);

        assertTrue(new File(dir, FILE).exists());
        assertTrue(new File(backupDir, FILE + ".1").exists());
        assertFalse(new File(backupDir, FILE + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);

        files = FileUtils.collectFiles(backupDir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());

        System.out.println(files);


        backupDir.delete();
    }

    @Test
    void sameDir()
            throws IOException {
        final VersionedFileService versionedFileService = new VersionedFileService(5);

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, FILE);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(dir, FILE + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and previous one to ".2"
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(dir, FILE + ".1").exists());
        assertTrue(new File(dir, FILE + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(2, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, rename ".2" to ".1"
        versionedFileService.restore(file);

        assertTrue(new File(dir, FILE).exists());
        assertTrue(new File(dir, FILE + ".1").exists());
        assertFalse(new File(dir, FILE + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(2, files.size());
        System.out.println(files);
    }

    @Test
    void sameDir1()
            throws IOException {
        final VersionedFileService versionedFileService = new VersionedFileService(1);

        final File dir = getTmpDir();
        List<File> files;
        final File file = new File(dir, FILE);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(dir, FILE + ".1").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // rename to ".1"; and delete previous one
        versionedFileService.save(file);

        assertFalse(new File(dir, FILE).exists());
        assertTrue(new File(dir, FILE + ".1").exists());
        assertFalse(new File(dir, FILE + ".2").exists());
        assertTrue(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);

        // Create new file, no suffix.
        assertTrue(file.createNewFile());

        // delete the original, rename ".1" to original, no ".1" left
        versionedFileService.restore(file);

        assertTrue(new File(dir, FILE).exists());
        assertFalse(new File(dir, FILE + ".1").exists());
        assertFalse(new File(dir, FILE + ".2").exists());
        assertFalse(versionedFileService.hasBackup(new File(dir, FILE)));

        files = FileUtils.collectFiles(dir, pathname
                -> pathname.getName().startsWith(FILE));
        assertEquals(1, files.size());
        System.out.println(files);
    }
}