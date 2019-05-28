package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public abstract class BRBaseActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    /**
     * Create the file lister fragment.
     *
     * @param defaultFilename for the user input field.
     */
    void createFileBrowser(@NonNull final String defaultFilename) {
        // use lastBackupFile as the root directory for the browser.
        String lastBackupFile =
                App.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                         StorageUtils.getSharedStorage().getAbsolutePath());
        File root = new File(Objects.requireNonNull(lastBackupFile));

        FileChooserFragment frag = FileChooserFragment.newInstance(root, defaultFilename);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.main_fragment, frag, FileChooserFragment.TAG)
                .commit();
    }
}
