    package com.pedro.streamer.rotation

    import android.content.ContentValues
    import android.content.Context
    import android.database.sqlite.SQLiteDatabase
    import android.database.sqlite.SQLiteOpenHelper


    // DatabaseHelper.java
    class DatabaseHelper(context: Context?) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            val createTable = "CREATE TABLE teams (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "logoPath TEXT)"
            db.execSQL(createTable)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS teams")
            onCreate(db)
        }

        fun addTeam(team: Teams): Long {
            val db = this.writableDatabase
            val values = ContentValues()
            values.put("name", team.name)
            values.put("logoPath", team.logoPath)
            return db.insert("teams", null, values)
        }

        val allTeams: List<Teams>
            get() {
                val teams: MutableList<Teams> = ArrayList<Teams>()
                val db = this.readableDatabase
                val cursor = db.query("teams", null, null, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        val team: Teams = Teams()
                        team.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        team.name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        team.logoPath = cursor.getString(cursor.getColumnIndexOrThrow("logoPath"))
                        teams.add(team)
                    } while (cursor.moveToNext())
                    cursor.close()
                }
                return teams
            }

        companion object {
            private const val DATABASE_NAME = "teams.db"
            private const val DATABASE_VERSION = 1
        }
    }
