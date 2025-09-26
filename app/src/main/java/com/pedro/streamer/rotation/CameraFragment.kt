
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
  private val localArrowObjectFilterRender = ImageObjectFilterRender()
  private val visitorArrowObjectFilterRender = ImageObjectFilterRender()
  private var man: Boolean = false
  private var plus: Boolean = true
  private lateinit var liveChatTextView: TextView
  private lateinit var liveChatManager: LiveChatManager
  private lateinit var localAttackButton: Button
  private lateinit var visitorAttackButton: Button
  private var localAttacking = true
  private val scoreHistory = mutableListOf<String>()
  private var firstAttacker = "local"
  //private var switchBackCameraButton = view.findViewById<ImageView>(R.id.switch_camera)
  /*private val notificationTextFilterRender = TextObjectFilterRender()
  private val notificationBackgroundFilterRender = ImageObjectFilterRender()
  private var notificationTimer: CountDownTimer? = null
  private var isNotificationVisible = false*/

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_camera, container, false)
    bStartStop = view.findViewById(R.id.b_start_stop)
    val switchBackCameraButton = view.findViewById<ImageView>(R.id.switch_camera)
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
    localAttackButton = view.findViewById<Button>(R.id.local_attack_button)
    visitorAttackButton = view.findViewById<Button>(R.id.visitor_attack_button)

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

    val localLogoObjectFilterRender = ImageObjectFilterRender()
    val localBitmap = BitmapFactory.decodeFile(team1.logoPath)

    // Stream resolution (por example 480x640 portrait mode)
    val streamWidth = 1920f
    val streamHeight = 1080f

    // Permitted max
    val maxHeight = 7f
    val maxWidth = 6f

    localLogoObjectFilterRender.setImage(localBitmap)
    localLogoObjectFilterRender.setReScale(streamHeight, streamWidth, maxWidth, maxHeight, 2F, 1F)

    val teamNameWidth = 12f
    val scoreWidth = 2f
    val teamNameX = 8f
    val localY = 1f
    val visitorY = 8f

    val localTextObjectFilterRender = TextObjectFilterRender()
    localTextObjectFilterRender.setText(team1.name, textSize, Color.BLACK)
    localTextObjectFilterRender.setScale(teamNameWidth, scoreboardHeight)
    localTextObjectFilterRender.setPosition(teamNameX, localY)

    localScoreTextObjectFilterRender = TextObjectFilterRender().apply {
      setText("0", textSize, Color.BLACK)
      setScale(scoreWidth, scoreboardHeight)
      setPosition(scoreX + 1f, localY)
    }

    visitorScoreTextObjectFilterRender = TextObjectFilterRender().apply {
      setText("0", textSize, Color.BLACK)
      setScale(scoreWidth, scoreboardHeight)
      setPosition(scoreX + 1f, visitorY)
    }

    val opponentTextObjectFilterRender = TextObjectFilterRender()
    opponentTextObjectFilterRender.setText(team2.name, textSize, Color.BLACK)
    opponentTextObjectFilterRender.setScale(teamNameWidth, scoreboardHeight)
    opponentTextObjectFilterRender.setPosition(teamNameX, visitorY)

    val options = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val arrowBitmap = getBitmapFromVectorDrawable(requireContext(), R.drawable.arrow_right)

    val arrowBitmapLocal = arrowBitmap.copy(Bitmap.Config.ARGB_8888, false)
    val arrowBitmapVisitor = arrowBitmap.copy(Bitmap.Config.ARGB_8888, false)

    localArrowObjectFilterRender.setImage(arrowBitmapLocal)
    visitorArrowObjectFilterRender.setImage(arrowBitmapVisitor)

    localArrowObjectFilterRender.setScale(-3f, 4f)
    localArrowObjectFilterRender.setPosition(27F, 2F)

    visitorArrowObjectFilterRender.setScale(-3f, 4f)
    visitorArrowObjectFilterRender.setPosition(27F, 9F)

    val opponentLogoObjectFilterRender = ImageObjectFilterRender()
    val bitmap = BitmapFactory.decodeFile(team2.logoPath)



    opponentLogoObjectFilterRender.setImage(bitmap)
    opponentLogoObjectFilterRender.setReScale(streamHeight, streamWidth, maxWidth, maxHeight, 2F, 8F)



    val whiteSquareFilter = ImageObjectFilterRender().apply {
      val whiteBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(whiteBitmap)
      canvas.drawColor(Color.WHITE)
      setImage(whiteBitmap)
      setScale(25f, 20f)
      setPosition(1F, 1F)
    }

    countdownTextFilterRender = TextObjectFilterRender()
    countdownTextFilterRender.setText("$matchTime:00", 50f, Color.BLACK)
    countdownTextFilterRender.setScale(5f, 7f)
    countdownTextFilterRender.setPosition(19F, 15F)

    val stream = genericStream.getGlInterface()
    stream.addFilter(whiteSquareFilter)

    stream.addFilter(localLogoObjectFilterRender)
    stream.addFilter(localTextObjectFilterRender)
    stream.addFilter(localScoreTextObjectFilterRender!!)
    stream.addFilter(visitorScoreTextObjectFilterRender!!)
    stream.addFilter(opponentTextObjectFilterRender)
    stream.addFilter(opponentLogoObjectFilterRender)
    stream.addFilter(countdownTextFilterRender)
    stream.addFilter(localArrowObjectFilterRender)
    stream.addFilter(visitorArrowObjectFilterRender)

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
      /*val isBreak = isBreakPoint(localScore, visitorScore, "local")
      val message = getScoreMessage(localScore, isBreak)
      showSportsNotification(message, team1.name ?: "Local")*/
      localScore++
      scoreHistory.add("local")
      localAttacking = true
      localScoreTextObjectFilterRender!!.updateScoreText(localScore.toString())
      updateAttackerArrow()
    }

    localMinus.setOnClickListener {
      plus = false
      val index = scoreHistory.lastIndexOf("local")
      if (index != -1) {
        scoreHistory.removeAt(index)
        localScore--
        localScoreTextObjectFilterRender?.updateScoreText(localScore.toString())
        updateAttackerArrow()
      }
    }

    visitorPlus.setOnClickListener {
      plus = true
      /*val isBreak = isBreakPoint(visitorScore, localScore, "visitor")
      val message = getScoreMessage(visitorScore, isBreak)
      showSportsNotification(message, team2.name ?: "visitor")*/
      visitorScore++
      scoreHistory.add("visitor")
      localAttacking = false
      visitorScoreTextObjectFilterRender!!.updateScoreText(visitorScore.toString())
      updateAttackerArrow()
    }

    visitorMinus.setOnClickListener {
      plus = false
      val index = scoreHistory.lastIndexOf("visitor")
      if (index != -1) {
        scoreHistory.removeAt(index)
        visitorScore--
        visitorScoreTextObjectFilterRender?.updateScoreText(visitorScore.toString())
        updateAttackerArrow()
      }
    }

    localAttackButton.setOnClickListener {
      firstAttacker = "local"
      localAttackButton.visibility = View.GONE
      visitorAttackButton.visibility = View.GONE
      updateAttackerArrow()
    }

    visitorAttackButton.setOnClickListener {
      firstAttacker = "visitor"
      localAttackButton.visibility = View.GONE
      visitorAttackButton.visibility = View.GONE
      updateAttackerArrow()
    }



    /*bSwitchCamera.setOnClickListener {
      when (val source = genericStream.videoSource) {
        is Camera2Source -> source.switchCamera()
      }
    }*/

    if(mixedCheckBox) {


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

    countdownButton = view.findViewById(R.id.countdown_button)
    countdownPauseButton = view.findViewById(R.id.countdown_pause_button)

    countdownButton.setOnClickListener {
      countdownButton.visibility = View.GONE
      countdownPauseButton.visibility = View.VISIBLE
      startCountdownTimer()
    }
    countdownPauseButton.setOnClickListener {
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
        setScale(2f, scoreboardHeight)
        setPosition(scoreX + 1f, position.y)
      }
      2 -> {
        setScale(3.5f, scoreboardHeight)
        setPosition(scoreX, position.y)
      }
      else -> {
        setScale(5f, 5f)
      }
    }

    if (mixedCheckBox){
      updateGenderZone()
    }
  }

  private fun updateAttackerArrow() {
    var lastScorer = if (firstAttacker == "local") "visitor" else "local"

    if (scoreHistory.isNotEmpty()){
      lastScorer = scoreHistory.last()
    }
    if (lastScorer == "visitor") {
      localArrowObjectFilterRender.setScale(-3f, 4f)
      localArrowObjectFilterRender.setPosition(27F, 2F)
      visitorArrowObjectFilterRender.setScale(0f, 0f)
    } else {
      localArrowObjectFilterRender.setScale(0f, 0f)
      visitorArrowObjectFilterRender.setScale(-3f, 4f)
      visitorArrowObjectFilterRender.setPosition(27F, 9F)
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
        delay(15000) // W8 30 seconds
      }
    }
  }

  /*private fun createNotificationBackground(width: Int = 400, height: Int = 120, ccolor: Int = Color.parseColor("#FF1744")): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Limpiar el canvas
    canvas.drawColor(Color.TRANSPARENT)

    val paint = Paint().apply {
      isAntiAlias = true
    }

    // Crear fondo con gradiente
    val gradient = LinearGradient(
      0f, 0f, width.toFloat(), height.toFloat(),
      ccolor, Color.parseColor("#D32F2F"),
      Shader.TileMode.CLAMP
    )
    paint.shader = gradient

    // Dibujar rectángulo redondeado
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, 20f, 20f, paint)

    // Añadir borde blanco
    paint.apply {
      shader = null
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = 4f
    }
    canvas.drawRoundRect(rect, 20f, 20f, paint)

    return bitmap
  }

  private fun showSportsNotification(message: String, teamName: String, duration: Long = 3000) {
    try {
      // Si ya hay una notificación visible, ocultarla primero
      if (isNotificationVisible) {
        hideNotification()
      }

      // Cancelar timer anterior si existe
      notificationTimer?.cancel()

      // Crear el texto completo
      val fullMessage = "$message\n$teamName"

      // Configurar el fondo de la notificación
      val notificationBg = createNotificationBackground(500, 150, Color.parseColor("#FF1744"))
      notificationBackgroundFilterRender.setImage(notificationBg)
      notificationBackgroundFilterRender.setScale(15f, 10f) // Ajustar tamaño
      notificationBackgroundFilterRender.setPosition(10f, 10f) // Centrado en la parte superior

      // Configurar el texto de la notificación
      notificationTextFilterRender.setText(fullMessage, 60f, Color.WHITE) // Texto blanco
      notificationTextFilterRender.setScale(12f, 8f)
      notificationTextFilterRender.setPosition(12f, 11f) // Ligeramente desplazado del fondo

      // Añadir al stream
      val stream = genericStream.getGlInterface()
      stream.addFilter(notificationBackgroundFilterRender)
      stream.addFilter(notificationTextFilterRender)

      isNotificationVisible = true

      // Programar ocultación automática
      notificationTimer = object : CountDownTimer(duration, 100) {
        override fun onTick(millisUntilFinished: Long) {
          // Opcional: añadir efecto de parpadeo en los últimos 500ms
          if (millisUntilFinished < 500) {
            val alpha = if ((millisUntilFinished / 100) % 2 == 0L) 0.5f else 1.0f
            // Nota: setAlpha no está disponible en ImageObjectFilterRender
            // pero podrías implementar un efecto de escala
          }
        }

        override fun onFinish() {
          hideNotification()
        }
      }.start()

    } catch (e: Exception) {
      println("Error mostrando notificación: ${e.message}")
      e.printStackTrace()
    }
  }

  private fun hideNotification() {
    try {
      if (isNotificationVisible) {
        val stream = genericStream.getGlInterface()
        stream.removeFilter(notificationBackgroundFilterRender)
        stream.removeFilter(notificationTextFilterRender)
        isNotificationVisible = false
      }
      notificationTimer?.cancel()
    } catch (e: Exception) {
      println("Error ocultando notificación: ${e.message}")
    }
  }

  private fun getScoreMessage(previousScore: Int,  isBreakPoint: Boolean = false): String {
    return when {
      isBreakPoint -> "¡BREAK!"
      /*newScore - previousScore == 1 -> "¡PUNTO!"
      newScore - previousScore > 1 -> "¡PUNTOS!"*/
      else -> "¡PUNTO!"
    }
  }

  private fun isBreakPoint(localScore: Int, visitorScore: Int, scoringTeam: String): Boolean {
    var attackingTeam = firstAttacker
    if (scoreHistory.isNotEmpty()){
      attackingTeam = scoreHistory.last()
    }
    return when {
      scoringTeam != attackingTeam -> true
      //visitorScore >= 5 && localScore <= visitorScore - 2 && scoringTeam == "visitor" -> true
      // Otros casos específicos de tu deporte
      else -> false
    }
  }*/

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

  private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId)!!
    val bitmap = Bitmap.createBitmap(
      drawable.intrinsicWidth,
      drawable.intrinsicHeight,
      Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
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
        width = 1920
        height = 1080
        vBitrate = 3500 * 1024
      }
      else if (resolution == "1440p") {
        width = 2560
        height = 1440
        vBitrate = 6000 * 1024
      }
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
    /*notificationTimer?.cancel()
    hideNotification()*/
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