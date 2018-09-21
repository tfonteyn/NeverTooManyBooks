package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;

public class SearchAdminHosts extends BookCatalogueActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search_hosts;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.search_internet);

        final EditText amazon_url = findViewById(R.id.amazon_url);
        amazon_url.setText(AmazonManager.getBaseURL());
        final EditText google_url = findViewById(R.id.google_url);
        google_url.setText(GoogleBooksManager.getBaseURL());

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText amazon_url = findViewById(R.id.amazon_url);
                String newAmazon = amazon_url.getText().toString();
                EditText google_url = findViewById(R.id.google_url);
                String newGoogle = google_url.getText().toString();

                //TODO: add sanity checks
                if (!newAmazon.isEmpty()) {
                    AmazonManager.setBaseURL(newAmazon);
                }
                if (!newGoogle.isEmpty()) {
                    GoogleBooksManager.setBaseURL(newGoogle);
                }
                finish();
            }
        });

        Button cancelBtn = findViewById(R.id.cancel);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
