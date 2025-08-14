package com.example.adproject

import android.content.Context

object AssignmentProgressStore {
    private const val PREFS = "assignment_progress"

    private fun key(aid: Int) = "a_$aid"

    data class Progress(val answers: MutableMap<Int, Int> = mutableMapOf(),
                        var completed: Boolean = false)

    fun get(ctx: Context, aid: Int): Progress {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(key(aid), null) ?: return Progress()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<Int, Int>()
            val ans = obj.optJSONObject("answers") ?: org.json.JSONObject()
            ans.keys().forEach { k -> map[k.toInt()] = ans.getInt(k) }
            Progress(map, obj.optBoolean("completed", false))
        } catch (_: Exception) { Progress() }
    }

    fun setAnswer(ctx: Context, aid: Int, qid: Int, choice: Int) {
        val p = get(ctx, aid)
        p.answers[qid] = choice
        save(ctx, aid, p)
    }

    fun setCompleted(ctx: Context, aid: Int, done: Boolean) {
        val p = get(ctx, aid); p.completed = done; save(ctx, aid, p)
    }

    private fun save(ctx: Context, aid: Int, p: Progress) {
        val obj = org.json.JSONObject()
        val m = org.json.JSONObject()
        p.answers.forEach { (k, v) -> m.put(k.toString(), v) }
        obj.put("answers", m); obj.put("completed", p.completed)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(aid), obj.toString()).apply()
    }
}
