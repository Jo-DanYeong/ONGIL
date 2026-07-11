package com.project.ongil;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.project.ongil.data.AssistantApiClient;
import com.project.ongil.data.SavedPlaceStore;
import com.project.ongil.data.TrafficApiClient;
import com.project.ongil.decision.SafetyDecisionEngine;
import com.skt.tmap.TMapData;
import com.skt.tmap.TMapPoint;
import com.skt.tmap.TMapView;
import com.skt.tmap.overlay.TMapMarkerItem;
import com.skt.tmap.overlay.TMapPolyLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 4201;

    private String role;
    private SafetyDecisionEngine.ReturnMode returnMode;
    private double trafficRisk = 0.30;
    private String trafficSource = "TOPIS 연결 전 시뮬레이션";
    private TrafficApiClient trafficClient;
    private TrafficApiClient.PickupRecommendation livePickupRecommendation;
    private AssistantApiClient assistantClient;
    private TMapView tMapView;
    private boolean mapReady;
    private String selectedPickupName;
    private TMapPoint selectedPickupPoint;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable activeTyping;

    private TextView statusTitle;
    private TextView statusBody;
    private TextView evidenceText;
    private TextView mapStatusBadge;
    private TextView assistantMessage;
    private TextView oniMapMessage;
    private MaterialButton locationShareButton;
    private MaterialButton safeRouteButton;
    private MaterialButton tmapButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        role = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        if (role == null) role = LoginActivity.ROLE_STUDENT;

        bindViews();
        bindRoleHeader();
        initializeMap();
        initializeAssistant();
        showDecisionPrompt();
        bindActions();
        loadLiveTrafficIfConfigured();
    }

    private void bindViews() {
        statusTitle = findViewById(R.id.statusTitle);
        statusBody = findViewById(R.id.statusBody);
        evidenceText = findViewById(R.id.decisionEvidenceText);
        mapStatusBadge = findViewById(R.id.mapStatusBadge);
        assistantMessage = findViewById(R.id.assistantMessage);
        oniMapMessage = findViewById(R.id.oniMapMessage);
        locationShareButton = findViewById(R.id.locationShareButton);
        safeRouteButton = findViewById(R.id.safeRouteButton);
        tmapButton = findViewById(R.id.tmapButton);
    }

    private void bindRoleHeader() {
        boolean isParent = LoginActivity.ROLE_PARENT.equals(role);
        ((TextView) findViewById(R.id.greetingText)).setText(
                isParent ? "오늘도 안전한 마중길이에요" : "오늘도 안전하게, 집으로");
        ((TextView) findViewById(R.id.roleBadge)).setText(
                isParent ? "학부모 모드" : "학생 모드");
    }

    private void bindActions() {
        findViewById(R.id.pickupModeButton).setOnClickListener(view -> showPickupDecision());
        findViewById(R.id.soloModeButton).setOnClickListener(view -> showSoloDecision());
        findViewById(R.id.oniPetButton).setOnClickListener(view -> openAssistantSheet());
        findViewById(R.id.assistantButton).setOnClickListener(view -> openAssistantSheet());
        findViewById(R.id.savedPlacesButton).setOnClickListener(view ->
                startActivity(new Intent(this, SavedPlacesActivity.class)));
        locationShareButton.setOnClickListener(view -> openLocationSharing());
        safeRouteButton.setOnClickListener(view -> {
            if (returnMode == SafetyDecisionEngine.ReturnMode.PICKUP) {
                startPickupWalkingRoute();
            } else {
                openRoutePlans();
            }
        });
        tmapButton.setOnClickListener(view -> Toast.makeText(
                this, "추천 픽업존으로 차량 안내를 시작해요.", Toast.LENGTH_SHORT).show());
    }

    private void initializeAssistant() {
        String backendUrl = BuildConfig.ONGIL_BACKEND_URL.trim();
        if (!backendUrl.isEmpty()) assistantClient = new AssistantApiClient(backendUrl);
    }

    private void initializeMap() {
        FrameLayout container = findViewById(R.id.tmapContainer);
        TextView loadingText = findViewById(R.id.mapLoadingText);
        String appKey = BuildConfig.TMAP_APP_KEY.trim();
        if (appKey.isEmpty()) {
            loadingText.setText("local.properties에 TMAP_APP_KEY를 설정해주세요");
            return;
        }

        tMapView = new TMapView(this);
        container.addView(tMapView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        tMapView.setOnApiKeyListenerCallback(new TMapView.OnApiKeyListenerCallback() {
            @Override
            public void onSKTMapApikeySucceed() {
                runOnUiThread(() -> loadingText.setText("지도를 준비하고 있어요"));
            }

            @Override
            public void onSKTMapApikeyFailed(String error) {
                runOnUiThread(() -> {
                    loadingText.setVisibility(View.VISIBLE);
                    loadingText.setText("TMAP 인증을 확인해주세요");
                });
            }
        });
        tMapView.setOnMapReadyListener(() -> runOnUiThread(() -> {
            loadingText.setVisibility(View.GONE);
            mapReady = true;
            if (SavedPlaceStore.hasPoint(this, SavedPlaceStore.ACADEMY)) {
                tMapView.setCenterPoint(
                        SavedPlaceStore.getLatitude(this, SavedPlaceStore.ACADEMY),
                        SavedPlaceStore.getLongitude(this, SavedPlaceStore.ACADEMY)
                );
            } else {
                tMapView.setCenterPoint(37.4984, 127.0618);
            }
            tMapView.setZoomLevel(16);
            tMapView.setTrafficInfoActive(true);
            addDemoMapMarkers();
            refreshSavedPlaceMarkers();
        }));
        tMapView.setSKTMapApiKey(appKey);
    }

    private void addDemoMapMarkers() {
        tMapView.removeAllTMapMarkerItem();
        addMapMarker("student", "학생", "학", 37.49835, 127.06120, Color.rgb(86, 120, 212));
        addMapMarker("parent", "학부모", "부", 37.49755, 127.06285, Color.rgb(226, 122, 82));
        addMapMarker("zone-a", "A 픽업존", "A", 37.49905, 127.06075, Color.rgb(76, 155, 117));
        addMapMarker("zone-b", "B 픽업존", "B", 37.49795, 127.06210, Color.rgb(40, 112, 77));
        addMapMarker("zone-c", "C 픽업존", "C", 37.49865, 127.06305, Color.rgb(236, 164, 85));
    }

    private void addMapMarker(String id, String title, String label,
                              double latitude, double longitude, int color) {
        tMapView.removeTMapMarkerItem(id);
        TMapMarkerItem marker = new TMapMarkerItem();
        marker.setId(id);
        marker.setIcon(createMarkerIcon(label, color));
        marker.setPosition(0.5f, 1.0f);
        marker.setTMapPoint(new TMapPoint(latitude, longitude));
        marker.setCalloutTitle(title);
        marker.setCanShowCallout(true);
        tMapView.addTMapMarkerItem(marker);
    }

    private Bitmap createMarkerIcon(String label, int color) {
        Bitmap bitmap = Bitmap.createBitmap(96, 112, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawCircle(48, 42, 32, paint);
        Path tail = new Path();
        tail.moveTo(33, 65);
        tail.lineTo(48, 102);
        tail.lineTo(63, 65);
        tail.close();
        canvas.drawPath(tail, paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(25);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = 42 - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(label, 48, baseline, paint);
        return bitmap;
    }

    private void showDecisionPrompt() {
        returnMode = null;
        statusTitle.setText("오늘 귀가 방식을 알려주세요");
        statusBody.setText("픽업 가능 여부에 따라 온길이 만남 장소 또는 야간 안전 경로를 결정해요.");
        evidenceText.setText("범죄 예측이 아닌 조도 · 안전 거점 · 시간대 · 교통 데이터로 판단합니다.");
        mapStatusBadge.setText("귀가 상황 확인 중");
        assistantMessage.setText("오늘 픽업이야, 혼자 가?");
        oniMapMessage.setText("오늘 픽업이야,\n혼자 가?");
        locationShareButton.setVisibility(View.GONE);
        safeRouteButton.setVisibility(View.GONE);
        tmapButton.setVisibility(View.GONE);
    }

    private void showPickupDecision() {
        returnMode = SafetyDecisionEngine.ReturnMode.PICKUP;
        if (livePickupRecommendation != null) {
            TrafficApiClient.PickupRecommendation zone = livePickupRecommendation;
            int walkMinutes = Math.max(1, (int) Math.round(zone.distanceMeters / 75.0));
            statusTitle.setText(zone.name + "에서 만나요");
            statusBody.setText("학생 도보 약 " + walkMinutes + "분 · 대기 차량 "
                    + zone.waitingVehicles + "/" + zone.capacity + "대 · 주변 도로 "
                    + zone.trafficLevel);
            evidenceText.setText("종합 " + zone.totalScore + "점 · 안전 " + zone.safetyScore
                    + "점 · 정차 적합 " + zone.stoppingScore + "점 · " + trafficSource);
            mapStatusBadge.setText(zone.name + " · 종합 " + zone.totalScore + "점");
            assistantMessage.setText("거리, 안전, 정차 여건, 현재 교통을 함께 비교해 "
                    + zone.name + "을 골랐어요.");
            oniMapMessage.setText(zone.name + "이 좋아요!");
            showPickupActions(zone.name);
            return;
        }
        SafetyDecisionEngine.PickupZone zone =
                SafetyDecisionEngine.recommendPickupZone(trafficRisk);

        statusTitle.setText(zone.name + "에서 만나요");
        statusBody.setText("학생 도보 " + zone.studentWalkMinutes + "분 · 대기 차량 "
                + zone.waitingCars + "대 · 주변 도로 " + zone.trafficLabel());
        evidenceText.setText("판단 근거 · " + trafficSource
                + " + 사용자 도착/대기 체크인(해커톤 시뮬레이션)");
        mapStatusBadge.setText(zone.name + " · " + zone.trafficLabel());
        assistantMessage.setText(zone.name + "이 가장 덜 붐벼요. 귀가 종료까지만 위치를 공유할까요?");
        oniMapMessage.setText(zone.name + "이 덜 붐벼요!");

        showPickupActions(zone.name);
    }

    private void showPickupActions(String zoneName) {
        selectedPickupName = zoneName;
        selectedPickupPoint = pickupPointFor(zoneName);
        locationShareButton.setText("일시 위치 공유");
        locationShareButton.setVisibility(View.VISIBLE);
        safeRouteButton.setText("픽업존 도보 경로");
        safeRouteButton.setVisibility(View.VISIBLE);
        tmapButton.setText("TMAP으로 " + zoneName + " 안내");
        tmapButton.setVisibility(LoginActivity.ROLE_PARENT.equals(role) ? View.VISIBLE : View.GONE);
    }

    private TMapPoint pickupPointFor(String zoneName) {
        if (zoneName.startsWith("A")) return new TMapPoint(37.49905, 127.06075);
        if (zoneName.startsWith("C")) return new TMapPoint(37.49865, 127.06305);
        return new TMapPoint(37.49795, 127.06210);
    }

    private void showSoloDecision() {
        returnMode = SafetyDecisionEngine.ReturnMode.SOLO;
        SafetyDecisionEngine.SafeRoute route = SafetyDecisionEngine.safeRoute(trafficRisk);

        statusTitle.setText(route.name + "을 추천해요");
        statusBody.setText(route.minutes + "분 · 밝은 길 " + route.lightingPercent
                + "% · 편의점/파출소 등 안전 거점 " + route.safetySpotCount + "곳");
        evidenceText.setText("판단 근거 · 가로등 · 안전 거점 · 야간 시간대 · " + trafficSource);
        mapStatusBadge.setText("야간 안전 점수 " + route.safetyScore + "점");
        assistantMessage.setText("조금 더 걸려도 밝고 도움을 요청하기 쉬운 길로 안내할게요.");
        oniMapMessage.setText("밝은 길을\n따라가자!");

        locationShareButton.setText("귀가 위치 일시 공유");
        locationShareButton.setVisibility(View.VISIBLE);
        safeRouteButton.setText("안전 경로 비교");
        safeRouteButton.setVisibility(View.VISIBLE);
        tmapButton.setVisibility(View.GONE);
    }

    private void loadLiveTrafficIfConfigured() {
        String backendUrl = BuildConfig.ONGIL_BACKEND_URL.trim();
        String configuredLinkIds = BuildConfig.TOPIS_LINK_IDS.trim();
        if (backendUrl.isEmpty() || configuredLinkIds.isEmpty()) return;

        List<String> linkIds = new ArrayList<>();
        for (String linkId : Arrays.asList(configuredLinkIds.split(","))) {
            if (!linkId.trim().isEmpty()) linkIds.add(linkId.trim());
        }
        if (linkIds.isEmpty()) return;

        trafficClient = new TrafficApiClient(backendUrl);
        trafficClient.assessLinks(linkIds, new TrafficApiClient.Callback() {
            @Override
            public void onSuccess(TrafficApiClient.Assessment assessment) {
                trafficRisk = assessment.trafficRisk;
                trafficSource = assessment.usesLiveData
                        ? "TOPIS 실시간 도로소통·돌발정보"
                        : "TOPIS 서버 시뮬레이션";
                refreshCurrentDecision();
            }

            @Override
            public void onError(Exception error) {
                trafficSource = "TOPIS 연결 실패 · 시뮬레이션";
                refreshCurrentDecision();
            }
        });
        trafficClient.recommendPickupZone(linkIds, new TrafficApiClient.PickupCallback() {
            @Override
            public void onSuccess(TrafficApiClient.PickupRecommendation recommendation) {
                livePickupRecommendation = recommendation;
                trafficRisk = recommendation.trafficRisk;
                trafficSource = recommendation.usesLiveData
                        ? "TOPIS 실시간 교통·돌발 + 후보지 안전·정차 데이터"
                        : "교통 시뮬레이션 + 후보지 안전·정차 데이터";
                refreshCurrentDecision();
            }

            @Override
            public void onError(Exception error) {
                livePickupRecommendation = null;
            }
        });
    }

    private void refreshCurrentDecision() {
        if (returnMode == SafetyDecisionEngine.ReturnMode.PICKUP) showPickupDecision();
        if (returnMode == SafetyDecisionEngine.ReturnMode.SOLO) showSoloDecision();
    }

    private void openAssistantSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_oni_chat, null);
        dialog.setContentView(sheet);

        TextView replyText = sheet.findViewById(R.id.oniReplyText);
        EditText input = sheet.findViewById(R.id.oniMessageInput);
        MaterialButton sendButton = sheet.findViewById(R.id.oniSendButton);
        View.OnClickListener sendTyped = view -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            input.setText("");
            sendAssistantMessage(message, replyText, sendButton, dialog);
        };
        sendButton.setOnClickListener(sendTyped);
        input.setOnEditorActionListener((view, actionId, event) -> {
            sendTyped.onClick(view);
            return true;
        });
        sheet.findViewById(R.id.quickPickupButton).setOnClickListener(view ->
                sendAssistantMessage("엄마가 데리러 온대", replyText, sendButton, dialog));
        sheet.findViewById(R.id.quickSoloButton).setOnClickListener(view ->
                sendAssistantMessage("오늘 혼자 걸어가", replyText, sendButton, dialog));
        sheet.findViewById(R.id.quickShareButton).setOnClickListener(view ->
                sendAssistantMessage("부모님한테 위치 보여줘", replyText, sendButton, dialog));
        dialog.show();
    }

    private void sendAssistantMessage(String message, TextView replyText,
                                      MaterialButton sendButton, BottomSheetDialog dialog) {
        replyText.setText("온이가 귀가 상황을 확인하고 있어요…");
        sendButton.setEnabled(false);
        if (assistantClient == null) {
            AssistantApiClient.Result result = offlineAssistantResult(message);
            typeOniReply(replyText, result.reply, () -> {
                sendButton.setEnabled(true);
                handleAssistantAction(result, dialog);
            });
            return;
        }
        assistantClient.chat(message, role, currentTimeBucket(), new AssistantApiClient.Callback() {
            @Override
            public void onSuccess(AssistantApiClient.Result result) {
                typeOniReply(replyText, result.reply, () -> {
                    sendButton.setEnabled(true);
                    handleAssistantAction(result, dialog);
                });
            }

            @Override
            public void onError(Exception error) {
                sendButton.setEnabled(true);
                AssistantApiClient.Result fallback = offlineAssistantResult(message);
                typeOniReply(replyText, fallback.reply + "\n(서버 연결 전 시연 모드)", () ->
                        handleAssistantAction(fallback, dialog));
            }
        });
    }

    private void typeOniReply(TextView target, String message, Runnable onComplete) {
        if (activeTyping != null) typingHandler.removeCallbacks(activeTyping);
        target.setText("");
        final int[] index = {0};
        activeTyping = new Runnable() {
            @Override
            public void run() {
                if (index[0] < message.length()) {
                    target.append(String.valueOf(message.charAt(index[0]++)));
                    typingHandler.postDelayed(this, 24);
                } else {
                    activeTyping = null;
                    onComplete.run();
                }
            }
        };
        typingHandler.post(activeTyping);
    }

    private void handleAssistantAction(AssistantApiClient.Result result, BottomSheetDialog dialog) {
        if (AssistantApiClient.OPEN_PICKUP_ZONES.equals(result.action)) {
            showPickupDecision();
            oniMapMessage.setText("픽업존을 골랐어요!");
            dialog.dismiss();
            Toast.makeText(this, result.reply, Toast.LENGTH_LONG).show();
        } else if (AssistantApiClient.OPEN_SAFE_ROUTES.equals(result.action)) {
            showSoloDecision();
            dialog.dismiss();
            Toast.makeText(this, result.reply, Toast.LENGTH_LONG).show();
            openRoutePlans();
        } else if (AssistantApiClient.REQUEST_LOCATION_SHARE.equals(result.action)) {
            dialog.dismiss();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("위치 공유 요청")
                    .setMessage(result.reply + "\n\n상대방에게 위치 공유를 요청할까요?")
                    .setPositiveButton("확인 후 요청", (confirmDialog, which) -> openLocationSharing())
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    private AssistantApiClient.Result offlineAssistantResult(String message) {
        String normalized = message.replaceAll("\\s+", "");
        boolean isParent = LoginActivity.ROLE_PARENT.equals(role);
        if (normalized.contains("위치")
                && (normalized.contains("공유") || normalized.contains("보여"))) {
            return new AssistantApiClient.Result(
                    isParent
                            ? "위치 공유는 상대방 확인 후 시작돼요. 요청 화면을 열어드릴게요."
                            : "위치 공유는 네가 확인한 뒤에만 시작돼. 요청 화면을 열어줄게.",
                    AssistantApiClient.REQUEST_LOCATION_SHARE, true);
        }
        if (normalized.contains("혼자") || normalized.contains("걸어")
                || normalized.contains("밤길") || normalized.contains("무서")) {
            return new AssistantApiClient.Result(
                    isParent
                            ? "학생이 이용할 밝은 길과 안전 거점 우선 경로를 보여드릴게요."
                            : "그럼 밝은 길과 안전 거점을 우선한 야간 안전 경로를 보여줄게.",
                    AssistantApiClient.OPEN_SAFE_ROUTES, false);
        }
        if (normalized.contains("픽업") || normalized.contains("데리러")
                || normalized.contains("마중")) {
            return new AssistantApiClient.Result(
                    isParent
                            ? "학생과 만나기 편한 픽업존을 교통·거리·정차 여건 순으로 보여드릴게요."
                            : "가까우면서 안전하고 정차하기 좋은 픽업존을 보여줄게.",
                    AssistantApiClient.OPEN_PICKUP_ZONES, false);
        }
        return new AssistantApiClient.Result(
                isParent
                        ? "학생의 귀가 상황을 알려주세요. 필요한 온길 기능을 열어드릴게요."
                        : "픽업인지 혼자 가는지 말해줘. 필요한 온길 기능을 열어줄게.",
                AssistantApiClient.NONE, false);
    }

    private String currentTimeBucket() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 21 || hour < 6) return "night";
        if (hour >= 18) return "evening";
        return "day";
    }

    private void refreshSavedPlaceMarkers() {
        if (!mapReady) return;
        refreshSavedPlace(SavedPlaceStore.HOME, "집", "집", Color.rgb(53, 92, 77));
        refreshSavedPlace(SavedPlaceStore.ACADEMY, "학원", "원", Color.rgb(142, 100, 181));
        refreshSavedPlace(SavedPlaceStore.SCHOOL, "학교", "교", Color.rgb(67, 139, 202));
    }

    private void refreshSavedPlace(String type, String title, String label, int color) {
        String markerId = "saved-" + type;
        String address = SavedPlaceStore.getAddress(this, type);
        if (address.isEmpty()) {
            tMapView.removeTMapMarkerItem(markerId);
            return;
        }
        if (SavedPlaceStore.hasPoint(this, type)) {
            addMapMarker(markerId, title, label,
                    SavedPlaceStore.getLatitude(this, type),
                    SavedPlaceStore.getLongitude(this, type), color);
            return;
        }
        new TMapData().findAddressPOI(address, items -> {
            if (items == null || items.isEmpty()) return;
            TMapPoint point = items.get(0).getPOIPoint();
            SavedPlaceStore.savePoint(this, type, point.getLatitude(), point.getLongitude());
            runOnUiThread(() -> addMapMarker(markerId, title, label,
                    point.getLatitude(), point.getLongitude(), color));
        });
    }

    private void startPickupWalkingRoute() {
        if (!mapReady || selectedPickupPoint == null) {
            Toast.makeText(this, "지도와 픽업존을 먼저 준비해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }
        requestCurrentLocation();
    }

    private void requestCurrentLocation() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        Location best = newestLocation(
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        );
        if (best != null) {
            renderPickupWalkingRoute(best);
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                renderPickupWalkingRoute(location);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try {
            String provider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            Toast.makeText(this, "현재 위치를 확인하고 있어요.", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "기기의 위치 서비스를 켜주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private Location newestLocation(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.getTime() >= second.getTime() ? first : second;
    }

    private void renderPickupWalkingRoute(Location location) {
        TMapPoint start = new TMapPoint(location.getLatitude(), location.getLongitude());
        addMapMarker("current-position", "현재 위치", "나",
                location.getLatitude(), location.getLongitude(), Color.rgb(53, 92, 77));
        statusTitle.setText(selectedPickupName + "까지 경로를 찾고 있어요");
        new TMapData().findPathDataWithType(
                TMapData.TMapPathType.PEDESTRIAN_PATH,
                start,
                selectedPickupPoint,
                polyLine -> runOnUiThread(() -> showPickupPath(start, polyLine))
        );
    }

    private void showPickupPath(TMapPoint start, TMapPolyLine polyLine) {
        if (polyLine == null || polyLine.getLinePointList().isEmpty()) {
            statusTitle.setText("픽업존 경로를 찾지 못했어요");
            Toast.makeText(this, "잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        tMapView.removeTMapPolyLine("pickup-walking-route");
        polyLine.setID("pickup-walking-route");
        polyLine.setLineColor(Color.rgb(53, 92, 77));
        polyLine.setLineWidth(8f);
        polyLine.setOutLineColor(Color.WHITE);
        polyLine.setOutLineWidth(3f);
        tMapView.addTMapPolyLine(polyLine);

        ArrayList<TMapPoint> bounds = new ArrayList<>(polyLine.getLinePointList());
        bounds.add(start);
        bounds.add(selectedPickupPoint);
        tMapView.fitBounds(tMapView.getBoundsFromPoints(bounds));
        int meters = (int) Math.round(polyLine.getDistance());
        int minutes = Math.max(1, (int) Math.ceil(meters / 75.0));
        statusTitle.setText(selectedPickupName + "까지 도보 약 " + minutes + "분");
        statusBody.setText("현재 위치에서 " + meters + "m · TMAP 보행자 경로");
        oniMapMessage.setText("픽업존까지\n같이 가자!");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestCurrentLocation();
            } else {
                Toast.makeText(this, "현재 위치를 허용해야 픽업존 경로를 안내할 수 있어요.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tMapView != null) tMapView.onResume();
        refreshSavedPlaceMarkers();
    }

    @Override
    protected void onPause() {
        if (tMapView != null) tMapView.onPause();
        super.onPause();
    }

    private void openLocationSharing() {
        Intent intent = new Intent(this, LocationShareActivity.class);
        intent.putExtra(LoginActivity.EXTRA_ROLE, role);
        startActivity(intent);
    }

    private void openRoutePlans() {
        Intent intent = new Intent(this, RoutePlansActivity.class);
        intent.putExtra("trafficRisk", trafficRisk);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (activeTyping != null) typingHandler.removeCallbacks(activeTyping);
        if (trafficClient != null) trafficClient.close();
        if (assistantClient != null) assistantClient.close();
        if (tMapView != null) tMapView.onDestroy();
        super.onDestroy();
    }
}
