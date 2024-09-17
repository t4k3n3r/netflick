package com.pedro.streamer.rotation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Configuration.java
@Parcelize
class Teams (
    var id: Int = 0,
    var name: String? = null,
    var logoPath: String? = null
): Parcelable
