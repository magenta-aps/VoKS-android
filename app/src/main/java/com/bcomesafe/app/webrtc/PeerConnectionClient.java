/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.webrtc;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bcomesafe.app.utils.DeviceNameHandler;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.webrtc.AppRTCClient.SignalingParameters;
import com.bcomesafe.app.webrtcutils.LooperExecutor;

/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaCodecVideoEncoder;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaConstraints.KeyValuePair;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Peer connection client implementation.
 * <p/>
 * All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
public class PeerConnectionClient {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = PeerConnectionClient.class.getSimpleName();

    private static final boolean useFrontFacingCamera = false;

    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String FIELD_TRIAL_VP9 = "WebRTC-SupportVP9/Enabled/";
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private static final String MAX_VIDEO_WIDTH_CONSTRAINT = "maxWidth";
    private static final String MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth";
    private static final String MAX_VIDEO_HEIGHT_CONSTRAINT = "maxHeight";
    private static final String MIN_VIDEO_HEIGHT_CONSTRAINT = "minHeight";
    private static final String MAX_VIDEO_FPS_CONSTRAINT = "maxFrameRate";
    private static final String MIN_VIDEO_FPS_CONSTRAINT = "minFrameRate";
    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static final int MAX_VIDEO_WIDTH = 1280;
    private static final int MAX_VIDEO_HEIGHT = 1280;
    private static final int MAX_VIDEO_FPS = 30;

    private static final PeerConnectionClient mInstance = new PeerConnectionClient();
    private final PCObserver mPcObserver = new PCObserver();
    private final SDPObserver mSdpObserver = new SDPObserver();
    private final LooperExecutor mExecutor;

    private PeerConnectionFactory mFactory;
    private PeerConnection mPeerConnection;
    private PeerConnectionFactory.Options mOptions = null;
    private VideoSource mVideoSource;
    private boolean videoCallEnabled;
    private boolean preferIsac;
    private boolean preferH264;
    private boolean videoSourceStopped;
    private boolean isError;
    private Timer mStatsTimer;
    private VideoRenderer.Callbacks mLocalRender;
    private VideoRenderer.Callbacks mRemoteRender;
    private SignalingParameters mSignalingParameters;
    private MediaConstraints mPcConstraints;
    private MediaConstraints mVideoConstraints;
    private MediaConstraints mAudioConstraints;
    private MediaConstraints mSdpMediaConstraints;
    private PeerConnectionParameters mPeerConnectionParameters;
    // Queued remote ICE candidates are consumed only after both local and
    // remote descriptions are set. Similarly local ICE candidates are sent to
    // remote peer after both local and remote description are set.
    private LinkedList<IceCandidate> mQueuedRemoteCandidates;
    private PeerConnectionEvents mEvents;
    private boolean mIsInitiator;
    private SessionDescription mLocalSdp; // either offer or answer SDP
    private MediaStream mMediaStream;
    private int mNumberOfCameras;
    private VideoCapturerAndroid mVideoCapturer;
    // enableVideo is set to true if video should be rendered and sent.
    private boolean mRenderVideo;
    private VideoTrack mLocalVideoTrack;
    private VideoTrack mRemoteVideoTrack;
    // Remote audio track
    private AudioTrack mRemoteAudioTrack = null;

    /**
     * Peer connection parameters.
     */
    public static class PeerConnectionParameters {
        public final boolean videoCallEnabled;
        public final boolean loopback;
        public final int videoWidth;
        public final int videoHeight;
        public final int videoFps;
        public final int videoStartBitrate;
        public final String videoCodec;
        public final boolean videoCodecHwAcceleration;
        public final int audioStartBitrate;
        public final String audioCodec;
        @SuppressWarnings("unused")
        public final boolean cpuOveruseDetection;

        @SuppressWarnings("SameParameterValue")
        public PeerConnectionParameters(
                boolean videoCallEnabled, boolean loopback,
                int videoWidth, int videoHeight, int videoFps, int videoStartBitrate,
                String videoCodec, boolean videoCodecHwAcceleration,
                int audioStartBitrate, String audioCodec,
                boolean cpuOveruseDetection) {
            this.videoCallEnabled = videoCallEnabled;
            this.loopback = loopback;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoStartBitrate = videoStartBitrate;
            this.videoCodec = videoCodec;
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
            this.audioStartBitrate = audioStartBitrate;
            this.audioCodec = audioCodec;
            this.cpuOveruseDetection = cpuOveruseDetection;
        }
    }

    /**
     * Peer connection events.
     */
    public interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        void onLocalDescription(final SessionDescription sdp);

        /**
         * Callback fired once local Ice candidate is generated.
         */
        void onIceCandidate(final IceCandidate candidate);

        /**
         * Callback fired once connection is established (IceConnectionState is NEW).
         */
        void onIceNew();

        /**
         * Callback fired once connection is established (IceConnectionState is CONNECTED).
         */
        void onIceConnected();

        /**
         * Callback fired once connection is checking (IceConnectionState is CHECKING).
         */
        void onIceChecking();

        /**
         * Callback fired once connection is completed (IceConnectionState is COMPLETED).
         */
        void onIceCompleted();

        /**
         * Callback fired once connection is closed (IceConnectionState is DISCONNECTED).
         */
        void onIceDisconnected();

        /**
         * Callback fired once connection is closed (IceConnectionState is CLOSED).
         */
        void onIceClosed();

        /**
         * Callback fired once peer connection is closed.
         */
        void onPeerConnectionClosed();

        /**
         * Callback fired once peer connection statistics is ready.
         */
        @SuppressWarnings("UnusedParameters")
        void onPeerConnectionStatsReady(final StatsReport[] reports);

        /**
         * Callback fired once peer connection error happened.
         */
        void onPeerConnectionError(final String description);
    }

    public PeerConnectionClient() {
        mExecutor = new LooperExecutor();
        // Looper thread is started once in private ctor and is used for all
        // peer connection API calls to ensure new peer connection factory is
        // created on the same thread as previously destroyed factory.
        mExecutor.requestStart();
    }

    @SuppressWarnings("unused")
    public static PeerConnectionClient getInstance() {
        return mInstance;
    }

    @SuppressWarnings("unused")
    public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
        this.mOptions = options;
    }

    public void createPeerConnectionFactory(
            final Context context,
            final EglBase.Context renderEGLContext,
            final PeerConnectionParameters peerConnectionParameters,
            final PeerConnectionEvents events) {
        this.mPeerConnectionParameters = peerConnectionParameters;
        this.mEvents = events;
        videoCallEnabled = mPeerConnectionParameters.videoCallEnabled;
        // Reset variables to initial states.
        mFactory = null;
        mPeerConnection = null;
        preferIsac = false;
        preferH264 = false;
        videoSourceStopped = false;
        isError = false;
        mQueuedRemoteCandidates = null;
        mLocalSdp = null; // either offer or answer SDP
        mMediaStream = null;
        mVideoCapturer = null;
        mRenderVideo = true;
        mLocalVideoTrack = null;
        mRemoteVideoTrack = null;
        mStatsTimer = new Timer();

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                createPeerConnectionFactoryInternal(context, renderEGLContext);
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    public void createPeerConnection(
            final VideoRenderer.Callbacks localRender,
            final VideoRenderer.Callbacks remoteRender,
            final SignalingParameters mSignalingParameters) {
        if (mPeerConnectionParameters == null) {
            log("Creating peer connection without initializing factory.");
            return;
        }
        this.mLocalRender = localRender;
        this.mRemoteRender = remoteRender;
        this.mSignalingParameters = mSignalingParameters;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                createMediaConstraintsInternal();
                createPeerConnectionInternal();
            }
        });
    }

    public void close() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                closeInternal();
            }
        });
    }

    public void closePeerConnection() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                closePeerConnectionInternal();
            }
        });
    }

    @SuppressWarnings("unused")
    public boolean isVideoCallEnabled() {
        return videoCallEnabled;
    }

    private void createPeerConnectionFactoryInternal(Context context, EglBase.Context renderEGLContext) {
        log("Create peer connection factory with EGLContext " + renderEGLContext + ". Use video: " + mPeerConnectionParameters.videoCallEnabled);
        isError = false;
        // Check if VP9 is used by default.
        if (videoCallEnabled && mPeerConnectionParameters.videoCodec != null && mPeerConnectionParameters.videoCodec.equals(VIDEO_CODEC_VP9)) {
            PeerConnectionFactory.initializeFieldTrials(FIELD_TRIAL_VP9);
        } else {
            PeerConnectionFactory.initializeFieldTrials(null);
        }
        // Check if H.264 is used by default.
        preferH264 = videoCallEnabled && mPeerConnectionParameters.videoCodec != null && mPeerConnectionParameters.videoCodec.equals(VIDEO_CODEC_H264);
        // Check if ISAC is used by default.
        preferIsac = mPeerConnectionParameters.audioCodec != null && mPeerConnectionParameters.audioCodec.equals(AUDIO_CODEC_ISAC);
        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, mPeerConnectionParameters.videoCodecHwAcceleration)) {
            mEvents.onPeerConnectionError("Failed to initializeAndroidGlobals");
        }
        mFactory = new PeerConnectionFactory();
        if (mOptions != null) {
            log("Factory networkIgnoreMask option: " + mOptions.networkIgnoreMask);
            mFactory.setOptions(mOptions);
        }
        log("Peer connection factory created.");
    }

    private void createMediaConstraintsInternal() {
        // Create peer connection constraints.
        mPcConstraints = new MediaConstraints();
        // Enable DTLS for normal calls and disable for loopback calls.
        if (mPeerConnectionParameters.loopback) {
            mPcConstraints.optional.add(new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "false"));
        } else {
            mPcConstraints.optional.add(new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
        }

        // Check if there is a camera on device and disable video call if not.
        mNumberOfCameras = Camera.getNumberOfCameras();
        if (mNumberOfCameras == 0) {
            log("No camera on device. Switch to audio only call.");
            videoCallEnabled = false;
        }
        // Create video constraints if video call is enabled.
        if (videoCallEnabled) {
            mVideoConstraints = new MediaConstraints();
            int videoWidth = mPeerConnectionParameters.videoWidth;
            int videoHeight = mPeerConnectionParameters.videoHeight;

            // If VP8 HW video encoder is supported and video resolution is not
            // specified force it to HD.
            if ((videoWidth == 0 || videoHeight == 0)
                    && mPeerConnectionParameters.videoCodecHwAcceleration
                    && MediaCodecVideoEncoder.isVp8HwSupported()) {
                videoWidth = HD_VIDEO_WIDTH;
                videoHeight = HD_VIDEO_HEIGHT;
            }

            // Add video resolution constraints.
            if (videoWidth > 0 && videoHeight > 0) {
                videoWidth = Math.min(videoWidth, MAX_VIDEO_WIDTH);
                videoHeight = Math.min(videoHeight, MAX_VIDEO_HEIGHT);
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MIN_VIDEO_WIDTH_CONSTRAINT, Integer.toString(videoWidth)));
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MAX_VIDEO_WIDTH_CONSTRAINT, Integer.toString(videoWidth)));
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MIN_VIDEO_HEIGHT_CONSTRAINT, Integer.toString(videoHeight)));
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MAX_VIDEO_HEIGHT_CONSTRAINT, Integer.toString(videoHeight)));
            }

            // Add fps constraints.
            int videoFps = mPeerConnectionParameters.videoFps;
            if (videoFps > 0) {
                videoFps = Math.min(videoFps, MAX_VIDEO_FPS);
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MIN_VIDEO_FPS_CONSTRAINT, Integer.toString(videoFps)));
                mVideoConstraints.mandatory.add(new KeyValuePair(
                        MAX_VIDEO_FPS_CONSTRAINT, Integer.toString(videoFps)));
            }
        }

        // Create audio constraints.
        mAudioConstraints = new MediaConstraints();

        // Create SDP constraints.
        mSdpMediaConstraints = new MediaConstraints();
        mSdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (videoCallEnabled || mPeerConnectionParameters.loopback) {
            mSdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        } else {
            mSdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        }
    }

    private void createPeerConnectionInternal() {
        if (mFactory == null || isError) {
            log("PeerConnection factory is not created");
            return;
        }
        log("Create peer connection");
        log("PCConstraints: " + mPcConstraints.toString());
        if (mVideoConstraints != null) {
            log("VideoConstraints: " + mVideoConstraints.toString());
        }
        mQueuedRemoteCandidates = new LinkedList<>();

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mSignalingParameters.iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;

        mPeerConnection = mFactory.createPeerConnection(rtcConfig, mPcConstraints, mPcObserver);
        mIsInitiator = false;

        // Set default WebRTC tracing and INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        // Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_DEFAULT), Logging.Severity.LS_INFO);

        mMediaStream = mFactory.createLocalMediaStream("ARDAMS");
        if (videoCallEnabled) {

            String cameraDeviceName = DeviceNameHandler.getDeviceName(0);
            if (useFrontFacingCamera) {
                log("trying to use front facing camera");
                String frontCameraDeviceName = DeviceNameHandler.getNameOfFrontFacingDevice();
                if (mNumberOfCameras > 1 && frontCameraDeviceName != null) {
                    cameraDeviceName = frontCameraDeviceName;
                }
            } else {
                log("trying to use back facing camera");
            }
            log("Opening camera: " + cameraDeviceName);

            mVideoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null);
            if (mVideoCapturer == null) {
                log("Failed to open camera");
                //reportError("Failed to open camera");
                //return;
            } else {
                mMediaStream.addTrack(createVideoTrack(mVideoCapturer));
            }
        }

        mMediaStream.addTrack(mFactory.createAudioTrack(AUDIO_TRACK_ID, mFactory.createAudioSource(mAudioConstraints)));
        mPeerConnection.addStream(mMediaStream);

        log("Peer connection created.");
    }


    private void closeInternal() {
        log("closeInternal()");
        log("Closing peer connection.");
        mStatsTimer.cancel();
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
        log("Closing video source.");
        if (mVideoSource != null) {
            mVideoSource.dispose();
            mVideoSource = null;
        }
        log("Closing peer connection factory.");
        if (mFactory != null) {
            mFactory.dispose();
            mFactory = null;
        }
        mOptions = null;
        mRemoteAudioTrack = null;
        log("Closing peer connection done.");
        mEvents.onPeerConnectionClosed();
    }

    private void closePeerConnectionInternal() {
        log("closePeerConnectionInternal()");
        log("Closing peer connection.");
        mStatsTimer.cancel();
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
        log("Closing video source.");
        if (mVideoSource != null) {
            mVideoSource.dispose();
            mVideoSource = null;
        }
        isError = false;
        mLocalSdp = null;
        mRemoteAudioTrack = null;
        log("Closing peer connection done.");
        mEvents.onPeerConnectionClosed();
    }

    @SuppressWarnings("unused")
    public boolean isHDVideo() {
        if (!videoCallEnabled) {
            return false;
        }
        int minWidth = 0;
        int minHeight = 0;
        if (mVideoConstraints != null) {
            for (KeyValuePair keyValuePair : mVideoConstraints.mandatory) {
                if (keyValuePair.getKey().equals("minWidth")) {
                    try {
                        minWidth = Integer.parseInt(keyValuePair.getValue());
                    } catch (NumberFormatException e) {
                        log("Can not parse video width from video constraints");
                    }
                } else if (keyValuePair.getKey().equals("minHeight")) {
                    try {
                        minHeight = Integer.parseInt(keyValuePair.getValue());
                    } catch (NumberFormatException e) {
                        log("Can not parse video height from video constraints");
                    }
                }
            }
        }
        return minWidth * minHeight >= 1280 * 720;
    }

    private void getStats() {
        if (mPeerConnection == null || isError) {
            return;
        }
        boolean success = mPeerConnection.getStats(new StatsObserver() {
            @Override
            public void onComplete(final StatsReport[] reports) {
                mEvents.onPeerConnectionStatsReady(reports);
            }
        }, null);
        if (!success) {
            log("getStats() returns false!");
        }
    }

    @SuppressWarnings("unused")
    public void enableStatsEvents(boolean enable, int periodMs) {
        if (enable) {
            try {
                mStatsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                getStats();
                            }
                        });
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                log("Can not schedule statistics timer");
                if (D) {
                    e.printStackTrace();
                }
            }
        } else {
            mStatsTimer.cancel();
        }
    }

    @SuppressWarnings("unused")
    public void setVideoEnabled(final boolean enable) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mRenderVideo = enable;
                if (mLocalVideoTrack != null) {
                    mLocalVideoTrack.setEnabled(mRenderVideo);
                }
                if (mRemoteVideoTrack != null) {
                    mRemoteVideoTrack.setEnabled(mRenderVideo);
                }
            }
        });
    }

    public void createOffer() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnection != null && !isError) {
                    log("PC Create OFFER");
                    mIsInitiator = true;
                    mPeerConnection.createOffer(mSdpObserver, mSdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnection != null && !isError) {
                    log("PC create ANSWER");
                    mIsInitiator = false;
                    mPeerConnection.createAnswer(mSdpObserver, mSdpMediaConstraints);
                }
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnection != null && !isError) {
                    if (mQueuedRemoteCandidates != null) {
                        mQueuedRemoteCandidates.add(candidate);
                    } else {
                        mPeerConnection.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnection == null || isError) {
                    return;
                }
                String sdpDescription = sdp.description;
                if (preferIsac) {
                    sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
                }
                if (videoCallEnabled && preferH264) {
                    sdpDescription = preferCodec(sdpDescription, VIDEO_CODEC_H264, false);
                }
                if (videoCallEnabled && mPeerConnectionParameters.videoStartBitrate > 0) {
                    sdpDescription = setStartBitrate(VIDEO_CODEC_VP8, true,
                            sdpDescription, mPeerConnectionParameters.videoStartBitrate);
                    sdpDescription = setStartBitrate(VIDEO_CODEC_VP9, true,
                            sdpDescription, mPeerConnectionParameters.videoStartBitrate);
                    sdpDescription = setStartBitrate(VIDEO_CODEC_H264, true,
                            sdpDescription, mPeerConnectionParameters.videoStartBitrate);
                }
                if (mPeerConnectionParameters.audioStartBitrate > 0) {
                    sdpDescription = setStartBitrate(AUDIO_CODEC_OPUS, false,
                            sdpDescription, mPeerConnectionParameters.audioStartBitrate);
                }
                log("Set remote SDP.");
                SessionDescription sdpRemote = new SessionDescription(
                        sdp.type, sdpDescription);
                mPeerConnection.setRemoteDescription(mSdpObserver, sdpRemote);
            }
        });
    }

    public void stopVideoSource() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mVideoSource != null && !videoSourceStopped) {
                    log("Stop video source.");
                    mVideoSource.stop();
                    videoSourceStopped = true;
                }
            }
        });
    }

    public void startVideoSource() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mVideoSource != null && videoSourceStopped) {
                    log("Restart video source.");
                    mVideoSource.restart();
                    videoSourceStopped = false;
                }
            }
        });
    }

    private void reportError(final String errorMessage) {
        log("PeerConnection error: " + errorMessage);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    mEvents.onPeerConnectionError(errorMessage);
                    isError = true;
                }
            }
        });
    }

    private VideoTrack createVideoTrack(VideoCapturerAndroid capturer) {
        mVideoSource = mFactory.createVideoSource(capturer, mVideoConstraints);

        mLocalVideoTrack = mFactory.createVideoTrack(VIDEO_TRACK_ID, mVideoSource);
        mLocalVideoTrack.setEnabled(mRenderVideo);
        mLocalVideoTrack.addRenderer(new VideoRenderer(mLocalRender));
        return mLocalVideoTrack;
    }

    private static String setStartBitrate(String codec, boolean isVideoCodec,
                                          String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            log("No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        log("Found " + codec + " rtpmap " + codecRtpMap
                + " at " + lines[rtpmapLineIndex]);

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                log("Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE
                            + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE
                            + "=" + (bitrateKbps * 1000);
                }
                log("Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " "
                            + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " "
                            + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                log("Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }

        }
        return newSdpDescription.toString();
    }

    private static String preferCodec(
            String sdpDescription, String codec, boolean isAudio) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        String mediaDescription = "m=video ";
        if (isAudio) {
            mediaDescription = "m=audio ";
        }
        for (int i = 0; (i < lines.length)
                && (mLineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                mLineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
            }
        }
        if (mLineIndex == -1) {
            log("No " + mediaDescription + " line, so can't prefer " + codec);
            return sdpDescription;
        }
        if (codecRtpMap == null) {
            log("No rtpmap for " + codec);
            return sdpDescription;
        }
        log("Found " + codec + " rtpmap " + codecRtpMap + ", prefer at "
                + lines[mLineIndex]);
        String[] origMLineParts = lines[mLineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newMLine = new StringBuilder();
            int origPartIndex = 0;
            // Format is: m=<media> <port> <proto> <fmt> ...
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(codecRtpMap);
            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
                }
            }
            lines[mLineIndex] = newMLine.toString();
            log("Change media description: " + lines[mLineIndex]);
        } else {
            log("Wrong SDP media description format: " + lines[mLineIndex]);
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    private void drainCandidates() {
        if (mQueuedRemoteCandidates != null) {
            log("Add " + mQueuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : mQueuedRemoteCandidates) {
                mPeerConnection.addIceCandidate(candidate);
            }
            mQueuedRemoteCandidates = null;
        }
    }

    private void switchCameraInternal() {
        if (!videoCallEnabled || mNumberOfCameras < 2 || isError || mVideoCapturer == null) {
            log("Failed to switch camera. Video: " + videoCallEnabled + ". Error : "
                    + isError + ". Number of cameras: " + mNumberOfCameras);
            return;  // No video is sent or only one camera is available or error happened.
        }
        log("Switch camera");
        mVideoCapturer.switchCamera(null);
    }

    @SuppressWarnings("unused")
    public void switchCamera() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                switchCameraInternal();
            }
        });
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mEvents.onIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onSignalingChange(
                PeerConnection.SignalingState newState) {
            log("SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(
                final PeerConnection.IceConnectionState newState) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    log("IceConnectionState: " + newState);
                    if (newState == IceConnectionState.NEW) {
                        mEvents.onIceNew();
                    } else if (newState == IceConnectionState.CHECKING) {
                        mEvents.onIceChecking();
                    } else if (newState == IceConnectionState.CONNECTED) {
                        mEvents.onIceConnected();
                    } else if (newState == IceConnectionState.COMPLETED) {
                        mEvents.onIceCompleted();
                    } else if (newState == IceConnectionState.DISCONNECTED) {
                        // NOTE close peer connection
                        //closePeerConnectionInternal();
                        mEvents.onIceDisconnected();
                    } else if (newState == IceConnectionState.CLOSED) {
                        mEvents.onIceClosed();
                    } else if (newState == IceConnectionState.FAILED) {
                        // NOTE close peer connection
                        //closePeerConnectionInternal();
                        mEvents.onPeerConnectionError("ICE connection failed.");
                        //reportError("ICE connection failed.");
                    }
                }
            });
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(
                PeerConnection.IceGatheringState newState) {
            log("IceGatheringState: " + newState);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mPeerConnection == null || isError) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        reportError("Weird-looking stream: " + stream);
                        return;
                    }
                    // Remote audio track
                    if (stream.audioTracks.size() != 0) {
                        mRemoteAudioTrack = stream.audioTracks.get(0);
                    }
                    if (stream.videoTracks.size() == 1) {
                        // Check needed
                        if (mRemoteRender != null) {
                            mRemoteVideoTrack = stream.videoTracks.get(0);
                            mRemoteVideoTrack.setEnabled(mRenderVideo);
                            mRemoteVideoTrack.addRenderer(new VideoRenderer(mRemoteRender));
                        }
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mPeerConnection == null || isError) {
                        return;
                    }
                    mRemoteVideoTrack = null;
                    stream.videoTracks.get(0).dispose();
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            // No need to do anything
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything
        }
    }

    // Implementation detail: handle offer creation/signaling and answer setting,
    // as well as adding remote ICE candidates once the answer SDP is set.
    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            if (mLocalSdp != null) {
                reportError("Multiple SDP create.");
                return;
            }
            String sdpDescription = origSdp.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            }
            if (videoCallEnabled && preferH264) {
                sdpDescription = preferCodec(sdpDescription, VIDEO_CODEC_H264, false);
            }
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            mLocalSdp = sdp;
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mPeerConnection != null && !isError) {
                        log("Set local SDP from " + sdp.type);
                        mPeerConnection.setLocalDescription(mSdpObserver, sdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mPeerConnection == null || isError) {
                        return;
                    }
                    if (mIsInitiator) {
                        // For offering peer connection we first create offer and set
                        // local SDP, then after receiving answer set remote SDP.
                        if (mPeerConnection.getRemoteDescription() == null) {
                            // We've just set our local SDP so time to send it.
                            log("Local SDP set successfully");
                            mEvents.onLocalDescription(mLocalSdp);
                        } else {
                            // We've just set remote description, so drain remote
                            // and send local ICE candidates.
                            log("Remote SDP set successfully");
                            drainCandidates();
                        }
                    } else {
                        // For answering peer connection we set remote SDP and then
                        // create answer and set local SDP.
                        if (mPeerConnection.getLocalDescription() != null) {
                            // We've just set our local SDP so time to send it, drain
                            // remote and send local ICE candidates.
                            log("Local SDP set successfully");
                            mEvents.onLocalDescription(mLocalSdp);
                            drainCandidates();
                        } else {
                            // We've just set remote SDP - do nothing for now -
                            // answer will be created soon.
                            log("Remote SDP set successfully");
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            reportError("setSDP error: " + error);
        }
    }

    public void muteRemoteAudioStream() {
        if (mRemoteAudioTrack != null) {
            mRemoteAudioTrack.setEnabled(false);
        }
    }

    public void unmuteRemoteAudioStream() {
        if (mRemoteAudioTrack != null) {
            mRemoteAudioTrack.setEnabled(true);
        }
    }

    /**
     * Logs out msg
     *
     * @param msg String
     */
    private static void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}

