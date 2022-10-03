package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.ui.AppBarConfiguration
import com.example.myapplication.databinding.ActivityMainBinding
import io.agora.mediaplayer.Constants.*
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediaplayer.IMediaPlayerObserver
import io.agora.mediaplayer.data.PlayerUpdatedInfo
import io.agora.mediaplayer.data.SrcInfo
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas


// Fill the App ID of your project generated on Agora Console.
private const val appId = "49e4c597ad1d4cf2bfc97451e4c5f67a"

// Fill the channel name.
private const val channelName = "testGaming"

// Fill the temp token generated on Agora Console.
private const val token = "007eJxTYHhb6mhkOeWpK+unS0XPuHLXNr/7dXeJwsSDT38snMfI8O+VAoOJZapJsqmleWKKYYpJcppRUlqypbmJqSFINM3MPHHGX/NkCX/LZIazy1kYGSAQxOdiKEktLnFPzM3MS2dgAAAnjyW0"

// An integer that identifies the local user.
private val uid = 0
private var isJoined = false

private var agoraEngine: RtcEngine? = null

//SurfaceView to render local video in a Container.
private var localSurfaceView: SurfaceView? = null

//SurfaceView to render Remote video in a Container.
private var remoteSurfaceView: SurfaceView? = null

private var mediaPlayer // Instance of the media player
        : IMediaPlayer? = null
private var isMediaPlaying = false
private var mediaDuration: Long = 0

// In a real world app, you declare the media location variable with an empty string
// and update it when a user chooses a media file from a local or remote source.
private const val mediaLocation = "https://www.appsloveworld.com/wp-content/uploads/2018/10/640.mp4"

private var mediaButton: Button? = null
private var mediaProgressBar: ProgressBar? = null

// Volume Control
private var volumeSeekBar: SeekBar? = null
private var muteCheckBox: CheckBox? = null
private var volume = 50
private var remoteUid = 0 // Stores the uid of the remote user


// Screen sharing
private const val DEFAULT_SHARE_FRAME_RATE = 10
private var isSharingScreen = false
private var fgServiceIntent: Intent? = null


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private fun checkSelfPermission(): Boolean {
        return !(ContextCompat.checkSelfPermission(
            this,
            REQUESTED_PERMISSIONS[0]
        ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    REQUESTED_PERMISSIONS[1]
                ) != PackageManager.PERMISSION_GRANTED)
    }


    fun showMessage(message: String?) {
        runOnUiThread {
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupVideoSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            // By default, the video module is disabled, call enableVideo to enable it.
            agoraEngine!!.enableVideo()
        } catch (e: Exception) {
            showMessage(e.toString())
        }
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        // Listen for the remote host joining the channel to get the uid of the host.
        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteUid = uid;
            showMessage("Remote user joined $uid")

            // Set the remote video view
            runOnUiThread { setupRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined Channel $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("Remote user offline $uid $reason")
            runOnUiThread { remoteSurfaceView!!.visibility = View.GONE }
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        val container = findViewById<FrameLayout>(R.id.remote_video_view_container)
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView!!.setZOrderMediaOverlay(true)
        container.addView(remoteSurfaceView)
        agoraEngine!!.setupRemoteVideo(
            VideoCanvas(
                remoteSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                uid
            )
        )
        // Display RemoteSurfaceView.
        remoteSurfaceView!!.setVisibility(View.VISIBLE)
    }

    /*private fun setupLocalVideo() {
        val container = findViewById<FrameLayout>(R.id.local_video_view_container)
        // Create a SurfaceView object and add it as a child to the FrameLayout.
        localSurfaceView = SurfaceView(baseContext)
        container.addView(localSurfaceView)
        // Pass the SurfaceView object to Agora so that it renders the local video.
        agoraEngine!!.setupLocalVideo(
            VideoCanvas(
                localSurfaceView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                0
            )
        )
    }*/

    private fun setupLocalVideo(forMediaPlayer: Boolean) {
        val container = findViewById<FrameLayout>(R.id.local_video_view_container)
        container.removeAllViews()

        // Create a SurfaceView object and add it as a child to the FrameLayout.
        localSurfaceView = SurfaceView(baseContext)
        container.addView(localSurfaceView)

        // Pass the SurfaceView object to the engine so that it renders the local video.
        if (forMediaPlayer) {
            val videoCanvas = VideoCanvas(
                localSurfaceView, Constants.RENDER_MODE_HIDDEN, Constants.VIDEO_MIRROR_MODE_AUTO,
                Constants.VIDEO_SOURCE_MEDIA_PLAYER, mediaPlayer!!.mediaPlayerId, 0
            )
            agoraEngine!!.setupLocalVideo(videoCanvas)
        } else {
            agoraEngine!!.setupLocalVideo(
                VideoCanvas(
                    localSurfaceView,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    0
                )
            )
        }
    }


    fun joinChannel(view: View?) {
        if (checkSelfPermission()) {
            val options = ChannelMediaOptions()

            // For a Video call, set the channel profile as COMMUNICATION.
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            // Set the client role as BROADCASTER or AUDIENCE according to the scenario.
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            // Display LocalSurfaceView.
            setupLocalVideo(false);
            localSurfaceView!!.visibility = View.VISIBLE
            // Start local preview.
            agoraEngine!!.startPreview()
            // Join the channel with a temp token.
            // You need to specify the user ID yourself, and ensure that it is unique in the channel.
            agoraEngine!!.joinChannel(token, channelName, uid, options)
        } else {
            Toast.makeText(applicationContext, "Permissions was not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun leaveChannel(view: View?) {
        if (!isJoined) {
            showMessage("Join a channel first")
        } else {
            agoraEngine!!.leaveChannel()
            showMessage("You left the channel")
            // Stop remote video rendering.
            if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = View.GONE
            // Stop local video rendering.
            if (localSurfaceView != null) localSurfaceView!!.visibility = View.GONE
            isJoined = false
        }
    }

    fun playMedia(view: View) {
        if (mediaButton == null) mediaButton = view as Button

        // Initialize the mediaPlayer and open a media file
        if (mediaPlayer == null) {
            // Create an instance of the media player
            mediaPlayer = agoraEngine!!.createMediaPlayer()
            // Set the mediaPlayerObserver to receive callbacks
            mediaPlayer!!.registerPlayerObserver(mediaPlayerObserver)
            // Open the media file
            mediaPlayer!!.open(mediaLocation, 0)
            mediaButton!!.setEnabled(false)
            mediaButton!!.setText("Opening Media File...")
            return
        }

        // Set up the local video container to handle the media player output
        // or the camera stream, alternately.
        isMediaPlaying = !isMediaPlaying
        // Set the stream publishing options
        updateChannelPublishOptions(isMediaPlaying)
        // Display the stream locally
        setupLocalVideo(isMediaPlaying)
        val state = mediaPlayer!!.getState()
        if (isMediaPlaying) { // Start or resume playing media
            if (state == MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                mediaPlayer!!.play()
            } else if (state == MediaPlayerState.PLAYER_STATE_PAUSED) {
                mediaPlayer!!.resume()
            }
            mediaButton!!.setText("Pause Playing Media")
        } else {
            if (state == MediaPlayerState.PLAYER_STATE_PLAYING) {
                // Pause media file
                mediaPlayer!!.pause()
                mediaButton!!.setText("Resume Playing Media")
            }
        }
    }


    private val mediaPlayerObserver: IMediaPlayerObserver = object : IMediaPlayerObserver {
        override fun onPlayerStateChanged(state: MediaPlayerState, error: MediaPlayerError) {
            showMessage(state.toString())
            if (state == MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                // Media file opened successfully
                mediaDuration = mediaPlayer!!.duration
                // Update the UI
                runOnUiThread {
                    mediaButton!!.text = "Play Media File"
                    mediaButton!!.isEnabled = true
                    mediaProgressBar!!.progress = 0
                }
            } else if (state == MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED) {
                isMediaPlaying = false
                // Media file finished playing
                runOnUiThread {
                    mediaButton!!.text = "Load Media File"
                    // Restore camera and microphone streams
                    setupLocalVideo(false)
                    updateChannelPublishOptions(false)
                    // Clean up
                    mediaPlayer!!.destroy()
                    mediaPlayer = null
                }
            }
        }

        override fun onPositionChanged(position: Long) {
            if (mediaDuration > 0) {
                val result = (position.toFloat() / mediaDuration.toFloat() * 100).toInt()
                runOnUiThread {
                    // Update the ProgressBar
                    mediaProgressBar!!.progress = java.lang.Long.valueOf(result.toLong()).toInt()
                }
            }
        }

        override fun onPlayerEvent(
            eventCode: MediaPlayerEvent,
            elapsedTime: Long,
            message: String
        ) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onMetaData(type: MediaPlayerMetadataType, data: ByteArray) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onPlayBufferUpdated(playCachedBuffer: Long) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onPreloadEvent(src: String, event: MediaPlayerPreloadEvent) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onAgoraCDNTokenWillExpire() {
            // Required to implement IMediaPlayerObserver
        }

        override fun onPlayerSrcInfoChanged(from: SrcInfo, to: SrcInfo) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onPlayerInfoUpdated(info: PlayerUpdatedInfo) {
            // Required to implement IMediaPlayerObserver
        }

        override fun onAudioVolumeIndication(volume: Int) {
            // Required to implement IMediaPlayerObserver
        }
    }

    private fun updateChannelPublishOptions(publishMediaPlayer: Boolean) {
        val channelOptions = ChannelMediaOptions()
        channelOptions.publishMediaPlayerAudioTrack = publishMediaPlayer
        channelOptions.publishMediaPlayerVideoTrack = publishMediaPlayer
        channelOptions.publishMicrophoneTrack = !publishMediaPlayer
        channelOptions.publishCameraTrack = !publishMediaPlayer
        if (publishMediaPlayer) channelOptions.publishMediaPlayerId = mediaPlayer!!.mediaPlayerId
        agoraEngine!!.updateChannelMediaOptions(channelOptions)
    }

    fun shareScreen(view: View) {
        val sharingButton = view as Button
        if (!isSharingScreen) { // Start sharing
            // Ensure that your Android version is Lollipop or higher.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    fgServiceIntent = Intent(this, MainActivity::class.java)
                    startForegroundService(fgServiceIntent)
                }
                // Get the screen metrics
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)

                // Set screen capture parameters
                val screenCaptureParameters = ScreenCaptureParameters()
                screenCaptureParameters.captureVideo = true
                screenCaptureParameters.videoCaptureParameters.width = metrics.widthPixels
                screenCaptureParameters.videoCaptureParameters.height = metrics.heightPixels
                screenCaptureParameters.videoCaptureParameters.framerate = DEFAULT_SHARE_FRAME_RATE
                screenCaptureParameters.captureAudio = true
                screenCaptureParameters.audioCaptureParameters.captureSignalVolume = 50

                // Start screen sharing
                agoraEngine!!.startScreenCapture(screenCaptureParameters)
                isSharingScreen = true
                startScreenSharePreview()
                // Update channel media options to publish the screen sharing video stream
                updateMediaPublishOptions(true)
                sharingButton.text = "Stop Screen Sharing"
            }
        } else { // Stop sharing
            agoraEngine!!.stopScreenCapture()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (fgServiceIntent != null) stopService(fgServiceIntent)
            }
            isSharingScreen = false
            sharingButton.text = "Start Screen Sharing"

            // Restore camera and microphone publishing
            updateMediaPublishOptions(false)
            setupLocalVideo(false)
        }
    }

    /*fun chat(view: View) {
        val chatButton = view as Button
        val chatIntent = Intent(this, FullscreenActivity::class.java)
        startActivity(chatIntent)
    }*/

    private fun startScreenSharePreview() {
        // Create render view by RtcEngine
        val container = findViewById<FrameLayout>(R.id.local_video_view_container)
        val surfaceView = SurfaceView(baseContext)
        if (container.childCount > 0) {
            container.removeAllViews()
        }
        // Add to the local container
        container.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // Setup local video to render your local camera preview
        agoraEngine!!.setupLocalVideo(
            VideoCanvas(
                surfaceView, Constants.RENDER_MODE_FIT,
                Constants.VIDEO_MIRROR_MODE_DISABLED,
                Constants.VIDEO_SOURCE_SCREEN_PRIMARY,
                0
            )
        )
        agoraEngine!!.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY)
    }

    fun updateMediaPublishOptions(publishScreen: Boolean) {
        val mediaOptions = ChannelMediaOptions()
        mediaOptions.publishCameraTrack = !publishScreen
        mediaOptions.publishMicrophoneTrack = !publishScreen
        mediaOptions.publishScreenCaptureVideo = publishScreen
        mediaOptions.publishScreenCaptureAudio = publishScreen
        agoraEngine!!.updateChannelMediaOptions(mediaOptions)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // If all the permissions are granted, initialize the RtcEngine object and join a channel.
        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }
        mediaProgressBar = findViewById(R.id.MediaProgress);
        setupVideoSDKEngine()
        volumeSeekBar = findViewById<View>(R.id.volumeSeekBar) as SeekBar
        volumeSeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                volume = progress
                agoraEngine!!.adjustRecordingSignalVolume(volume)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                //Required to implement OnSeekBarChangeListener
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //Required to implement OnSeekBarChangeListener
            }
        })

        muteCheckBox = findViewById<View>(R.id.muteCheckBox) as CheckBox
        muteCheckBox!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            agoraEngine!!.muteRemoteAudioStream(
                remoteUid,
                isChecked
            )
        })

    }

    override fun onDestroy() {
        super.onDestroy()
        // Destroy the media player
        mediaPlayer!!.stop()
        mediaPlayer!!.unRegisterPlayerObserver(mediaPlayerObserver);
        mediaPlayer!!.destroy();

        agoraEngine!!.stopPreview()
        agoraEngine!!.leaveChannel()

        // Destroy the engine in a sub-thread to avoid congestion
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }
}