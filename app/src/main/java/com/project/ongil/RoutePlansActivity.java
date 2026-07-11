package com.project.ongil;

import android.os.Bundle;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.project.ongil.decision.SafetyDecisionEngine;

public class RoutePlansActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_plans);

        double trafficRisk = getIntent().getDoubleExtra("trafficRisk", 0.30);
        bindRoute(R.id.planATitle, R.id.planAMeta, R.id.planAProgress,
                R.id.planAFactors, SafetyDecisionEngine.safeRoute(trafficRisk));
        bindRoute(R.id.planBTitle, R.id.planBMeta, R.id.planBProgress,
                R.id.planBFactors, SafetyDecisionEngine.fastRoute(trafficRisk));

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.planAButton).setOnClickListener(view ->
                Toast.makeText(this, "안전 우선 플랜 A를 선택했어요.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.planBButton).setOnClickListener(view ->
                Toast.makeText(this, "빠른 플랜 B를 선택했어요.", Toast.LENGTH_SHORT).show());
    }

    private void bindRoute(int titleId, int metaId, int progressId, int factorsId,
                           SafetyDecisionEngine.SafeRoute route) {
        ((TextView) findViewById(titleId)).setText(route.name);
        ((TextView) findViewById(metaId)).setText(route.minutes + "분 · 야간 안전 점수 "
                + route.safetyScore + "점");
        ((LinearProgressIndicator) findViewById(progressId)).setProgress(route.safetyScore);
        ((TextView) findViewById(factorsId)).setText(
                "✓ 밝은 길 구간 " + route.lightingPercent + "%\n"
                        + "✓ 편의점·파출소 등 안전 거점 " + route.safetySpotCount + "곳\n"
                        + (route.darkAlleyCount == 0
                        ? "✓ 어두운 골목 없이 이동"
                        : "△ 어두운 골목 " + route.darkAlleyCount + "곳"));
    }
}
