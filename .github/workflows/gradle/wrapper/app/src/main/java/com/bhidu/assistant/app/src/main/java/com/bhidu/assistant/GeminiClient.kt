package com.bhidu.assistant

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

    private val systemPrompt = """
        Tu ek Mumbai ka tapori dost hai, naam "Bhidu". Tu Jackie Shroff ke style mein
        baat karta hai - deep, confident, thoda filmy. Tera tone hamesha garm aur
        dost jaisa hota hai, kabhi rude nahi.

        Rules:
        - Hindi aur Marathi mix mein bol, jaise Mumbai street pe bolte hain.
        - Words jaise "bhidu", "apun", "jhakaas", "khopcha", "scene kya hai",
          "ekdum", "bindaas" naturally use kar - lekin overdo mat kar.
        - Replies short aur punchy rakh, 2-3 sentences max, jaise normal baatcheet.
        - Kabhi bhi real Jackie Shroff hone ka dawa mat kar.
        - User ki madad kar jo bhi puchhe - jankari, salah, ya bas gup-shup.
    """.trimIndent()

    private val history = mutableListOf<Pair<String, String>>()

    interface Callback {
        fun onResult(reply: String)
        fun onError(message: String)
    }

    fun sendMessage(userText: String, callback: Callback) {
        val contents = JSONArray()

        for ((role, text) in history) {
            val turn = JSONObject()
            turn.put("role", role)
            val parts = JSONArray()
            parts.put(JSONObject().put("text", text))
            turn.put("parts", parts)
            contents.put(turn)
        }

        val userTurn = JSONObject()
        userTurn.put("role", "user")
        val userParts = JSONArray()
        userParts.put(JSONObject().put("text", userText))
        userTurn.put("parts", userParts)
        contents.put(userTurn)

        val body = JSONObject()
        body.put("contents", contents)

        val sysInstruction = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", systemPrompt))
        sysInstruction.put("parts", sysParts)
        body.put("system_instruction", sysInstruction)

        val generationConfig = JSONObject()
        generationConfig.put("temperature", 0.9)
        generationConfig.put("maxOutputTokens", 200)
        body.put("generationConfig", generationConfig)

        val request = Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Connection nahi ho payi, bhidu. Net check kar: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback.onError("API error: ${it.code} - ${it.message}")
                        return
                    }
                    try {
                        val json = JSONObject(it.body?.string() ?: "")
                        val reply = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        history.add("user" to userText)
                        history.add("model" to reply)
                        if (history.size > 20) repeat(2) { history.removeAt(0) }

                        callback.onResult(reply.trim())
                    } catch (e: Exception) {
                        callback.onError("Reply samajh nahi aaya: ${e.message}")
                    }
                }
            }
        })
    }

    fun clearHistory() { history.clear() }
}
