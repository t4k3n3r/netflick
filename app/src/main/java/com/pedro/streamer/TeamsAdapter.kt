package com.pedro.streamer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pedro.streamer.rotation.Teams
import com.squareup.picasso.Picasso
import java.io.File

class TeamsAdapter(private val teams: List<Teams>, private val onDelete: (Teams) -> Unit) : RecyclerView.Adapter<TeamsAdapter.ViewHolder>() {

    private val selectedTeams = mutableListOf<Teams>()
    private var startingTeamIndex = 0 // 0 para el primer equipo seleccionado, 1 para el segundo

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val teamName: TextView = view.findViewById(R.id.teamName)
        val teamLogo: ImageView = view.findViewById(R.id.teamLogo)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteTeam)

        init {
            view.setOnClickListener {
                val team = teams[adapterPosition]
                if (selectedTeams.contains(team)) {
                    selectedTeams.remove(team)
                    view.setBackgroundColor(android.graphics.Color.WHITE)
                } else if (selectedTeams.size < 2) {
                    selectedTeams.add(team)
                    view.setBackgroundColor(android.graphics.Color.LTGRAY)
                }
            }
            deleteButton.setOnClickListener {
                val team = teams[adapterPosition]
                onDelete(team) // callback a MainActivity
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.team_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val team = teams[position]
        holder.teamName.text = team.name
        val path = team.logoPath
        val file = File(path)
        Picasso.get().load(file).into(holder.teamLogo)

        // Establecer el color de fondo del itemView según si está seleccionado o no
        holder.itemView.setBackgroundColor(if (selectedTeams.contains(team)) android.graphics.Color.LTGRAY else android.graphics.Color.WHITE)
    }

    override fun getItemCount(): Int {
        return teams.size
    }

    fun getSelectedTeams(): List<Teams> = selectedTeams

    fun getStartingTeamIndex(): Int = startingTeamIndex

    fun setStartingTeamIndex(index: Int) {
        startingTeamIndex = index
    }
}