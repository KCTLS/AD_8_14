package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.adproject.api.ApiClient
import com.example.adproject.model.QsInform
import kotlinx.coroutines.*

class ExerciseActivity : AppCompatActivity() {

    // --- 网络：统一用 ApiClient 单例 ---
    val api by lazy { ApiClient.api }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 搜索
    private var currentQuery: String = ""
    private var searchJob: Job? = null

    // 是否进入“收紧模式”：存在任一筛选或搜索时为 true（全部清空后回到 false）
    private var hasInteracted = false

    // 默认兜底（首次/清空时使用）
    private val gradeDefaults = listOf(
        "grade1","grade2","grade3","grade4","grade5","grade6",
        "grade7","grade8","grade9","grade10","grade11","grade12"
    )
    private val subjectDefaults = listOf("language science","natural science","social science")
    private val categoryDefaults = listOf(
        "Adaptations","Adaptations and natural selection","Age of Exploration","Analyzing literature",
        "Anatomy and physiology","Animals","Asia: society and environment","Astronomy","Atoms and molecules",
        "Author's purpose","Author's purpose and tone","Basic economic principles","Banking and finance",
        "Biochemistry","Capitalization","Categories","Cells","Chemical reactions","Cities","Classification",
        "Classification and scientific names","Climate change","Colonial America","Comprehension strategies",
        "Conservation","Conservation and natural resources","Context clues","Creative techniques","Cultural celebrations",
        "Designing experiments","Descriptive details","Developing and supporting arguments","Domain-specific vocabulary",
        "Early 19th century American history","Early China","Early Modern Europe","Early Americas",
        "Earth events","Earth's features","Economics","Editing and revising","Electricity","Ecological interactions",
        "Engineering practices","English colonies in North America","Fossils","Force and motion","Formatting",
        "Genes to traits","Geography","Greece","Government","Heat and thermal energy","Heredity","Historical figures",
        "Independent reading comprehension","Informational texts: level 1","Islamic empires","Kinetic and potential energy",
        "Literary devices","Magnets","Maps","Materials","Mixtures","Natural resources and human impacts",
        "Oceania: geography","Oceans and continents","Opinion writing","Particle motion and energy","Persuasive strategies",
        "Phrases and clauses","Photosynthesis","Physical Geography","Physical and chemical change","Plant reproduction",
        "Plants","Poetry elements","Pronouns","Pronouns and antecedents","Read-alone texts","Reading-comprehension",
        "Reference skills","Research skills","Rhyming","Rocks and minerals","Scientific names","Science-and-engineering-practices",
        "Sentences, fragments, and run-ons","Shades of meaning","Short and long vowels","Social studies skills","Solutions",
        "States","State capitals","States of matter","Supply and demand","Text structure","The Americas: geography",
        "The Antebellum period","The American Revolution","The Civil War","The Civil War and Reconstruction",
        "The Constitution","The Early Republic","The Jacksonian period","The Silk Road","Thermal energy",
        "Topographic maps","Traits","Traits and heredity","Units and measurement","Velocity, acceleration, and forces",
        "Verb tense","Visual elements","Water cycle","Weather and climate","Word usage and nuance","World religions"
    )
    private val topicDefaults = listOf(
        "capitalization","chemistry","civics","culture","economics","earth-science",
        "figurative-language","global-studies","grammar","literacy-in-science","phonological-awareness",
        "physics","pronouns","punctuation","reading-comprehension","reference-skills","science-and-engineering-practices",
        "units-and-measurement","us-history","verbs","vocabulary","word-study",
        "world-history","writing-strategies"
    )

    // 当前可选项（由结果数据动态收紧；用于 Subject/Category/Topic）
    private var currentSubjectOptions  = subjectDefaults.toMutableList()
    private var currentCategoryOptions = categoryDefaults.toMutableList()
    private var currentTopicOptions    = topicDefaults.toMutableList()

    // —— 单选选择 —— //
    private var selectedGrade: String? = null
    private var selectedSubject: String? = null
    private var selectedCategory: String? = null
    private var selectedTopic: String? = null

    // 列表 & 适配器
    private lateinit var adapter: QuestionAdapter
    private lateinit var questionList: ListView

    // 分页
    private var currentPage = 1
    private var isLoading = false

    // 按钮
    private lateinit var btnGrade: Button
    private lateinit var btnSubject: Button
    private lateinit var btnCategory: Button
    private lateinit var btnTopic: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_exercise)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 先用本地缓存
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val cached = UserSession.name(this) ?: "User"
        welcomeText.text = "Good to see you, $cached.\nYour progress starts here."

// 🔹一行异步刷新：从服务端拿到名字就更新 UI（拿不到就维持缓存）
        coroutineScope.launch {
            UserSession.syncNameFromServer(this@ExerciseActivity)?.let { latest ->
                welcomeText.text = "Good to see you, $latest.\nYour progress starts here."
            }
        }

        initViews()
        loadInitialData()
    }

    private fun initViews() {
        btnGrade = findViewById(R.id.gradeButton)
        btnSubject = findViewById(R.id.subjectButton)
        btnCategory = findViewById(R.id.categoryButton)
        btnTopic = findViewById(R.id.topicButton)

        questionList = findViewById(R.id.questionList)

        val searchInput = findViewById<EditText>(R.id.searchEditText)
        val searchIcon = findViewById<ImageView>(R.id.searchIcon)

        // 点击放大镜
        searchIcon.setOnClickListener {
            currentQuery = searchInput.text?.toString()?.trim().orEmpty()
            hasInteracted = !noFilters()
            applyFilters()
            hideKeyboard(searchInput)
        }
        // 软键盘“搜索”
        searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                currentQuery = v.text?.toString()?.trim().orEmpty()
                hasInteracted = !noFilters()
                applyFilters()
                hideKeyboard(searchInput)
                true
            } else false
        }
        // 防抖
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                searchJob?.cancel()
                searchJob = coroutineScope.launch {
                    delay(400)
                    if (text != currentQuery) {
                        currentQuery = text
                        hasInteracted = !noFilters()
                        applyFilters()
                    }
                }
            }
        })

        // 适配器
        adapter = QuestionAdapter(this, mutableListOf())
        questionList.adapter = adapter

        // —— 四个按钮：满足你描述的交互 —— //
        btnGrade.setOnClickListener {
            coroutineScope.launch {
                val options = when {
                    selectedGrade != null -> listOf(selectedGrade!!)           // 已选：只显示自己
                    noFilters() -> gradeDefaults                               // 无任何筛选：全量
                    else -> fetchFacetOptions("grade")                         // 有其它筛选：按其余条件动态计算
                }
                showSingleChoiceDialog("Select Grade", options.ifEmpty { gradeDefaults }, selectedGrade) { chosen ->
                    selectedGrade = chosen
                    btnGrade.text = chosen ?: "Grade"
                    hasInteracted = !noFilters()
                    applyFilters()
                }
            }
        }

        btnSubject.setOnClickListener {
            coroutineScope.launch {
                val options = when {
                    selectedSubject != null -> listOf(selectedSubject!!)
                    noFilters() -> subjectDefaults
                    else -> fetchFacetOptions("subject")
                }
                showSingleChoiceDialog("Select Subject", options.ifEmpty { currentSubjectOptions }, selectedSubject) { chosen ->
                    selectedSubject = chosen
                    btnSubject.text = chosen ?: "Subject"
                    hasInteracted = !noFilters()
                    applyFilters()
                }
            }
        }

        btnCategory.setOnClickListener {
            coroutineScope.launch {
                val options = when {
                    selectedCategory != null -> listOf(selectedCategory!!)
                    noFilters() -> categoryDefaults
                    else -> fetchFacetOptions("category")
                }
                showSingleChoiceDialog("Select Category", options.ifEmpty { currentCategoryOptions }, selectedCategory) { chosen ->
                    selectedCategory = chosen
                    btnCategory.text = chosen ?: "Category"
                    hasInteracted = !noFilters()
                    applyFilters()
                }
            }
        }

        btnTopic.setOnClickListener {
            coroutineScope.launch {
                val options = when {
                    selectedTopic != null -> listOf(selectedTopic!!)
                    noFilters() -> topicDefaults
                    else -> fetchFacetOptions("topic")
                }
                showSingleChoiceDialog("Select Topic", options.ifEmpty { currentTopicOptions }, selectedTopic) { chosen ->
                    selectedTopic = chosen
                    btnTopic.text = chosen ?: "Topic"
                    hasInteracted = !noFilters()
                    applyFilters()
                }
            }
        }

        // 底部导航
        val exerciseButton = findViewById<Button>(R.id.exerciseButton)
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)
        val classButton = findViewById<Button>(R.id.classButton)
        val homeButton = findViewById<Button>(R.id.homeButton)
        setSelectedButton(exerciseButton)

        exerciseButton.setOnClickListener { setSelectedButton(exerciseButton) }
        dashboardButton.setOnClickListener {
            setSelectedButton(dashboardButton)
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        classButton.setOnClickListener {
            setSelectedButton(classButton)
            startActivity(Intent(this, ClassActivity::class.java))
        }
        homeButton.setOnClickListener {
            setSelectedButton(homeButton)
            startActivity(Intent(this, HomeActivity::class.java))
        }

        // 列表点击 → 进入题目页
        questionList.setOnItemClickListener { _, _, position, _ ->
            val questionId = adapter.getItem(position)?.id ?: return@setOnItemClickListener
            findViewById<View>(R.id.searchCard).visibility = View.GONE
            findViewById<View>(R.id.filterCard).visibility = View.GONE
            questionList.visibility = View.GONE
            findViewById<View>(R.id.fragmentContainer).visibility = View.VISIBLE

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, QuestionFragment.newInstance(questionId))
                .addToBackStack(null)
                .commit()
        }

        // 滚动触底自动加载
        questionList.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                if (!isLoading && firstVisibleItem + visibleItemCount >= totalItemCount && totalItemCount != 0) {
                    loadNextPage()
                }
            }
        })
    }

    private fun loadInitialData() {
        applyFilters()
    }

    /** 是否完全没有筛选与搜索 */
    private fun noFilters(): Boolean =
        selectedGrade == null &&
                selectedSubject == null &&
                selectedCategory == null &&
                selectedTopic == null &&
                currentQuery.isBlank()

    /** 打开弹窗前：忽略自身维度，按其余筛选动态计算候选（抓 1~3 页即可） */
    private suspend fun fetchFacetOptions(facet: String): List<String> = withContext(Dispatchers.IO) {
        val result = linkedSetOf<String>()
        var page = 1
        val maxPages = 3
        while (page <= maxPages) {
            val resp = api.viewQuestion(
                keyword = "",
                questionName = currentQuery,
                grade     = if (facet == "grade") ""    else (selectedGrade ?: ""),
                subject   = if (facet == "subject") ""  else (selectedSubject ?: ""),
                category  = if (facet == "category") "" else (selectedCategory ?: ""),
                topic     = if (facet == "topic") ""    else (selectedTopic ?: ""),
                page = page,
                questionIndex = -1
            )
            if (!resp.isSuccessful) break
            val items = resp.body()?.data?.items ?: emptyList()
            if (items.isEmpty()) break

            for (q in items) {
                val v = when (facet) {
                    "grade"    -> q.grade
                    "subject"  -> q.subject
                    "category" -> q.category
                    "topic"    -> q.topic
                    else -> null
                }?.trim()
                if (!v.isNullOrEmpty()) result += v
            }
            page++
        }
        result.toList().sorted()
    }

    /** 单选弹窗 */
    private fun showSingleChoiceDialog(
        title: String,
        options: List<String>,
        current: String?,
        onPicked: (String?) -> Unit
    ) {
        val display = options.toTypedArray()
        var chosenIndex = if (current != null) options.indexOf(current) else -1

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(display, chosenIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton("Apply") { d, _ ->
                onPicked(if (chosenIndex in options.indices) options[chosenIndex] else null)
                d.dismiss()
            }
            .setNegativeButton("Clear") { d, _ ->
                onPicked(null); d.dismiss()
            }
            .setNeutralButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun setSelectedButton(selectedButton: Button) {
        findViewById<Button>(R.id.exerciseButton).isSelected = false
        findViewById<Button>(R.id.dashboardButton).isSelected = false
        findViewById<Button>(R.id.classButton).isSelected = false
        findViewById<Button>(R.id.homeButton).isSelected = false
        selectedButton.isSelected = true
    }

    /** 应用筛选并刷新第一页；随后重建候选 */
    private fun applyFilters() {
        currentPage = 1
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.viewQuestion(
                        keyword = "",
                        questionName = currentQuery,
                        grade = selectedGrade ?: "",
                        subject = selectedSubject ?: "",
                        topic = selectedTopic ?: "",
                        category = selectedCategory ?: "",
                        page = currentPage,
                        questionIndex = -1
                    )
                }

                if (response.isSuccessful) {
                    response.body()?.let { resultDTO ->
                        if (resultDTO.errorMessage == null) {
                            val questions: List<QsInform> = resultDTO.data?.items ?: emptyList()
                            if (questions.isEmpty()) {
                                Toast.makeText(this@ExerciseActivity, "No questions match the criteria", Toast.LENGTH_SHORT).show()
                            }
                            adapter.updateData(questions.toMutableList())
                            rebuildFacetOptionsFromData()
                        } else {
                            Toast.makeText(this@ExerciseActivity, "Error: ${resultDTO.errorMessage}", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(this@ExerciseActivity, "Request failed: server returned empty data", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ExerciseActivity, "Network error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: retrofit2.HttpException) {
                Toast.makeText(this@ExerciseActivity, "HTTP error: ${e.message()}", Toast.LENGTH_SHORT).show()
            } catch (e: java.io.IOException) {
                Toast.makeText(this@ExerciseActivity, "Network connection failed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ExerciseActivity, "Unknown error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Load next page when reaching the bottom */
    fun loadNextPage(onAppended: ((List<QsInform>) -> Unit)? = null) {
        if (isLoading) return
        isLoading = true
        val nextPage = currentPage + 1

        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.viewQuestion(
                        keyword = "",
                        questionName = currentQuery,
                        grade = selectedGrade ?: "",
                        subject = selectedSubject ?: "",
                        topic = selectedTopic ?: "",
                        category = selectedCategory ?: "",
                        page = nextPage,
                        questionIndex = -1
                    )
                }

                if (response.isSuccessful) {
                    val resultDTO = response.body()
                    if (resultDTO?.errorMessage == null) {
                        val questions: List<QsInform> = resultDTO?.data?.items ?: emptyList()
                        if (questions.isNotEmpty()) {
                            currentPage = nextPage
                            adapter.addItems(questions.toMutableList())
                            rebuildFacetOptionsFromData()
                            onAppended?.invoke(questions)
                        } else {
                            Toast.makeText(this@ExerciseActivity, "No more questions", Toast.LENGTH_SHORT).show()
                            onAppended?.invoke(emptyList())
                        }
                    } else {
                        Toast.makeText(this@ExerciseActivity, "Error: ${resultDTO.errorMessage}", Toast.LENGTH_SHORT).show()
                        onAppended?.invoke(emptyList())
                    }
                } else {
                    Toast.makeText(this@ExerciseActivity, "Network error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    onAppended?.invoke(emptyList())
                }
            } catch (e: retrofit2.HttpException) {
                Toast.makeText(this@ExerciseActivity, "HTTP error: ${e.message()}", Toast.LENGTH_SHORT).show()
                onAppended?.invoke(emptyList())
            } catch (e: java.io.IOException) {
                Toast.makeText(this@ExerciseActivity, "Network connection failed", Toast.LENGTH_SHORT).show()
                onAppended?.invoke(emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ExerciseActivity, "Unknown error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                onAppended?.invoke(emptyList())
            } finally {
                isLoading = false
            }
        }
    }


    /**
     * 重建可选项：
     * - 尚未交互：保持全量默认（不收紧）
     * - 收紧模式：用当前列表数据收紧 Subject/Category/Topic（Grade 不依赖当前页）
     */
    private fun rebuildFacetOptionsFromData() {
        if (!hasInteracted) {
            currentSubjectOptions  = subjectDefaults.toMutableList()
            currentCategoryOptions = categoryDefaults.toMutableList()
            currentTopicOptions    = topicDefaults.toMutableList()
            return
        }

        val data = adapter.getData()
        if (data.isEmpty()) {
            currentSubjectOptions  = subjectDefaults.toMutableList()
            currentCategoryOptions = categoryDefaults.toMutableList()
            currentTopicOptions    = topicDefaults.toMutableList()
            return
        }

        // ⚠️ 若 QsInform 字段名不同，请改成你的实际字段
        val subjectsInData   = data.mapNotNull { it.subject?.trim() }.filter { it.isNotEmpty() }.toSet()
        val categoriesInData = data.mapNotNull { it.category?.trim() }.filter { it.isNotEmpty() }.toSet()
        val topicsInData     = data.mapNotNull { it.topic?.trim() }.filter { it.isNotEmpty() }.toSet()

        if (subjectsInData.isNotEmpty())   currentSubjectOptions  = subjectsInData.sorted().toMutableList()
        if (categoriesInData.isNotEmpty()) currentCategoryOptions = categoriesInData.sorted().toMutableList()
        if (topicsInData.isNotEmpty())     currentTopicOptions    = topicsInData.sorted().toMutableList()

        // 选择合法性（grade 只需在全量里）
        if (selectedSubject != null && selectedSubject !in currentSubjectOptions) {
            selectedSubject = null; btnSubject.text = "Subject"
        }
        if (selectedCategory != null && selectedCategory !in currentCategoryOptions) {
            selectedCategory = null; btnCategory.text = "Category"
        }
        if (selectedTopic != null && selectedTopic !in currentTopicOptions) {
            selectedTopic = null; btnTopic.text = "Topic"
        }
        if (selectedGrade != null && selectedGrade !in gradeDefaults) {
            selectedGrade = null; btnGrade.text = "Grade"
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun getAdapterData(): List<QsInform> = adapter.getData()

    fun getNextQuestionIdOrLoad(currentId: Int, onReady: (Int?) -> Unit) {
        val data = adapter.getData()
        val idx = data.indexOfFirst { it.id == currentId }
        if (idx == -1) { onReady(null); return }
        if (idx + 1 < data.size) { onReady(data[idx + 1].id); return }
        loadNextPage { appended ->
            if (appended.isNotEmpty()) onReady(appended.first().id) else onReady(null)
        }
    }

    fun getPrevQuestionId(currentId: Int): Int? {
        val data = adapter.getData()
        val idx = data.indexOfFirst { it.id == currentId }
        return if (idx > 0) data[idx - 1].id else null
    }

    fun showMainUI() {
        findViewById<View>(R.id.searchCard).visibility = View.VISIBLE
        findViewById<View>(R.id.filterCard).visibility = View.VISIBLE
        questionList.visibility = View.VISIBLE
        findViewById<View>(R.id.fragmentContainer).visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

