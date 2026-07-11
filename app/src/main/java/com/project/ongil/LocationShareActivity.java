package com.project.ongil;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class LocationShareActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_share);

        String role = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        boolean isParent = LoginActivity.ROLE_PARENT.equals(role);

        TextView title = findViewById(R.id.shareTitle);
        TextView description = findViewById(R.id.shareDescription);
        MaterialButton primaryButton = findViewById(R.id.sharePrimaryButton);
        MaterialButton secondaryButton = findViewById(R.id.shareSecondaryButton);

        title.setText(isParent ? "학생에게 위치 공유 요청" : "학부모의 위치 공유 요청");
        description.setText(isParent
                ? "학생이 수락하면 귀가가 끝날 때까지만 서로의 위치를 확인할 수 있어요."
                : "엄마가 귀가 중 위치 확인을 요청했어요. 수락 여부는 학생이 직접 선택해요.");
        primaryButton.setText(isParent ? "요청 보내기" : "수락하고 공유하기");
        secondaryButton.setText(isParent ? "아직 보내지 않기" : "거절하기");

        primaryButton.setOnClickListener(view -> {
            findViewById(R.id.waitingCard).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.shareStatusText)).setText(isParent
                    ? "학생의 응답을 기다리고 있어요"
                    : "귀가 완료 시까지 위치를 공유 중이에요");
            Toast.makeText(this, isParent ? "위치 공유 요청을 보냈어요." : "위치 공유를 시작했어요.", Toast.LENGTH_SHORT).show();
        });
        secondaryButton.setOnClickListener(view -> finish());
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
    }
}
