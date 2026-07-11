package com.project.ongil;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        role = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        if (role == null) {
            role = LoginActivity.ROLE_STUDENT;
        }

        bindRoleContent();
        findViewById(R.id.locationShareButton).setOnClickListener(view -> openLocationSharing());
        findViewById(R.id.safeRouteButton).setOnClickListener(view -> openRoutePlans());
        findViewById(R.id.assistantButton).setOnClickListener(view -> showAssistant());
        findViewById(R.id.tmapButton).setOnClickListener(view ->
                Toast.makeText(this, "선택한 픽업존을 TMAP으로 연결할 예정이에요.", Toast.LENGTH_SHORT).show());
    }

    private void bindRoleContent() {
        boolean isParent = LoginActivity.ROLE_PARENT.equals(role);
        TextView greeting = findViewById(R.id.greetingText);
        TextView roleBadge = findViewById(R.id.roleBadge);
        TextView statusTitle = findViewById(R.id.statusTitle);
        TextView statusBody = findViewById(R.id.statusBody);

        greeting.setText(isParent ? "오늘도 안전한 마중길이에요" : "오늘도 안전하게, 집으로");
        roleBadge.setText(isParent ? "학부모 모드" : "학생 모드");
        statusTitle.setText(isParent ? "B 픽업존이 가장 여유로워요" : "현재 위치를 확인했어요");
        statusBody.setText(isParent
                ? "학생 도보 4분 · 차량 혼잡도 낮음"
                : "픽업 요청을 기다리거나 안전 경로를 찾아보세요.");

        findViewById(R.id.tmapButton).setVisibility(isParent ? View.VISIBLE : View.GONE);
    }

    private void openLocationSharing() {
        Intent intent = new Intent(this, LocationShareActivity.class);
        intent.putExtra(LoginActivity.EXTRA_ROLE, role);
        startActivity(intent);
    }

    private void openRoutePlans() {
        startActivity(new Intent(this, RoutePlansActivity.class));
    }

    private void showAssistant() {
        boolean isParent = LoginActivity.ROLE_PARENT.equals(role);
        String message = isParent
                ? "지금 학원 앞이 혼잡해요. B 픽업존에서 만날까요?"
                : "오늘은 부모님과 만날까요, 혼자 안전하게 귀가할까요?";
        new MaterialAlertDialogBuilder(this)
                .setTitle("온길 도우미")
                .setMessage(message)
                .setPositiveButton("추천 보기", (dialog, which) -> openRoutePlans())
                .setNegativeButton("나중에", null)
                .show();
    }
}
