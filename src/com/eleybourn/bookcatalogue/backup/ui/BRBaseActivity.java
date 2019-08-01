package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public abstract class BRBaseActivity
        extends BaseActivity {

    /** Fragment manager tag. */
    private static final String TAG = "BRBaseActivity";

    private static final String BKEY_ROOT_PATH = TAG + ":root";
    private static final String BKEY_FILE_LIST = TAG + ":list";
    @NonNull
    private final ArrayList<FileDetails> mFileDetails = new ArrayList<>();
    File mRootDir;
    RecyclerView mListView;

    protected ProgressDialogFragment mProgressDialog;

    private FileDetailsAdapter mAdapter;

    private TextView mCurrentFolderView;

    private final TaskListener<ArrayList<FileDetails>> mFileListerTaskListener =
            new TaskListener<ArrayList<FileDetails>>() {
                @Override
                public void onTaskFinished(@NonNull final TaskFinishedMessage<ArrayList<FileDetails>> message) {
                    //noinspection SwitchStatementWithTooFewBranches
                    switch (message.taskId) {
                        case R.id.TASK_ID_FILE_LISTER:
                            onGotFileList(message.result);
                            break;

                        default:
                            Logger.warnWithStackTrace(this, "Unknown taskId=" + message.taskId);
                            break;
                    }
                }
            };

    /** User clicks on the 'up' button. */
    private final View.OnClickListener onPathUpClickListener = view -> {
        String parent = mRootDir.getParent();
        if (parent == null) {
            UserMessage.show(view, R.string.warning_no_parent_directory_found);
            return;
        }
        onPathChanged(new File(parent));
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_backup_restore;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCurrentFolderView = findViewById(R.id.current_folder);
        mCurrentFolderView.setOnClickListener(onPathUpClickListener);
        findViewById(R.id.btn_path_up).setOnClickListener(onPathUpClickListener);

        mListView = findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(linearLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        mAdapter = new FileDetailsAdapter(this);
        mListView.setAdapter(mAdapter);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                                             | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    void setupList(@Nullable final Bundle args) {

        // populate with the existing content if we have it
        if (args != null) {
            mRootDir = new File(Objects.requireNonNull(args.getString(BKEY_ROOT_PATH)));
            ArrayList<FileDetails> list = Objects.requireNonNull(
                    args.getParcelableArrayList(BKEY_FILE_LIST));
            onGotFileList(list);

        } else {
            // use lastBackupFile as the root directory for the browser.
            String lastBackupFile =
                    App.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                             StorageUtils.getSharedStorage().getAbsolutePath());
            File rootDir = new File(Objects.requireNonNull(lastBackupFile));
            // Turn the File into a directory
            if (rootDir.isDirectory()) {
                rootDir = new File(rootDir.getAbsolutePath());
            } else {
                rootDir = new File(rootDir.getParent());
            }
            if (!rootDir.exists()) {
                // fall back to default
                rootDir = StorageUtils.getSharedStorage();
            }
            mCurrentFolderView.setText(rootDir.getAbsolutePath());

            // start the task to get the content
            onPathChanged(rootDir);
        }
    }

    /**
     * A new root directory is selected.
     * <p>
     * Rebuild the file list in background.
     *
     * @param rootDir the new root
     */
    private void onPathChanged(@NonNull final File rootDir) {
        if (rootDir.isDirectory()) {
            mRootDir = rootDir;
            new FileListerTask(mRootDir, mFileListerTaskListener).execute();
        }
    }

    /**
     * The user selected a file.
     *
     * @param file selected
     */
    protected abstract void onFileSelected(@NonNull File file);

    /**
     * Display the list.
     * Can be called from the background task, or from onCreate after a re-create.
     *
     * @param fileDetails List of FileDetails
     */
    private void onGotFileList(@NonNull final ArrayList<FileDetails> fileDetails) {
        mCurrentFolderView.setText(mRootDir.getAbsolutePath());

        mFileDetails.clear();
        mFileDetails.addAll(fileDetails);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Save our root path and list.
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_ROOT_PATH, mRootDir.getAbsolutePath());
        outState.putParcelableArrayList(BKEY_FILE_LIST, mFileDetails);
    }

    protected void onTaskProgressMessage(final TaskListener.TaskProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    protected void onTaskCancelledMessage(final Integer taskId) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        UserMessage.show(this, R.string.progress_end_cancelled);
    }

    /**
     * Interface for details of mFileDetails in current directory.
     */
    public interface FileDetails
            extends Parcelable {

        /** Get the underlying File object. */
        @NonNull
        File getFile();

        void onBindViewHolder(@NonNull Holder holder,
                              @NonNull Context context);
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        final TextView filenameView;
        final ImageView imageView;

        final androidx.constraintlayout.widget.Group fileDetails;
        final TextView fileContentView;
        final TextView dateView;
        final TextView sizeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            filenameView = itemView.findViewById(R.id.filename);
            imageView = itemView.findViewById(R.id.icon);

            fileDetails = itemView.findViewById(R.id.file_details);
            fileContentView = itemView.findViewById(R.id.file_content);
            dateView = itemView.findViewById(R.id.date);
            sizeView = itemView.findViewById(R.id.size);
        }
    }

    /**
     * List Adapter for FileDetails objects.
     */
    class FileDetailsAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;


        /**
         * Constructor.
         *
         * @param context Current context
         */
        FileDetailsAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = mInflater.inflate(R.layout.row_file_chooser, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            FileDetails item = mFileDetails.get(position);
            item.onBindViewHolder(holder, mInflater.getContext());

            File file = item.getFile();

            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    // descend into the selected directory
                    onPathChanged(file);
                } else {
                    onFileSelected(file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mFileDetails.size();
        }
    }
}
