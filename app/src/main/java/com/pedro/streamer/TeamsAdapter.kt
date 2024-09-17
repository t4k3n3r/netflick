package com.pedro.streamer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pedro.streamer.rotation.Teams
import com.squareup.picasso.Picasso
import java.io.File

class TeamsAdapter(private val teams: List<Teams>) : RecyclerView.Adapter<TeamsAdapter.ViewHolder>() {

    private val selectedTeams = mutableListOf<Teams>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val teamName: TextView = view.findViewById(R.id.teamName)
        val teamLogo: ImageView = view.findViewById(R.id.teamLogo)
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
}
