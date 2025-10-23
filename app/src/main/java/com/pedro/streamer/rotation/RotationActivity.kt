package com.pedro.streamer.rotation

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.api.services.youtube.YouTube
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.streamer.MainActivity
import com.pedro.streamer.R
import com.pedro.streamer.utils.FilterMenu
import com.pedro.streamer.utils.setColor
import com.pedro.streamer.utils.toast


/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RotationActivity : AppCompatActivity(), OnTouchListener {

  private lateinit var cameraFragment: CameraFragment
  private val filterMenu: FilterMenu by lazy { FilterMenu(this) }
  private var currentVideoSource: MenuItem? = null
  private var currentAudioSource: MenuItem? = null
  private var currentOrientation: MenuItem? = null
  private var currentFilter: MenuItem? = null
  var youtubeService: YouTube? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    setContentView(R.layout.rotation_activity)

    youtubeService = MainActivity.YoutubeServiceManager.youtubeService

    val selectedTeams = intent.getParcelableArrayListExtra<Teams>("selectedTeams")
    val rtmpUrl = intent.getStringExtra("rtmpUrl")
    val matchTime = intent.getStringExtra("matchTime")
    val halfTime = intent.getStringExtra("halfTime")
    val halfObjectiveScore = intent.getStringExtra("halfObjectiveScore")
    val resolution = intent.getStringExtra("resolution")
    val mixedCheckBox = intent.getBooleanExtra("mixedCheckBox", false)
    val liveChatId = intent.getStringExtra("liveChatId")
    cameraFragment = CameraFragment.newInstance(selectedTeams, rtmpUrl, matchTime, halfTime, halfObjectiveScore, resolution, mixedCheckBox, liveChatId)
    selectedTeams?.let {
      // Maneja los equipos seleccionados
      for (team in it) {
        toast("Team: ${team.name}")
      }
    }
    supportFragmentManager.beginTransaction().add(R.id.container, cameraFragment).commit()
    com.pedro.library.view.OpenGlView(this)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    try {
      when (item.itemId) {
        R.id.video_source_camera1 -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          cameraFragment.genericStream.changeVideoSource(Camera1Source(applicationContext))
        }
        R.id.video_source_camera2 -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          cameraFragment.genericStream.changeVideoSource(Camera2Source(applicationContext))
        }
        R.id.video_source_bitmap -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
          cameraFragment.genericStream.changeVideoSource(BitmapSource(bitmap))
        }
        R.id.audio_source_microphone -> {
          currentAudioSource = updateMenuColor(currentAudioSource, item)
          cameraFragment.genericStream.changeAudioSource(MicrophoneSource())
        }
        R.id.orientation_horizontal -> {
          currentOrientation = updateMenuColor(currentOrientation, item)
          cameraFragment.setOrientationMode(false)
        }
        R.id.orientation_vertical -> {
          currentOrientation = updateMenuColor(currentOrientation, item)
          cameraFragment.setOrientationMode(true)
        }
        else -> {
          val result = filterMenu.onOptionsItemSelected(item, cameraFragment.genericStream.getGlInterface())
          if (result) currentFilter = updateMenuColor(currentFilter, item)
          return result
        }
      }
    } catch (e: IllegalArgumentException) {
      toast("Change source error: ${e.message}")
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
    if (filterMenu.spriteGestureController.spriteTouched(view, motionEvent)) {
      filterMenu.spriteGestureController.moveSprite(view, motionEvent)
      filterMenu.spriteGestureController.scaleSprite(motionEvent)
      return true
    }
    return false
  }

  private fun updateMenuColor(currentItem: MenuItem?, item: MenuItem): MenuItem {
    currentItem?.setColor(this, R.color.black)
    item.setColor(this, R.color.appColor)
    return item
  }
}