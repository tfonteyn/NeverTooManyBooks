package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;

public class AdminHostsFragment extends Fragment {

    private EditText amazon_url;
    private EditText google_url;
    private EditText isfdb_url;

    private boolean isCreated;


    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_hosts, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        amazon_url = getView().findViewById(R.id.amazon_url);
        amazon_url.setText(AmazonManager.getBaseURL());

        google_url = getView().findViewById(R.id.google_url);
        google_url.setText(GoogleBooksManager.getBaseURL());

        isfdb_url = getView().findViewById(R.id.isfdb_url);
        isfdb_url.setText(ISFDBManager.getBaseURL());

        isCreated = true;
    }

    public void saveState() {
        if (isCreated) {
            //TODO: add sanity checks
            String newAmazon = amazon_url.getText().toString().trim();
            if (!newAmazon.isEmpty()) {
                AmazonManager.setBaseURL(newAmazon);
            }
            String newGoogle = google_url.getText().toString().trim();
            if (!newGoogle.isEmpty()) {
                GoogleBooksManager.setBaseURL(newGoogle);
            }
            String newIsfdb = isfdb_url.getText().toString().trim();
            if (!newIsfdb.isEmpty()) {
                ISFDBManager.setBaseURL(newIsfdb);
            }
        }
    }
}
