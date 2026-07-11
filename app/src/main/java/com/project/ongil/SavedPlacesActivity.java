package com.project.ongil;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.project.ongil.data.SavedPlaceStore;

public class SavedPlacesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_places);

        TextInputEditText homeInput = findViewById(R.id.homeAddressInput);
        TextInputEditText academyInput = findViewById(R.id.academyAddressInput);
        TextInputEditText schoolInput = findViewById(R.id.schoolAddressInput);
        homeInput.setText(SavedPlaceStore.getAddress(this, SavedPlaceStore.HOME));
        academyInput.setText(SavedPlaceStore.getAddress(this, SavedPlaceStore.ACADEMY));
        schoolInput.setText(SavedPlaceStore.getAddress(this, SavedPlaceStore.SCHOOL));

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.savePlacesButton).setOnClickListener(view -> {
            SavedPlaceStore.saveAddress(this, SavedPlaceStore.HOME, textOf(homeInput));
            SavedPlaceStore.saveAddress(this, SavedPlaceStore.ACADEMY, textOf(academyInput));
            SavedPlaceStore.saveAddress(this, SavedPlaceStore.SCHOOL, textOf(schoolInput));
            Toast.makeText(this, "자주 가는 장소를 저장했어요.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
