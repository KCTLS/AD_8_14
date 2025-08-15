package com.example.adproject

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestionFragment : Fragment() {

    companion object {
        private const val ARG_QUESTION_ID = "questionId"
        fun newInstance(questionId: Int) = QuestionFragment().apply {
            arguments = Bundle().apply { putInt(ARG_QUESTION_ID, questionId) }
        }
    }

    private var questionId: Int = 0
    private val api by lazy { ApiClient.api }

    private lateinit var questionText: TextView
    private lateinit var questionImage: ImageView
    private lateinit var optionsGroup: RadioGroup
    private lateinit var confirmBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var returnBtn: Button
    private lateinit var feedbackText: TextView

    private var choices: List<String> = emptyList()
    private var correctAnswerIndex: Int = -1
    private var selectedOptionIndex: Int = -1
    private var isAnswered = false

    // ✅ 推荐模式下的 id 列表（若为空则说明在 ExerciseActivity 里）
    private val recList: IntArray by lazy {
        requireActivity().intent?.getIntArrayExtra("id_list") ?: intArrayOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        questionId = arguments?.getInt(ARG_QUESTION_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_question, container, false).also { view ->
        questionText = view.findViewById(R.id.questionText)
        questionImage = view.findViewById(R.id.questionImage)
        optionsGroup = view.findViewById(R.id.optionsGroup)
        confirmBtn = view.findViewById(R.id.confirmButton)
        prevBtn = view.findViewById(R.id.prevButton)
        nextBtn = view.findViewById(R.id.nextButton)
        returnBtn = view.findViewById(R.id.returnButton)
        feedbackText = view.findViewById(R.id.feedbackText)

        confirmBtn.setOnClickListener { onConfirmClicked() }
        prevBtn.setOnClickListener { onPrevClicked() }
        nextBtn.setOnClickListener { onNextClicked() }

        returnBtn.setOnClickListener {
            when (val act = activity) {
                is ExerciseActivity -> {
                    parentFragmentManager.popBackStack()
                    act.showMainUI()
                }
                else -> {
                    requireActivity().setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra("answeredId", questionId)
                    )
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 在 Recommended 模式下：根据 recList 决定按钮可用性
        if (activity !is ExerciseActivity && recList.isNotEmpty()) {
            val idx = recList.indexOf(questionId)
            prevBtn.isEnabled = (idx > 0)
            nextBtn.isEnabled = (idx >= 0 && idx < recList.size - 1)
        } else {
            // 旧逻辑：ExerciseActivity 决定上一/下一题
            val act = activity as? ExerciseActivity
            prevBtn.isEnabled = (act?.getPrevQuestionId(questionId) != null)
            nextBtn.isEnabled = (act != null)
        }

        loadAndDisplayQuestion()
    }

    // ===== 推荐模式的 Prev/Next =====
    private fun onPrevClicked() {
        if (activity is ExerciseActivity || recList.isEmpty()) {
            // 旧逻辑
            navigateByDelta(-1)
            return
        }
        val idx = recList.indexOf(questionId)
        if (idx <= 0) {
            Toast.makeText(requireContext(), "This is already the first question", Toast.LENGTH_SHORT).show()
            return
        }
        val prevId = recList[idx - 1]
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, newInstance(prevId))
            .commit()
    }

    private fun onNextClicked() {
        if (activity is ExerciseActivity || recList.isEmpty()) {
            // 旧逻辑
            goNext()
            return
        }
        val idx = recList.indexOf(questionId)
        if (idx < 0 || idx >= recList.size - 1) {
            Toast.makeText(requireContext(), "This is already the last question", Toast.LENGTH_SHORT).show()
            return
        }
        val nextId = recList[idx + 1]
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, newInstance(nextId))
            .commit()
    }
    // ===============================

    private fun loadAndDisplayQuestion() {
        isAnswered = false
        selectedOptionIndex = -1
        feedbackText.visibility = View.GONE
        feedbackText.text = ""

        viewLifecycleOwner.lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) { api.getQuestionById(questionId) }
            if (!isAdded) return@launch

            if (resp.isSuccessful) {
                val dto = resp.body()?.data
                if (dto != null) {
                    questionText.text = dto.question
                    correctAnswerIndex = dto.answer
                    choices = dto.choices.orEmpty()

                    if (!dto.image.isNullOrEmpty()) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val bmp = withContext(Dispatchers.IO) {
                                    val bytes = Base64.decode(dto.image, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                                if (isAdded) questionImage.setImageBitmap(bmp)
                            } catch (_: Exception) {
                                if (isAdded) questionImage.setImageResource(R.drawable.error_image)
                            }
                        }
                    } else {
                        questionImage.setImageResource(R.drawable.placeholder_image)
                    }

                    optionsGroup.removeAllViews()
                    choices.forEach { text ->
                        val rb = RadioButton(requireContext()).apply {
                            id = View.generateViewId()
                            this.text = text
                            setPadding(8, 8, 8, 8)
                        }
                        optionsGroup.addView(rb)
                    }
                    optionsGroup.setOnCheckedChangeListener { group, checkedId ->
                        selectedOptionIndex = group.indexOfChild(group.findViewById(checkedId))
                    }

                    // 再次刷新 Prev 可用性（Exercise 模式）
                    val act = (activity as? ExerciseActivity)
                    if (act != null) {
                        prevBtn.isEnabled = (act.getPrevQuestionId(questionId) != null)
                    }
                } else {
                    Toast.makeText(requireContext(), "Question data is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to load: ${resp.code()}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onConfirmClicked() {
        if (selectedOptionIndex == -1) {
            Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show()
            return
        }
        val correct = (selectedOptionIndex == correctAnswerIndex)
        val msg = if (correct) "✅ Correct!"
        else "❌ Incorrect. The correct answer is: ${choices.getOrNull(correctAnswerIndex) ?: "-"}"

        feedbackText.text = msg
        feedbackText.visibility = View.VISIBLE

        // 提交结果（保持原逻辑）
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rsp = withContext(Dispatchers.IO) {
                    api.answerQuestion(
                        id = questionId,
                        correct = if (correct) 1 else 0,
                        param = selectedOptionIndex
                    )
                }
                if (rsp.isSuccessful && rsp.body()?.code == 1) {
                    Toast.makeText(requireContext(), "Submitted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Submission failed", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
            }
        }

        isAnswered = true
    }

    // ======= 旧的 ExerciseActivity 跳转逻辑保持不变 =======
    private fun goNext() {
        val act = activity as? ExerciseActivity ?: return
        nextBtn.isEnabled = false
        act.getNextQuestionIdOrLoad(questionId) { nextId ->
            if (!isAdded) return@getNextQuestionIdOrLoad
            nextBtn.isEnabled = true
            if (nextId != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, newInstance(nextId))
                    .commit()
            } else {
                Toast.makeText(requireContext(), "This is already the last question", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateByDelta(delta: Int) {
        val act = (activity as? ExerciseActivity) ?: return
        val nextId = if (delta < 0) act.getPrevQuestionId(questionId) else null
        if (delta > 0) { // 下一题按钮单独由 goNext() 处理分页
            goNext(); return
        }
        if (nextId == null) {
            Toast.makeText(requireContext(), "This is already the first question", Toast.LENGTH_SHORT).show()
            return
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, newInstance(nextId))
            .commit()
    }
    // =====================================================
}
