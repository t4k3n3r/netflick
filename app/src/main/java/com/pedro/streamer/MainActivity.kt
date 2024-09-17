package com.pedro.streamer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pedro.streamer.rotation.DatabaseHelper
import com.pedro.streamer.rotation.RotationActivity
import com.pedro.streamer.rotation.Teams

class MainActivity : AppCompatActivity() {
  private lateinit var editTeamName: EditText
  private lateinit var buttonSelectLogo: Button
  private lateinit var buttonSaveTeam: Button
  private lateinit var buttonStartRotationActivity: Button
  private lateinit var recyclerViewTeams: RecyclerView
  private lateinit var databaseHelper: DatabaseHelper
  private var selectedLogoPath: String = ""

  private val permissions = mutableListOf(
    Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
  ).apply {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
      this.add(Manifest.permission.POST_NOTIFICATIONS)
    }
  }.toTypedArray()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    editTeamName = findViewById(R.id.editTeamName)
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
      startRotationActivity()
    }
    transitionAnim(true)
    loadTeams()
    requestPermissions()
  }

  private fun selectLogo() {
    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    startActivityForResult(intent, 1)
  }
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
      val selectedImageUri = data.data
      selectedLogoPath = getRealPathFromURI(selectedImageUri) ?: ""
    }
  }

  // Helper function to get real file path from Content URI
  private fun getRealPathFromURI(contentUri: Uri?): String? {
    if (contentUri == null) return null

    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val cursor = contentResolver.query(contentUri, projection, null, null, null)

    cursor?.use {
      if (it.moveToFirst()) {
        val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        return it.getString(columnIndex)
      }
    }
    return null
  }

  private fun saveTeam() {
    val name = editTeamName.text.toString()
    if (name.isNotEmpty() && selectedLogoPath.isNotEmpty()) {
      val team = Teams()
      team.name = name
      team.logoPath = selectedLogoPath
      databaseHelper.addTeam(team)
      loadTeams()
    } else {
      Toast.makeText(this, "Nombre y logo son requeridos", Toast.LENGTH_SHORT).show()
    }
  }

  private fun loadTeams() {
    val teams = databaseHelper.allTeams
    val adapter = TeamsAdapter(teams)
    recyclerViewTeams.layoutManager = LinearLayoutManager(this)
    recyclerViewTeams.adapter = adapter
  }

  private fun startRotationActivity() {
    val adapter = recyclerViewTeams.adapter as TeamsAdapter
    val selectedTeams = adapter.getSelectedTeams()
    if (selectedTeams.size == 2) {
      val intent = Intent(this, RotationActivity::class.java)
      intent.putParcelableArrayListExtra("selectedTeams", ArrayList(selectedTeams))
      startActivity(intent)
    } else {
      Toast.makeText(this, "Por favor selecciona 2 equipos", Toast.LENGTH_SHORT).show()
    }
  }

  @Suppress("DEPRECATION")
  private fun transitionAnim(isOpen: Boolean) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val type = if (isOpen) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE
      overrideActivityTransition(type, R.anim.slide_in, R.anim.slide_out)
    } else {
      overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }
  }

  private fun requestPermissions() {
    if (!hasPermissions(this)) {
      ActivityCompat.requestPermissions(this, permissions, 1)
    }
  }

  private fun hasPermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
      for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission)
          != PackageManager.PERMISSION_GRANTED
        ) {
          return false
        }
      }
    }
    return true
  }
}