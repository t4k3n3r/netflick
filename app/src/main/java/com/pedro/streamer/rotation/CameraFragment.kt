
package com.pedro.streamer.rotation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.api.services.youtube.model.LiveChatMessage
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.streamer.R
import com.pedro.streamer.utils.LiveChatManager
import com.pedro.streamer.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraFragment: Fragment(), ConnectChecker {
  private lateinit var team1: Teams
  private lateinit var team2: Teams
  private lateinit var rtmpUrl: String
  private lateinit var resolution: String
  private lateinit var matchTime: String
  private var mixedCheckBox by Delegates.notNull<Boolean>()

  companion object {
    private const val ARG_TEAMS = "selectedTeams"

    fun newInstance(
      selectedTeams: ArrayList<Teams>?,
      rtmpUrl: String?,
      matchTime: String?,
      resolution: String?,
      mixedCheckBox: Boolean,
      liveChatId: String?

    ): CameraFragment {
      return CameraFragment().apply {
        arguments = Bundle().apply {
          putParcelableArrayList(ARG_TEAMS, selectedTeams)
          putString("rtmpUrl", rtmpUrl)
          putString("matchTime", matchTime)
          putString("resolution", resolution)
          putBoolean("mixedCheckBox", mixedCheckBox)
          putString("liveChatId", liveChatId)
        }
      }
    }
  }

  val genericStream: GenericStream by lazy {
    GenericStream(requireContext(), this).apply {
      getGlInterface().autoHandleOrientation = true
    }
  }
  private lateinit var surfaceView: SurfaceView
  private lateinit var bStartStop: ImageView
  private lateinit var countdownButton: Button
  private lateinit var femaleButton: Button
  private lateinit var maleButton: Button
  private lateinit var countdownPauseButton: Button
  private val scoreX = 21f
  private val scoreboardHeight = 7f
  private val textSize = 50f

  //This resolution also works
  private var width = 1920
  private var height = 1080
  private var vBitrate = 3500 * 1024
  //private lateinit var countdownTextView: TextView
  /*private val width = 640
  private val height = 480
  private val vBitrate = 1200 * 1000*/

  private var rotation = 0
  private val sampleRate = 32000
  private val isStereo = true
  private val aBitrate = 128 * 1000
  //private var recordPath = ""

  private var localScoreTextObjectFilterRender: TextObjectFilterRender? = null
  private var visitorScoreTextObjectFilterRender: TextObjectFilterRender? = null
  private var countdownTextFilterRender = TextObjectFilterRender()
  private var localScore = 0
  private var visitorScore = 0
  private var timeLeftInMillis: Long = 0L
  private var isPaused: Boolean = false
  private var countDownTimer: CountDownTimer? = null
  private lateinit var zoomSeekBar: SeekBar
  private var maxZoom: Float = 1.0f
  private var minZoom: Float = 1.0f
  private var backCameraIds: List<String> = listOf()
  private var currentBackCameraIndex: Int = 0
  private val womanObjectFilterRender = ImageObjectFilterRender()
  private val manObjectFilterRender = ImageObjectFilterRender()
  private var man: Boolean = false
  private var plus: Boolean = true
  private lateinit var liveChatTextView: TextView
  private lateinit var liveChatManager: LiveChatManager
  //private var switchBackCameraButton = view.findViewById<ImageView>(R.id.switch_camera)

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_camera, container, false)
    bStartStop = view.findViewById(R.id.b_start_stop)
    //val bSwitchCamera = view.findViewById<ImageView>(R.id.switch_camera_frontal)
    val switchBackCameraButton = view.findViewById<ImageView>(R.id.switch_camera)
    //val etUrl = view.findViewById<EditText>(R.id.et_rtp_url)
    //man = view.findViewById<ImageView>(R.id.man)
    //woman = view.findViewById<ImageView>(R.id.woman)
    femaleButton = view.findViewById<Button>(R.id.female_button)
    maleButton = view.findViewById<Button>(R.id.male_button)
    val localPlus = view.findViewById<Button>(R.id.local_button_plus)
    val localMinus = view.findViewById<Button>(R.id.local_button_minus)
    val visitorPlus = view.findViewById<Button>(R.id.visitor_button_plus)
    val visitorMinus = view.findViewById<Button>(R.id.visitor_button_minus)
    val localName = view.findViewById<TextView>(R.id.text_local_name)
    val visitorName = view.findViewById<TextView>(R.id.text_visitor_name)
    localName.text = team1.name
    visitorName.text = team2.name
    liveChatTextView = view.findViewById(R.id.liveChatTextView)
    liveChatTextView.movementMethod = ScrollingMovementMethod()
    startLoadingComments()

    val youtubeService = (activity as? RotationActivity)?.youtubeService
    val liveChatId = arguments?.getString("liveChatId")

    if (youtubeService != null && liveChatId != null) {
      liveChatManager = LiveChatManager(youtubeService, liveChatId)
      fetchLiveChatMessages()
    }

    surfaceView = view.findViewById(R.id.surfaceView)
    (activity as? RotationActivity)?.let {
      surfaceView.setOnTouchListener(it)
    }
    surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
        if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        //var width = 2560
        //var height = 1440
        genericStream.getGlInterface().setPreviewResolution(width, height)
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (genericStream.isOnPreview) genericStream.stopPreview()
      }

    })


    bStartStop.setOnClickListener {
      if (!genericStream.isStreaming) {
        genericStream.startStream(rtmpUrl)
        bStartStop.setImageResource(R.drawable.stream_stop_icon)
      } else {
        genericStream.stopStream()
        bStartStop.setImageResource(R.drawable.stream_icon)
      }
    }

    // Añade el logo del oponente a la emisión
    val localLogoObjectFilterRender = ImageObjectFilterRender()
    val localBitmap = BitmapFactory.decodeFile(team1.logoPath)

    // Stream resolution (por example 480x640 portrait mode)
    val streamWidth = 1920f
    val streamHeight = 1080f

    // Permitted max
    val maxHeight = 7f
    val maxWidth = 6f

    // Configure the image in the render
    localLogoObjectFilterRender.setImage(localBitmap)
    localLogoObjectFilterRender.setReScale(streamHeight, streamWidth, maxWidth, maxHeight, 2F, 1F)

    val teamNameWidth = 12f
    val scoreWidth = 2f
    // val scoreboardHeight = 7f
    val teamNameX = 8f
    //val scoreX = 21f
    val localY = 1f
    val visitorY = 8f

    //Add local text to the stream
    val localTextObjectFilterRender = TextObjectFilterRender()
    localTextObjectFilterRender.setText(team1.name, textSize, Color.BLACK)
    localTextObjectFilterRender.setScale(teamNameWidth, scoreboardHeight)
    localTextObjectFilterRender.setPosition(teamNameX, localY)

    //Add local score text to the stream
    localScoreTextObjectFilterRender = TextObjectFilterRender().apply {
      setText("0", textSize, Color.BLACK)
      setScale(scoreWidth, scoreboardHeight)
      setPosition(scoreX + 1f, localY)
    }

    //Add opponent score text to the stream
    visitorScoreTextObjectFilterRender = TextObjectFilterRender().apply {
      setText("0", textSize, Color.BLACK)
      setScale(scoreWidth, scoreboardHeight)
      setPosition(scoreX + 1f, visitorY)
    }

    //Add opponent text to the stream
    val opponentTextObjectFilterRender = TextObjectFilterRender()
    opponentTextObjectFilterRender.setText(team2.name, textSize, Color.BLACK)
    opponentTextObjectFilterRender.setScale(teamNameWidth, scoreboardHeight)
    opponentTextObjectFilterRender.setPosition(teamNameX, visitorY)



    //Add opponent logo to the stream
    val opponentLogoObjectFilterRender = ImageObjectFilterRender()
    val bitmap = BitmapFactory.decodeFile(team2.logoPath)



    opponentLogoObjectFilterRender.setImage(bitmap)
    opponentLogoObjectFilterRender.setReScale(streamHeight, streamWidth, maxWidth, maxHeight, 2F, 8F)



    val whiteSquareFilter = ImageObjectFilterRender().apply {
      // Crea una imagen en blanco
      val whiteBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(whiteBitmap)
      canvas.drawColor(Color.WHITE)
      setImage(whiteBitmap)
      setScale(25f, 20f) // Ajusta el tamaño del cuadrado según sea necesario
      setPosition(1F, 1F) // Ajusta la posición según sea necesario
    }

    //Add countdown text to the stream
    countdownTextFilterRender = TextObjectFilterRender()
    countdownTextFilterRender.setText("$matchTime:00", 50f, Color.BLACK)
    countdownTextFilterRender.setScale(5f, 7f)
    countdownTextFilterRender.setPosition(19F, 15F)

    //Add all filters to the stream
    val stream = genericStream.getGlInterface()
    stream.addFilter(whiteSquareFilter)
    stream.addFilter(localLogoObjectFilterRender)
    stream.addFilter(localTextObjectFilterRender)
    stream.addFilter(localScoreTextObjectFilterRender!!)
    stream.addFilter(visitorScoreTextObjectFilterRender!!)
    stream.addFilter(opponentTextObjectFilterRender)
    stream.addFilter(opponentLogoObjectFilterRender)
    stream.addFilter(countdownTextFilterRender)

    // Add the man and woman icon if needed
    val womanIcon = BitmapFactory.decodeResource(resources, R.drawable.woman)
    val manIcon = BitmapFactory.decodeResource(resources, R.drawable.man)
    manObjectFilterRender.setImage(manIcon)
    womanObjectFilterRender.setImage(womanIcon)
    womanObjectFilterRender.setScale(5f, 6f)
    womanObjectFilterRender.setPosition(15F, 15F)
    manObjectFilterRender.setScale(5f, 6f)
    manObjectFilterRender.setPosition(15F, 15F)
    if(mixedCheckBox){
      stream.addFilter(manObjectFilterRender)
      stream.addFilter(womanObjectFilterRender)
    }



    localPlus.setOnClickListener {
      plus = true
      localScore++
      localScoreTextObjectFilterRender!!.updateScoreText(localScore.toString())
    }

    localMinus.setOnClickListener {
      plus = false
      if (localScore > 0) {
        localScore--
        localScoreTextObjectFilterRender!!.updateScoreText(localScore.toString())
      }
    }

    visitorPlus.setOnClickListener {
      plus = true
      visitorScore++
      visitorScoreTextObjectFilterRender!!.updateScoreText(visitorScore.toString())
    }

    visitorMinus.setOnClickListener {
      plus = false
      if (visitorScore > 0) {
        visitorScore--
        visitorScoreTextObjectFilterRender!!.updateScoreText(visitorScore.toString())
      }
    }




    /*bSwitchCamera.setOnClickListener {
      when (val source = genericStream.videoSource) {
        is Camera2Source -> source.switchCamera()
      }
    }*/

    if(mixedCheckBox) {


      //Male or Female gender
      maleButton.setOnClickListener {
        man = true
        maleButton.visibility = View.GONE
        femaleButton.visibility = View.GONE
        womanObjectFilterRender.setScale(0f, 0f)
        manObjectFilterRender.setScale(5f, 6f)
      }

      femaleButton.setOnClickListener {
        man = false
        maleButton.visibility = View.GONE
        femaleButton.visibility = View.GONE
        manObjectFilterRender.setScale(0f, 0f)
        womanObjectFilterRender.setScale(5f, 6f)
      }
    }
    else{
      maleButton.visibility = View.GONE
      femaleButton.visibility = View.GONE
    }

    // Countdown button and text view
    countdownButton = view.findViewById(R.id.countdown_button)
    countdownPauseButton = view.findViewById(R.id.countdown_pause_button)
    //countdownTextView = view.findViewById(R.id.countdown_textview)

    countdownButton.setOnClickListener {
      countdownButton.visibility = View.GONE
      countdownPauseButton.visibility = View.VISIBLE
      startCountdownTimer()
    }
    countdownPauseButton.setOnClickListener {
      //countdownButton.visibility = View.VISIBLE
      //countdownPauseButton.visibility = View.GONE
      pauseCountdownTimer()
    }
    zoomSeekBar = view.findViewById(R.id.zoomSeekBar)
    zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        setCameraZoom(progress)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {
        // No se necesita implementación aquí
      }

      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        // No se necesita implementación aquí
      }
    })

    //obtain the camera zoom range
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
      try {
        for (cameraId in cameraManager.cameraIdList) {
          val characteristics = cameraManager.getCameraCharacteristics(cameraId)
          val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
          maxDigitalZoom?.let {
            maxZoom = it
            minZoom = 1.0f
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    switchBackCameraButton?.setOnClickListener {
      switchBackCamera()
    }

    // Obtener todas las cámaras traseras
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
      backCameraIds = cameraManager.cameraIdList.filter { cameraId ->
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        facing != null && facing == CameraCharacteristics.LENS_FACING_BACK
      }
    }
    return view
  }

  fun TextObjectFilterRender.updateScoreText(score: String) {
    setText(score, textSize, Color.BLACK)
    when (score.length) {
      1 -> {
        // Adjust scale and position for single-digit scores
        setScale(2f, scoreboardHeight)
        setPosition(scoreX + 1f, position.y)  // Adjust position as needed
      }
      2 -> {
        // Adjust scale and position for double-digit scores
        setScale(3.5f, scoreboardHeight)
        setPosition(scoreX, position.y)  // Adjust position as needed
      }
      else -> {
        // Default scale and position
        setScale(5f, 5f)
        //setPosition(19F, 1F)  // Adjust position as needed
      }
    }
    if (mixedCheckBox){
      updateGenderZone()
    }
  }

  private fun fetchLiveChatMessages() {
    CoroutineScope(Dispatchers.Main).launch {
      try {
        val messages = liveChatManager.getLiveChatMessages()
        displayMessages(messages)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun displayMessages(messages: List<LiveChatMessage>) {
    val chatMessages = messages.joinToString("\n") { message ->
      val author = message.authorDetails.displayName
      val text = message.snippet.displayMessage
      "$author: $text"
    }
    liveChatTextView.text = chatMessages
    liveChatTextView.post {
      val scrollAmount = liveChatTextView.layout.getLineTop(liveChatTextView.lineCount) - liveChatTextView.height
      liveChatTextView.scrollTo(0, maxOf(scrollAmount, 0))
    }
  }

  private fun startLoadingComments() {
    lifecycleScope.launch(Dispatchers.Main) {
      while (true) {
        fetchLiveChatMessages()
        delay(15000) // Esperar 30 segundos
      }
    }
  }

  private fun updateGenderZone(){
    if(plus) {
      if ((localScore + visitorScore) % 2 != 0) {
        if (!man) {
          manObjectFilterRender.setScale(5f, 6f)
          womanObjectFilterRender.setScale(0f, 0f)
          manObjectFilterRender.setPosition(15F, 15F)
        } else {
          manObjectFilterRender.setScale(0f, 0f)
          womanObjectFilterRender.setScale(5f, 6f)
          womanObjectFilterRender.setPosition(15F, 15F)
        }
        man = !man
      }
    }
    else {
      if ((localScore + visitorScore) % 2 == 0) {
        if (!man) {
          manObjectFilterRender.setScale(5f, 6f)
          womanObjectFilterRender.setScale(0f, 0f)
          manObjectFilterRender.setPosition(15F, 15F)
        } else {
          manObjectFilterRender.setScale(0f, 0f)
          womanObjectFilterRender.setScale(5f, 6f)
          womanObjectFilterRender.setPosition(15F, 15F)
        }
        man = !man
      }
    }
  }


  private fun switchBackCamera() {
    if (backCameraIds.isEmpty()) return

    currentBackCameraIndex = (currentBackCameraIndex + 1) % backCameraIds.size
    val newCameraId = backCameraIds[currentBackCameraIndex]

    // Detén la transmisión y libera la cámara actual
    if (genericStream.isOnPreview) {
      genericStream.stopPreview()
    }

    // Cambia a la nueva cámara trasera
    genericStream.videoSource.release()
    when (val source = genericStream.videoSource) {
      is Camera2Source -> {
        source.release()
        val surfaceTexture = SurfaceTexture(newCameraId.toInt()) // Use a suitable texture ID
        surfaceTexture.setDefaultBufferSize(surfaceView.width, surfaceView.height)
        source.start(surfaceTexture)
      }
    }
    // Reinicia la vista previa
    genericStream.startPreview(surfaceView)
  }

  // Method to adjust zoom
  private fun setCameraZoom(zoomLevel: Int) {
    val zoomLevel = minZoom + (zoomLevel / 100.0f) * (maxZoom - minZoom)
    when (val source = genericStream.videoSource) {
      is Camera1Source -> source.setZoom(zoomLevel.toInt())
      is Camera2Source -> source.setZoom(zoomLevel)
      //is CameraXSource -> source.setZoom(zoomLevel)
    }
  }

  private fun startCountdownTimer() {
    timeLeftInMillis = matchTime.toLong() * 60 * 1000L // 45 minutes in milliseconds

    countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
      override fun onTick(millisUntilFinished: Long) {
        if (isPaused) {
          cancel()
        }
        else{
          timeLeftInMillis = millisUntilFinished
          val minutes = millisUntilFinished / 1000 / 60
          val seconds = millisUntilFinished / 1000 % 60
          //countdownTextView.text = String.format("%02d:%02d", minutes, seconds)
          countdownTextFilterRender.updateText(String.format("%02d:%02d", minutes, seconds))
        }

      }

      override fun onFinish() {
        //countdownTextView.text = "00:00"
        countdownTextFilterRender.updateText("00:00")
      }
    }.start()
  }

  private fun resumeCountdownTimer() {
    isPaused = false
    countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
      override fun onTick(millisUntilFinished: Long) {
        if (!isPaused) {
          timeLeftInMillis = millisUntilFinished
          val minutes = millisUntilFinished / 1000 / 60
          val seconds = millisUntilFinished / 1000 % 60
          countdownTextFilterRender.updateText(String.format("%02d:%02d", minutes, seconds))
        } else {
          cancel()
        }
      }

      override fun onFinish() {
        countdownTextFilterRender.updateText("00:00")
      }
    }.start()
  }

  private fun pauseCountdownTimer() {
    if (isPaused){
      isPaused = false
      countdownPauseButton.text = "Pause"
      resumeCountdownTimer()
    }
    else {
      isPaused = true
      countdownPauseButton.text = "Resume"
    }
  }


  fun setOrientationMode(isVertical: Boolean) {
    val wasOnPreview = genericStream.isOnPreview
    genericStream.release()
    rotation = if (isVertical) 90 else 0
    prepare()
    if (wasOnPreview) genericStream.startPreview(surfaceView)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let {
      val selectedTeams = it.getParcelableArrayList<Teams>(ARG_TEAMS)
      rtmpUrl = it.getString("rtmpUrl").toString()
      matchTime = it.getString("matchTime").toString()
      mixedCheckBox = it.getBoolean("mixedCheckBox")
      resolution = "2160p"//it.getString("resolution")
      if (selectedTeams != null && selectedTeams.size == 2) {
        team1 = selectedTeams[0]
        team2 = selectedTeams[1]
      } else {
        throw IllegalArgumentException("Two teams must be provided")
      }
    } ?: throw IllegalArgumentException("Arguments are required")
    prepare()
    genericStream.getStreamClient().setReTries(10)
  }

  private fun prepare() {
    val prepared = try {
      //This resolution works
      /*private val width = 1280
      private val height = 720
      private val vBitrate = 1500 * 1024*/
      if (resolution == "1080p"){
        //This resolution also works
        width = 1920
        height = 1080
        vBitrate = 3500 * 1024
      }

      //This one is working but sometimes streams with frezzings
      else if (resolution == "1440p") {
        width = 2560
        height = 1440
        vBitrate = 6000 * 1024
      }
      // not tested 2160p (4K): 3840x2160
      else if (resolution == "2160p") {

        width = 3840
        height = 2160
        vBitrate = 10000 * 1024
      }

      //genericStream.setVideoCodec(VideoCodec.H265)

      genericStream.prepareVideo(width, height, vBitrate, rotation = rotation) &&
          genericStream.prepareAudio(sampleRate, isStereo, aBitrate)
    } catch (e: IllegalArgumentException) {
      false
    }
    if (!prepared) {
      toast("Audio or Video configuration failed")
      activity?.finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    genericStream.release()
  }

  override fun onConnectionStarted(url: String) {
  }

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    if (genericStream.getStreamClient().reTry(5000, reason, null)) {
      toast("Retry")
    } else {
      genericStream.stopStream()
      bStartStop.setImageResource(R.drawable.stream_icon)
      toast("Failed: $reason")
    }
  }

  override fun onNewBitrate(bitrate: Long) {}

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    genericStream.stopStream()
    bStartStop.setImageResource(R.drawable.stream_icon)
    toast("Auth error")
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }
}