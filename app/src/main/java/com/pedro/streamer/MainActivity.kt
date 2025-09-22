package com.pedro.streamer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.CdnSettings
import com.google.api.services.youtube.model.LiveBroadcast
import com.google.api.services.youtube.model.LiveBroadcastContentDetails
import com.google.api.services.youtube.model.LiveBroadcastSnippet
import com.google.api.services.youtube.model.LiveBroadcastStatus
import com.google.api.services.youtube.model.LiveStream
import com.google.api.services.youtube.model.LiveStreamSnippet
import com.google.api.services.youtube.model.LiveStreamStatus
import com.pedro.streamer.rotation.DatabaseHelper
import com.pedro.streamer.rotation.RotationActivity
import com.pedro.streamer.rotation.Teams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity() {
  private lateinit var apiKey: String
  //private val channelId = "UCizpnK6dl1Fn2asSXBZyd5Q"
  private lateinit var googleSignInClient: GoogleSignInClient
  private lateinit var credential: GoogleAccountCredential
  private var liveChatId: String = ""
  private val REQUEST_ACCOUNT_PICKER = 1000
  private val REQUEST_AUTHORIZATION = 1001
  private val SCOPES = arrayOf("https://www.googleapis.com/auth/youtube.upload")
  private var streamTitle: String? = null
  private var description: String? = null
  private var visibility: String? = null
  private val TAG = "CreateLiveBroadcastTask"
  private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
  private val APPLICATION_NAME = "NetFlick Streamer"
  //private val CLIENT_SECRETS = "com/pedro/streamer/google_credentials.json"
  private var rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/hdv8-77ta-vjvu-ey2r-40bx"

  private lateinit var editTeamName: EditText
  private lateinit var editTournamentName: EditText
  private lateinit var editMatchTime: EditText
  private lateinit var visibilityRadioGroup : RadioGroup
  private lateinit var mixedCheckBox: CheckBox
  private lateinit var buttonSelectLogo: Button
  private lateinit var buttonSaveTeam: Button
  private lateinit var buttonStartRotationActivity: Button
  private lateinit var recyclerViewTeams: RecyclerView
  private lateinit var databaseHelper: DatabaseHelper
  private var selectedLogoPath: String = ""
  private var youtube: YouTube? = null
  object YoutubeServiceManager {
    var youtubeService: YouTube? = null
  }


  private val permissions = mutableListOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE
  ).apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      this.add(Manifest.permission.POST_NOTIFICATIONS)
      this.add(Manifest.permission.READ_MEDIA_IMAGES)
      this.add(Manifest.permission.READ_MEDIA_VIDEO)
      this.add(Manifest.permission.READ_MEDIA_AUDIO)
    }
  }.toTypedArray()


  private suspend fun createLiveStream(selectedTeams: List<Teams>, selectedResolution: String): String {
    suspendCoroutine { continuation ->
      try {

        val youtubeService = getService()
        YoutubeServiceManager.youtubeService = youtubeService
        val Team1 = selectedTeams[0].name
        val Team2 = selectedTeams[1].name
        val pattern = "dd/MM/yyyy"
        val simpleDateFormat = SimpleDateFormat(pattern)
        val date = simpleDateFormat.format(System.currentTimeMillis())
        val tournamentName = editTournamentName.text.toString()

        streamTitle = "$Team1 vs $Team2 $tournamentName - $date"
        description = "$Team1 vs $Team2 $tournamentName - $date"

        val liveBroadcastSnippet = LiveBroadcastSnippet()
        liveBroadcastSnippet.title = streamTitle
        liveBroadcastSnippet.description = description
        liveBroadcastSnippet.scheduledStartTime = DateTime(System.currentTimeMillis())
        val liveBroadcastStatus = LiveBroadcastStatus()
        liveBroadcastStatus.privacyStatus = visibility
        val liveBroadcastContentDetails = LiveBroadcastContentDetails()
        liveBroadcastContentDetails.enableAutoStart = true
        liveBroadcastContentDetails.enableAutoStop = true

        val liveBroadcast = LiveBroadcast()
        liveBroadcast.snippet = liveBroadcastSnippet
        liveBroadcast.status = liveBroadcastStatus
        liveBroadcast.contentDetails = liveBroadcastContentDetails

        val returnedLiveBroadcast = youtubeService.liveBroadcasts()
          .insert(listOf("snippet", "status", "contentDetails"), liveBroadcast)
          .execute()

        Log.d(TAG, "Live broadcast created with ID: ${returnedLiveBroadcast.id}")
        liveChatId = returnedLiveBroadcast.snippet.liveChatId


        val liveStreamSnippet = LiveStreamSnippet()
        liveStreamSnippet.title = "$streamTitle - LiveStream"
        liveStreamSnippet.description = description
        val liveStreamStatus = LiveStreamStatus()
        liveStreamStatus.streamStatus = "active"
        val cdnSettings = CdnSettings()
        cdnSettings.ingestionType = "rtmp"
        cdnSettings.resolution = selectedResolution
        cdnSettings.frameRate = "30fps"
        val liveStream = LiveStream()
        liveStream.snippet = liveStreamSnippet
        liveStream.cdn = cdnSettings
        liveStream.status = liveStreamStatus

        val returnedLiveStream = youtubeService.liveStreams()
          .insert(listOf("snippet", "cdn", "status"), liveStream)
          .execute()

        val liveBroadcastBind = youtubeService.liveBroadcasts()
          .bind(returnedLiveBroadcast.id, listOf("snippet"))

        val returnedLiveBroadcastBind =  liveBroadcastBind
          .setStreamId(returnedLiveStream.id)
          .execute()

        Log.d(TAG, "Live stream created with ID: ${returnedLiveStream.id}")
        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/" + returnedLiveStream.cdn.ingestionInfo.streamName
        continuation.resume(rtmpUrl)
      } catch (e: GooglePlayServicesAvailabilityIOException) {
        //errorMessage = "Google Play Services not available: " + e.message
        continuation.resumeWithException(e)
      } catch (e: UserRecoverableAuthIOException) {
        /*withContext(Dispatchers.Main) {
          startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
        }*/
        //errorMessage = "Authorization required."
        continuation.resumeWithException(e)
      } catch (e: IOException) {
        //errorMessage = "Error: " + e.message
        Log.e(TAG, e.message.toString())
        continuation.resumeWithException(e)
      }
    }
    return rtmpUrl
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    editTeamName = findViewById(R.id.editTeamName)
    editTournamentName = findViewById(R.id.editTrournamentName)
    editTournamentName.setText(R.string.tournament_name)
    editMatchTime = findViewById(R.id.editMatchTime)
    visibilityRadioGroup = findViewById<RadioGroup>(R.id.visibilityRadioGroupOptions)
    mixedCheckBox = findViewById(R.id.mixedCheckBox)
    editMatchTime.setText(R.string._45_min)
    buttonSelectLogo = findViewById(R.id.buttonSelectLogo)
    buttonSaveTeam = findViewById(R.id.buttonSaveTeam)
    buttonStartRotationActivity = findViewById(R.id.buttonStartRotationActivity)
    recyclerViewTeams = findViewById(R.id.recyclerViewTeams)
    databaseHelper = DatabaseHelper(this)
    buttonSelectLogo.setOnClickListener {
      selectLogo()
    }
    buttonSaveTeam.setOnClickListener {
      saveTeam()
    }
    buttonStartRotationActivity.setOnClickListener {
      CoroutineScope(Dispatchers.Main).launch {
        startRotationActivity()
      }
    }
    transitionAnim(true)
    loadTeams()
    loadPreferences()

    apiKey = getString(R.string.youtube_api_key)
    credential = GoogleAccountCredential.usingOAuth2(this, listOf(SCOPES[0]))

    requestPermissions()
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestScopes(Scope(SCOPES[0]))
      .requestId()
      .requestEmail()
      .build()

    googleSignInClient = GoogleSignIn.getClient(this, gso)
    val account = GoogleSignIn.getLastSignedInAccount(this)
    if (account != null) {
      setupYouTubeService(account)
    } else {
      signIn()
    }
  }

  private fun selectLogo() {
    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    startActivityForResult(intent, 1)
  }

  private fun getRealPathFromURI(contentUri: Uri?): String? {
    if (contentUri == null) {
      return null
    }
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val cursor = contentResolver.query(contentUri, projection, null, null, null)
    cursor?.let {
      if (cursor.moveToFirst()) {
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        val path = cursor.getString(columnIndex)
        cursor.close()
        return path
      }
      cursor.close()
    }
    return null
  }

  private fun saveTeam() {
    val teamName = editTeamName.text.toString()
    if (teamName.isNotEmpty() && selectedLogoPath.isNotEmpty()) {
      val team = Teams(0, teamName, selectedLogoPath)
      val id = databaseHelper.addTeam(team)
      if (id > 0) {
        Toast.makeText(this, "Team saved successfully", Toast.LENGTH_SHORT).show()
        loadTeams()
      } else {
        Toast.makeText(this, "Failed to save team", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(this, "Please enter team name and select a logo", Toast.LENGTH_SHORT).show()
    }
  }

  private fun loadTeams() {
    val teamsList = databaseHelper.allTeams
    val adapter = TeamsAdapter(teamsList) { team ->
      deleteTeam(team)
    }
    recyclerViewTeams.layoutManager = LinearLayoutManager(this)
    recyclerViewTeams.adapter = adapter
  }

  private fun deleteTeam(team: Teams) {
    val rowsDeleted = databaseHelper.deleteTeam(team.id)
    if (rowsDeleted > 0) {
      Toast.makeText(this, "Equipo eliminado", Toast.LENGTH_SHORT).show()
      loadTeams()
    } else {
      Toast.makeText(this, "Error al eliminar equipo", Toast.LENGTH_SHORT).show()
    }
  }

  private fun savePreferences() {
    val sharedPreferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString("teamName", editTeamName.text.toString())
    editor.putString("tournamentName", editTournamentName.text.toString())
    editor.putString("matchTime", editMatchTime.text.toString())
    editor.putBoolean("mixedGame", mixedCheckBox.isChecked)

    if (visibilityRadioGroup.checkedRadioButtonId == R.id.radioPublic) {
      editor.putString("radioOption", "public")
    } else if (visibilityRadioGroup.checkedRadioButtonId == R.id.radioUnlisted) {
      editor.putString("radioOption", "unlisted")
    } else if (visibilityRadioGroup.checkedRadioButtonId == R.id.radioNoBroadcast) {
      editor.putString("radioOption", "no_broadcast")
    }

    editor.apply()
  }

  private fun loadPreferences() {
    val sharedPreferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
    editTeamName.setText(sharedPreferences.getString("teamName", ""))
    editTournamentName.setText(sharedPreferences.getString("tournamentName", "@string/tournament_name"))
    editMatchTime.setText(sharedPreferences.getString("matchTime", "@string._45_min"))
    mixedCheckBox.isChecked = sharedPreferences.getBoolean("mixedGame", false)

    val radioOption = sharedPreferences.getString("radioOption", "public")
    if (radioOption == "public") {
      visibilityRadioGroup.check(R.id.radioPublic)
    } else if (radioOption == "unlisted") {
      visibilityRadioGroup.check(R.id.radioUnlisted)
    } else if (radioOption == "no_broadcast") {
      visibilityRadioGroup.check(R.id.radioNoBroadcast)
    }
  }

  private fun startRotationActivity() {
    CoroutineScope(Dispatchers.Main).launch {
      val adapter = recyclerViewTeams.adapter as TeamsAdapter
      val selectedTeams = adapter.getSelectedTeams()
      if (selectedTeams.size == 2) {
        if (visibilityRadioGroup.checkedRadioButtonId == R.id.radioPublic) {
          visibility = "Public"
        }
        else if (visibilityRadioGroup.checkedRadioButtonId == R.id.radioUnlisted) {
          visibility = "Unlisted"
        }
        val resolutionSpinner = findViewById<Spinner>(R.id.resolutionSpinner)
        val selectedResolution = resolutionSpinner.selectedItem.toString()
        if (visibilityRadioGroup.checkedRadioButtonId != R.id.radioNoBroadcast) {
          rtmpUrl = withContext(Dispatchers.IO) { createLiveStream(selectedTeams, selectedResolution) }
        }

        val intent = Intent(this@MainActivity, RotationActivity::class.java)
        intent.putExtra("rtmpUrl", rtmpUrl)
        intent.putExtra("matchTime", editMatchTime.text.toString())
        intent.putExtra("mixedCheckBox", mixedCheckBox.isChecked)
        intent.putExtra("resolution", selectedResolution)
        intent.putExtra("visibility", visibility)
        intent.putExtra("liveChatId", liveChatId)
        intent.putParcelableArrayListExtra("selectedTeams", ArrayList(selectedTeams))
        savePreferences()
        startActivity(intent)
      } else {
        Toast.makeText(this@MainActivity, "Por favor selecciona 2 equipos", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun transitionAnim(enable: Boolean) {
    val fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in)
    val fadeOut = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out)
    if (enable) {
      buttonSelectLogo.startAnimation(fadeIn)
      buttonSaveTeam.startAnimation(fadeIn)
      buttonStartRotationActivity.startAnimation(fadeIn)
    } else {
      buttonSelectLogo.startAnimation(fadeOut)
      buttonSaveTeam.startAnimation(fadeOut)
      buttonStartRotationActivity.startAnimation(fadeOut)
    }
  }

  private fun requestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!hasPermissions(this, *permissions)) {
        ActivityCompat.requestPermissions(this, permissions, 1)
      }
    }
  }

  private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
    if (context != null && permissions.isNotEmpty()) {
      for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(
            context,
            permission
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          return false
        }
      }
    }
    return true
  }

  private fun signIn() {
    val signInIntent = googleSignInClient.signInIntent
    startActivityForResult(signInIntent, REQUEST_ACCOUNT_PICKER)
  }

  private fun setupYouTubeService(account: GoogleSignInAccount) {
    credential.selectedAccount = account.account
    youtube = YouTube.Builder(
      GoogleNetHttpTransport.newTrustedTransport(),
      JSON_FACTORY,
      credential
    )
      .setApplicationName(APPLICATION_NAME)
      .build()
  }

  private fun getService(): YouTube {
    if (youtube == null) {
      val transport = GoogleNetHttpTransport.newTrustedTransport()
      val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
      youtube = YouTube.Builder(transport, jsonFactory, credential)
        .setApplicationName(APPLICATION_NAME)
        .build()
    }
    return youtube as YouTube
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
      REQUEST_ACCOUNT_PICKER -> if (resultCode == Activity.RESULT_OK) {
        data?.let {
          val task = GoogleSignIn.getSignedInAccountFromIntent(data)
          handleSignInResult(data)
        }
      }
      1 -> if (resultCode == Activity.RESULT_OK && data != null) {
        val selectedImage: Uri? = data.data
        selectedLogoPath = getRealPathFromURI(selectedImage) ?: ""
        // Update UI with the selected logo if needed
      }
      REQUEST_AUTHORIZATION -> if (resultCode == Activity.RESULT_OK) {
      }
    }
  }


  private fun handleSignInResult(result: Intent) {
    GoogleSignIn.getSignedInAccountFromIntent(result)
      .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
        Log.d(TAG, "Signed in as " + googleAccount.email)
        // Use the authenticated account to sign in to the Drive service.
        val credential =
          GoogleAccountCredential.usingOAuth2(
            this, setOf<String>(YouTubeScopes.YOUTUBE_UPLOAD)
          )
        credential.setSelectedAccount(googleAccount.account)
        val googleYoutubeService: YouTube? =
          YouTube.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
          )
            .setApplicationName("youtube API Control")
            .build()

        // The DriveServiceHelper encapsulates all REST API and SAF functionality.
        // Its instantiation is required before handling any onClick actions.
        val broadcastSnippet = LiveBroadcastSnippet()
        broadcastSnippet.title = streamTitle
        broadcastSnippet.description = description
        val startTime = DateTime(System.currentTimeMillis())
        broadcastSnippet.scheduledStartTime = startTime

        val broadcastStatus = LiveBroadcastStatus()
        broadcastStatus.privacyStatus = visibility
        val liveBroadcast = LiveBroadcast()
        liveBroadcast.snippet = broadcastSnippet
        liveBroadcast.status = broadcastStatus
        val returnedBroadcast = googleYoutubeService?.liveBroadcasts()
          ?.insert(listOf("snippet", "status"), liveBroadcast)
            ?.execute()
        //mDriveServiceHelper = DriveServiceHelper(googleDriveService)
      }
      .addOnFailureListener { exception: Exception? ->
        Log.e(
          TAG,
          "Unable to sign in.",
          exception
        )
      }
  }

}
