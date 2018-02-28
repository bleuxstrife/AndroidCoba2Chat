package com.android.rivchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.android.rivchat.R;

import com.android.rivchat.data.StaticConfig;
import com.android.rivchat.util.Consts;
import com.android.rivchat.util.PermissionsChecker;
import com.android.rivchat.util.RingtonePlayer;
import com.android.rivchat.util.WebRtcSessionManager;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.AppRTCAudioManager;
import com.quickblox.videochat.webrtc.BaseSession;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.QBSignalingSpec;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionStateCallback;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSignalingCallback;
import com.quickblox.videochat.webrtc.exception.QBRTCSignalException;
import com.quickblox.videochat.webrtc.view.QBRTCSurfaceView;
import com.quickblox.videochat.webrtc.view.QBRTCVideoTrack;

import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.VideoRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by EduSPOT on 27/02/2018.
 */

public class VideoActivity extends AppCompatActivity implements QBRTCClientSessionCallbacks, QBRTCSessionStateCallback<QBRTCSession>, QBRTCSignalingCallback {
    QBRTCSurfaceView remoteVideo;
    QBRTCSurfaceView localVideo;
    String emailFriend;
    int userIDFriend;
    List<Integer> userIds;
    QBRTCSession currentSession;
    QBRTCClient rtcClient;
    RingtonePlayer ringtonePlayer;
    WebRtcSessionManager sessionManager;
    Handler showIncomingCallWindowTaskHandler;
    Runnable showIncomingCallWindowTask;
    boolean isInCommingCall;
    AppRTCAudioManager audioManager;
    boolean callStarted;
    boolean closeByWifiStateAllow = true;
    boolean previousDeviceEarPiece;
    boolean showToastAfterHeadsetPlugged = true;
    boolean isVideoCall;
    boolean headsetPlugged;
    OnChangeDynamicToggle onChangeDynamicCallback;
    String hangUpReason;
    ArrayList<CurrentCallStateCallback> currentCallStateCallbackList = new ArrayList<>();
    PermissionsChecker checker;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        remoteVideo = (QBRTCSurfaceView) findViewById(R.id.remote_video_view);
        localVideo = (QBRTCSurfaceView) findViewById(R.id.local_video_view);
        EglBase eglContext = QBRTCClient.getInstance(this).getEglContext();
        remoteVideo.init(eglContext.getEglBaseContext(), null);
        isInCommingCall = getIntent().getBooleanExtra(StaticConfig.EXTRA_IS_INCOMING_CALL, true);
        sessionManager = WebRtcSessionManager.getInstance(this);
        if (!currentSessionExist()) {
//            we have already currentSession == null, so it's no reason to do further initialization
            finish();
            return;
        }
        initCurrentSession(currentSession);
        initQBRTCClient();
        ringtonePlayer = new RingtonePlayer(this, R.raw.beep);
        startSuitableFragment(isInCommingCall);
        checker = new PermissionsChecker(getApplicationContext());


    }

    private void fillVideoView(int userId, QBRTCSurfaceView videoView, QBRTCVideoTrack videoTrack) {
        videoTrack.addRenderer(new VideoRenderer(videoView));
    }

    private void call(int UserId){
        userIds = new ArrayList<>();
        userIds.add(UserId);

        currentSession = QBRTCClient.getInstance(this).createNewSessionWithOpponents(userIds, QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO);
//        currentSession.startCall();
    }

    private void initQBRTCClient() {
        rtcClient = QBRTCClient.getInstance(this);

        rtcClient.setCameraErrorHandler(new CameraVideoCapturer.CameraEventsHandler() {
            @Override
            public void onCameraError(final String s) {

//                showToast("Camera error: " + s);
            }

            @Override
            public void onCameraDisconnected() {
//                showToast("Camera onCameraDisconnected: ");
            }

            @Override
            public void onCameraFreezed(String s) {
//                showToast("Camera freezed: " + s);
                hangUpCurrentSession();
            }

            @Override
            public void onCameraOpening(String s) {
//                showToast("Camera aOpening: " + s);
            }

            @Override
            public void onFirstFrameAvailable() {
//                showToast("onFirstFrameAvailable: ");
            }

            @Override
            public void onCameraClosed() {
            }
        });
    }

    public void initCurrentSession(QBRTCSession session) {
        if (session != null) {
//            Log.d(TAG, "Init new QBRTCSession");
            this.currentSession = session;
            this.currentSession.addSessionCallbacksListener(VideoActivity.this);
            this.currentSession.addSignalingCallback(VideoActivity.this);
        }
    }

    public void releaseCurrentSession() {
//        Log.d(TAG, "Release current session");
        if (currentSession != null) {
            this.currentSession.removeSessionCallbacksListener(VideoActivity.this);
            this.currentSession.removeSignalingCallback(VideoActivity.this);
            rtcClient.removeSessionsCallbacksListener(VideoActivity.this);
            this.currentSession = null;
        }
    }

    private boolean currentSessionExist() {
        currentSession = sessionManager.getCurrentSession();
        return currentSession != null;
    }

    @Override
    public void onReceiveNewSession(QBRTCSession qbrtcSession) {
//        Log.d(TAG, "Session " + session.getSessionID() + " are income");
        if (getCurrentSession() != null) {
//            Log.d(TAG, "Stop new session. Device now is busy");
            qbrtcSession.rejectCall(null);
        }
    }

    @Override
    public void onUserNoActions(QBRTCSession qbrtcSession, Integer integer) {
        startIncomeCallTimer(0);
    }

    @Override
    public void onSessionStartClose(QBRTCSession qbrtcSession) {
        if (qbrtcSession.equals(getCurrentSession())) {
            qbrtcSession.removeSessionCallbacksListener(VideoActivity.this);
            notifyCallStateListenersCallStopped();
        }
    }

    @Override
    public void onUserNotAnswer(QBRTCSession qbrtcSession, Integer integer) {
        if (!qbrtcSession.equals(getCurrentSession())) {
            return;
        }
        ringtonePlayer.stop();
    }

    @Override
    public void onCallRejectByUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {
        if (!qbrtcSession.equals(getCurrentSession())) {
            return;
        }
        ringtonePlayer.stop();
    }

    @Override
    public void onCallAcceptByUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {
        if (!qbrtcSession.equals(getCurrentSession())) {
            return;
        }
        ringtonePlayer.stop();
    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {

    }

    @Override
    public void onSessionClosed(QBRTCSession qbrtcSession) {

        if (qbrtcSession.equals(getCurrentSession())) {
//            Log.d(TAG, "Stop session");

            if (audioManager != null) {
                audioManager.close();
            }
            releaseCurrentSession();

            closeByWifiStateAllow = true;
            finish();
        }
    }

    @Override
    public void onStateChanged(QBRTCSession qbrtcSession, BaseSession.QBRTCSessionState qbrtcSessionState) {

    }

    @Override
    public void onConnectedToUser(QBRTCSession qbrtcSession, Integer integer) {
        callStarted = true;
        notifyCallStateListenersCallStarted();
        forbiddenCloseByWifiState();
        if (isInCommingCall) {
            stopIncomeCallTimer();
        }
    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onConnectionClosedForUser(QBRTCSession qbrtcSession, Integer integer) {
        if (hangUpReason != null && hangUpReason.equals(Consts.WIFI_DISABLED)) {
            Intent returnIntent = new Intent();
            setResult(Consts.CALL_ACTIVITY_CLOSE_WIFI_DISABLED, returnIntent);
            finish();
        }
    }

    @Override
    public void onSuccessSendingPacket(QBSignalingSpec.QBSignalCMD qbSignalCMD, Integer integer) {

    }

    @Override
    public void onErrorSendingPacket(QBSignalingSpec.QBSignalCMD qbSignalCMD, Integer integer, QBRTCSignalException e) {

    }
    public void hangUpCurrentSession() {
        ringtonePlayer.stop();
        if (getCurrentSession() != null) {
            getCurrentSession().hangUp(new HashMap<String, String>());
        }
    }

    public void rejectCurrentSession() {
        if (getCurrentSession() != null) {
            getCurrentSession().rejectCall(new HashMap<String, String>());
        }
    }

    private QBRTCSession getCurrentSession() {
        return currentSession;
    }

    private void startIncomeCallTimer(long time) {
        showIncomingCallWindowTaskHandler.postAtTime(showIncomingCallWindowTask, SystemClock.uptimeMillis() + time);
    }

    private void stopIncomeCallTimer() {
//        Log.d(TAG, "stopIncomeCallTimer");
        showIncomingCallWindowTaskHandler.removeCallbacks(showIncomingCallWindowTask);
    }

    private void initIncomingCallTask() {
        showIncomingCallWindowTaskHandler = new Handler(Looper.myLooper());
        showIncomingCallWindowTask = new Runnable() {
            @Override
            public void run() {
                if (currentSession == null) {
                    return;
                }

                QBRTCSession.QBRTCSessionState currentSessionState = currentSession.getState();
                if (QBRTCSession.QBRTCSessionState.QB_RTC_SESSION_NEW.equals(currentSessionState)) {
                    rejectCurrentSession();
                } else {
                    ringtonePlayer.stop();
                    hangUpCurrentSession();
                }
//                Toaster.longToast("Call was stopped by timer");
            }
        };
    }

    private void startSuitableFragment(boolean isInComingCall) {
        if (isInComingCall) {
            initIncomingCallTask();
//            startLoadAbsentUsers();
            addIncomeCallFragment();
            checkPermission();
        } else {
            QBUsers.getUserByEmail(emailFriend).performAsync(new QBEntityCallback<QBUser>() {
                @Override
                public void onSuccess(QBUser qbUser, Bundle bundle) {
                    userIDFriend = qbUser.getId();
                    call(userIDFriend);
                }

                @Override
                public void onError(QBResponseException e) {

                }
            });
            addConversationFragment(isInComingCall);
        }
    }
    public interface CurrentCallStateCallback {
        void onCallStarted();

        void onCallStopped();

        void onOpponentsListUpdated(ArrayList<QBUser> newUsers);
    }

    private void notifyCallStateListenersCallStarted() {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onCallStarted();
        }
    }

    private void notifyCallStateListenersCallStopped() {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onCallStopped();
        }
    }

    private void notifyCallStateListenersNeedUpdateOpponentsList(final ArrayList<QBUser> newUsers) {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onOpponentsListUpdated(newUsers);
        }
    }

    private void forbiddenCloseByWifiState() {
        closeByWifiStateAllow = false;
    }

    private void initAudioManager() {
        audioManager = AppRTCAudioManager.create(this, new AppRTCAudioManager.OnAudioManagerStateListener() {
            @Override
            public void onAudioChangedState(AppRTCAudioManager.AudioDevice audioDevice) {
                if (callStarted) {
                    if (audioManager.getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.EARPIECE) {
                        previousDeviceEarPiece = true;
                    } else if (audioManager.getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.SPEAKER_PHONE) {
                        previousDeviceEarPiece = false;
                    }
                    if (showToastAfterHeadsetPlugged) {
//                        Toaster.shortToast("Audio device switched to  " + audioDevice);
                    }
                }
            }
        });
        isVideoCall = QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO.equals(currentSession.getConferenceType());
        if (isVideoCall) {
            audioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
//            Log.d(TAG, "AppRTCAudioManager.AudioDevice.SPEAKER_PHONE");
        } else {
            audioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
            previousDeviceEarPiece = true;
//            Log.d(TAG, "AppRTCAudioManager.AudioDevice.EARPIECE");
        }

        audioManager.setOnWiredHeadsetStateListener(new AppRTCAudioManager.OnWiredHeadsetStateListener() {
            @Override
            public void onWiredHeadsetStateChanged(boolean plugged, boolean hasMicrophone) {
                headsetPlugged = plugged;
                if (callStarted) {
//                    Toaster.shortToast("Headset " + (plugged ? "plugged" : "unplugged"));
                }
                if (onChangeDynamicCallback != null) {
                    if (!plugged) {
                        showToastAfterHeadsetPlugged = false;
                        if (previousDeviceEarPiece) {
                            setAudioDeviceDelayed(AppRTCAudioManager.AudioDevice.EARPIECE);
                        } else {
                            setAudioDeviceDelayed(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
                        }
                    }
                    onChangeDynamicCallback.enableDynamicToggle(plugged, previousDeviceEarPiece);
                }
            }
        });
        audioManager.init();
    }
    private void addIncomeCallFragment() {
//        Log.d(TAG, "QBRTCSession in addIncomeCallFragment is " + currentSession);
//
//        if (currentSession != null) {
//            IncomeCallFragment fragment = new IncomeCallFragment();
//            FragmentExecuotr.addFragment(getSupportFragmentManager(), R.id.fragment_container, fragment, INCOME_CALL_FRAGMENT);
//        } else {
//            Log.d(TAG, "SKIP addIncomeCallFragment method");
//        }
    }

    private void addConversationFragment(boolean isIncomingCall) {
//        BaseConversationFragment conversationFragment = BaseConversationFragment.newInstance(
//                isVideoCall
//                        ? new VideoConversationFragment()
//                        : new AudioConversationFragment(),
//                isIncomingCall);
//        FragmentExecuotr.addFragment(getSupportFragmentManager(), R.id.fragment_container, conversationFragment, conversationFragment.getClass().getSimpleName());
    }
    private void startPermissionsActivity(boolean checkOnlyAudio) {
        PermissionsActivity.startActivity(this, checkOnlyAudio, Consts.PERMISSIONS);
    }
    private void checkPermission() {
        if (checker.lacksPermissions(Consts.PERMISSIONS)) {
            startPermissionsActivity(!isVideoCall);
        }
    }

    public interface OnChangeDynamicToggle {
        void enableDynamicToggle(boolean plugged, boolean wasEarpiece);
    }
    private void setAudioDeviceDelayed(final AppRTCAudioManager.AudioDevice audioDevice) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showToastAfterHeadsetPlugged = true;
                audioManager.setAudioDevice(audioDevice);
            }
        }, 500);
    }

}
