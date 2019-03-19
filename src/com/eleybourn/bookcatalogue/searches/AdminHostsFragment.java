package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;

/**
 * Allows editing the host url for some of the search sites.
 * <p>
 * Note: {@link AmazonManager} uses a proxy;
 * but the "search amazon" menu links *do* use the url setup here.
 * <p>
 * {@link ISFDBManager} is currently only a single url, but the site people make the
 * source/data freely available, so someone could setup a new site/mirror.
 * Plumbing all present, but visibility set to 'gone' in the xml layout.
 */
public class AdminHostsFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = AdminHostsFragment.class.getSimpleName();

    private EditText amazon_url;
    private EditText google_url;
    private EditText isfdb_url;

    private boolean isCreated;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_hosts, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = requireView();

        amazon_url = view.findViewById(R.id.amazon_url);
        amazon_url.setText(AmazonManager.getBaseURL());

        google_url = view.findViewById(R.id.google_url);
        google_url.setText(GoogleBooksManager.getBaseURL());

        isfdb_url = view.findViewById(R.id.isfdb_url);
        isfdb_url.setText(ISFDBManager.getBaseURL());

        isCreated = true;
    }

    public boolean isDirty() {
        return !(AmazonManager.getBaseURL().equals(amazon_url.getText().toString().trim())
                && GoogleBooksManager.getBaseURL().equals(google_url.getText().toString().trim())
                && ISFDBManager.getBaseURL().equals(isfdb_url.getText().toString().trim()));
    }

    //TODO: add sanity checks on the url's
    void saveSettings() {
        if (isCreated && isDirty()) {
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
