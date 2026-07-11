package com.project.ongil;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RoutePlansActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_plans);

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.planAButton).setOnClickListener(view ->
                Toast.makeText(this, "안전 우선 플랜 A를 선택했어요.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.planBButton).setOnClickListener(view ->
                Toast.makeText(this, "빠른 플랜 B를 선택했어요.", Toast.LENGTH_SHORT).show());
    }
}
