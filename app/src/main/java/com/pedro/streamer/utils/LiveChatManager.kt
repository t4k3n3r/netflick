package com.pedro.streamer.utils

import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.LiveChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveChatManager(private val youTubeService: YouTube, private val liveChatId: String) {

    suspend fun getLiveChatMessages(): List<LiveChatMessage> {
        return withContext(Dispatchers.IO) {
            val liveChatRequest = youTubeService.liveChatMessages()
                .list(liveChatId, listOf("snippet", "authorDetails"))
                .setMaxResults(200L)
                .execute()

            liveChatRequest.items
        }
    }
}