package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

public class AdministrationGoogleBooks extends BookCatalogueActivity {

    @Override
    protected int getLayoutId(){
        return R.layout.activity_administration_google_books;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.google_books);
            setupPage();
        } catch (Exception ignore) {
            Logger.logError(ignore);
        }
    }

    private void setupPage() {
        /* Host link */
        final EditText url = findViewById(R.id.url);
        url.setText(GoogleBooksManager.getBaseURL());

        /* Save Button */
        Button btn = findViewById(R.id.confirm);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newURL = url.getText().toString();
                //TODO: add sanity checks
                if (!newURL.isEmpty()) {
                    GoogleBooksManager.setBaseURL(newURL);
                    finish();
                }
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