package com.example.adproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class QuestionHost : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_question_host)

        if (savedInstanceState == null) {
            val qid = intent.getIntExtra("questionId", 0)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, QuestionFragment.newInstance(qid))
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
