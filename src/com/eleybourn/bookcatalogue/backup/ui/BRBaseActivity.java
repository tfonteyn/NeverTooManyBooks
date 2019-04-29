package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.tasks.OnTaskFinishedListener;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public abstract class BRBaseActivity
        extends BaseActivity
        implements OnTaskFinishedListener {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_file_chooser_base;
    }

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Handle 'Cancel' button
        findViewById(R.id.cancel).setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    /**
     * Create the file lister fragment.
     *
     * @param defaultFilename for the user input field.
     */
    void createFileBrowser(final String defaultFilename) {
        // use lastBackupFile as the root directory for the browser.
        String lastBackupFile =
                App.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                         StorageUtils.getSharedStorage().getAbsolutePath());
        File root = new File(Objects.requireNonNull(lastBackupFile));

        FileChooserFragment frag = FileChooserFragment.newInstance(root, defaultFilename);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.browser_fragment, frag, FileChooserFragment.TAG)
                .commit();
    }
}
