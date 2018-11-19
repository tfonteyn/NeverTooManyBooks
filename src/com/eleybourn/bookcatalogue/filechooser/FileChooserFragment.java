/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.filechooser;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.filechooser.FileLister.FileListerListener;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter.ViewProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Fragment to display a simple directory/file browser.
 *
 * @author pjw
 */
public class FileChooserFragment extends Fragment implements FileListerListener {

    public static final String TAG = "FileChooserFragment";

    private static final String BKEY_ROOT_PATH = "rootPath";
    private static final String BKEY_LIST = "list";

    private File mRootPath;

    private EditText mFilenameField;
    private TextView mPathField;

    // Create an empty one in case we are rotated before generated.
    @Nullable
    private ArrayList<FileDetails> mList = new ArrayList<>();

    /** Create a new chooser fragment */
    @NonNull
    public static FileChooserFragment newInstance(final @NonNull File root, final @NonNull String fileName) {
        String path;
        // Turn the passed File into a directory
        if (root.isDirectory()) {
            path = root.getAbsolutePath();
        } else {
            path = root.getParent();
        }

        // Build the fragment and save the details
        Bundle args = new Bundle();
        args.putString(BKEY_ROOT_PATH, path);
        args.putString(UniqueId.BKEY_FILE_SPEC, fileName);
        FileChooserFragment frag = new FileChooserFragment();
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnPathChangedListener))
            throw new RTE.MustImplementException(context, OnPathChangedListener.class);
    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_chooser, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this);
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mFilenameField = getView().findViewById(R.id.file_name);
        mPathField = getView().findViewById(R.id.path);

        if (savedInstanceState != null) {
            mRootPath = new File(Objects.requireNonNull(savedInstanceState.getString(BKEY_ROOT_PATH)));
            ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_LIST);
            Objects.requireNonNull(list);
            onGotFileList(mRootPath, list);
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);

            mRootPath = new File(Objects.requireNonNull(args.getString(BKEY_ROOT_PATH)));
            mFilenameField.setText(args.getString(UniqueId.BKEY_FILE_SPEC));
            mPathField.setText(mRootPath.getAbsolutePath());
            tellActivityPathChanged();
        }

        // 'up' directory
        getView().findViewById(R.id.row_path_up).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUp();
            }
        });
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Convenience method to tell our activity the path has changed.
     */
    private void tellActivityPathChanged() {
        ((OnPathChangedListener) requireActivity()).onPathChanged(mRootPath);
    }

    /**
     * Handle the 'Up' action
     */
    private void handleUp() {
        String parent = mRootPath.getParent();
        if (parent == null) {
            //Snackbar.make(this.getView(), R.string.no_parent_directory_found, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(requireActivity(), R.string.warning_no_parent_directory_found);
            return;
        }
        mRootPath = new File(parent);

        tellActivityPathChanged();
    }

    /**
     * Save our root path and list
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putString(BKEY_ROOT_PATH, mRootPath.getAbsolutePath());
        outState.putParcelableArrayList(BKEY_LIST, mList);
        super.onSaveInstanceState(outState);
    }

    /**
     * Accessor
     *
     * @return File
     */
    @NonNull
    public File getSelectedFile() {
        return new File(mRootPath.getAbsolutePath() + File.separator + mFilenameField.getText().toString().trim());
    }

    /**
     * Display the list
     *
     * @param root Root directory
     * @param list List of FileDetails
     */
    @Override
    public void onGotFileList(final @NonNull File root, final @NonNull ArrayList<FileDetails> list) {
        mRootPath = root;
        mPathField.setText(mRootPath.getAbsolutePath());

        // Setup and display the list
        mList = list;
        // We pass no view ID since each item can provide the view id
        FileDetailsAdapter adapter = new FileDetailsAdapter(requireActivity(), mList);
        //noinspection ConstantConditions
        ListView lv = getView().findViewById(android.R.id.list);
        lv.setAdapter(adapter);
    }

    /**
     * Interface that the containing Activity must implement. Called when user changes path.
     *
     * @author pjw
     */
    public interface OnPathChangedListener {
        void onPathChanged(File root);
    }

    /** Interface for details of files in current directory */
    public interface FileDetails extends ViewProvider, Parcelable {
        /** Get the underlying File object */
        @NonNull
        File getFile();

        /** Called to fill in the details of this object in the View provided by the ViewProvider implementation */
        void onGetView(final @NonNull View convertView, final @NonNull Context context);
    }

    /**
     * List Adapter for FileDetails objects
     *
     * @author pjw
     */
    protected class FileDetailsAdapter extends SimpleListAdapter<FileDetails> implements SimpleListAdapterRowActionListener<FileDetails>{

        FileDetailsAdapter(final @NonNull Context context, final @NonNull ArrayList<FileDetails> items) {
            super(context, 0, items);
        }

        @Override
        public void onGetView(final @NonNull View convertView, final @NonNull FileDetails item) {
            item.onGetView(convertView, requireActivity());
        }

        @Override
        public void onRowClick(final @NonNull View v, final @Nullable FileDetails item, final int position) {
            if (item != null) {
                if (item.getFile().isDirectory()) {
                    mRootPath = item.getFile();
                    tellActivityPathChanged();
                } else {
                    mFilenameField.setText(item.getFile().getName());
                }
            }
        }
    }

}
