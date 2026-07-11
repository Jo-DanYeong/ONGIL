package com.project.ongil;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE = "extra_role";
    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_PARENT = "parent";

    private String selectedRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        TextInputEditText emailInput = findViewById(R.id.emailInput);
        MaterialButtonToggleGroup roleGroup = findViewById(R.id.roleGroup);
        MaterialButton continueButton = findViewById(R.id.continueButton);

        selectedRole = ROLE_STUDENT;
        roleGroup.check(R.id.studentRoleButton);

        roleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedRole = checkedId == R.id.studentRoleButton ? ROLE_STUDENT : ROLE_PARENT;
        });

        continueButton.setOnClickListener(view -> {
            if (selectedRole == null) {
                Toast.makeText(this, "학생 또는 학부모를 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(EXTRA_ROLE, selectedRole);
            startActivity(intent);
        });
    }
}
