package com.pranav.study.cet_study_sprint

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences
    private var grade = "11"
    private var exam = "CET"
    private var focusSeconds = 25 * 60
    private var running = false
    private var isBreak = false
    private var phaseDurationSeconds = 25 * 60
    private var phaseEndElapsedMs = 0L
    private var timerRunnable: Runnable? = null
    private var activeTab = "Home"
    private var selectedSubject = "Physics"
    private var syllabusFilter = "All"
    private val tabButtons = mutableMapOf<String, Pair<LinearLayout, TextView>>()
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val questions = listOf(
        Mcq("Physics", "A car moves east and then north with equal displacement. Resultant angle to east?", arrayOf("30°", "45°", "60°", "90°"), 1, "Equal perpendicular components give tan θ = 1."),
        Mcq("Physics", "If a planet's mass doubles at fixed radius, surface gravity becomes:", arrayOf("Half", "Unchanged", "Double", "Four times"), 2, "g = GM/R²."),
        Mcq("Chemistry", "How many moles are in 9 g water? M = 18 g mol⁻¹", arrayOf("0.25", "0.5", "1", "2"), 1, "moles = mass / molar mass."),
        Mcq("Chemistry", "Which change is oxidation?", arrayOf("Gain electrons", "Lower oxidation number", "Loss of electrons", "Gain hydrogen"), 2, "Oxidation is loss of electrons."),
        Mcq("Mathematics", "If sin θ = 3/5 for an acute angle, cos θ is:", arrayOf("2/5", "3/4", "4/5", "5/4"), 2, "Use sin²θ + cos²θ = 1."),
        Mcq("Mathematics", "A fair coin is tossed twice. Probability of exactly one head:", arrayOf("1/4", "1/3", "1/2", "3/4"), 2, "HT and TH are two of four outcomes."),
        Mcq("Biology", "Which cell organelle is the main site of aerobic respiration?", arrayOf("Ribosome", "Mitochondrion", "Golgi body", "Lysosome"), 1, "Mitochondria produce most cellular ATP during aerobic respiration."),
        Mcq("Biology", "If two heterozygous pea plants are crossed, what fraction of offspring are homozygous recessive?", arrayOf("One quarter", "One half", "Three quarters", "All"), 0, "A monohybrid Aa × Aa cross gives aa in one of four outcomes.")
    )
    private val syllabus11 = mapOf(
        "Physics" to listOf("Vectors", "Error Analysis", "Motion in a Plane", "Laws of Motion", "Gravitation", "Thermal Properties", "Sound", "Optics", "Electrostatics", "Semiconductors"),
        "Chemistry" to listOf("Basic Concepts", "Structure of Atom", "Chemical Bonding", "Redox Reactions", "States of Matter", "Hydrocarbons", "Organic Chemistry"),
        "Mathematics" to listOf("Trigonometry II", "Straight Line", "Circle", "Probability", "Complex Numbers", "Functions", "Limits", "Continuity")
    )
    private val syllabus12 = mapOf(
        "Physics" to listOf("Rotational Dynamics", "Thermodynamics", "Oscillations", "Wave Optics", "Current Electricity", "Magnetic Fields", "Electromagnetic Induction", "AC Circuits", "Semiconductor Devices"),
        "Chemistry" to listOf("Solid State", "Solutions", "Ionic Equilibria", "Electrochemistry", "Chemical Kinetics", "Coordination Compounds", "Amines", "Biomolecules"),
        "Mathematics" to listOf("Matrices", "Trigonometric Functions", "Vectors", "Line and Plane", "Differentiation", "Integration", "Differential Equations", "Probability Distributions")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("study_sprint", MODE_PRIVATE)
        if (prefs.contains("profile_gender")) prefs.edit().remove("profile_gender").apply()
        grade = prefs.getString("grade", "11") ?: "11"
        exam = prefs.getString("exam", "CET") ?: "CET"
        focusSeconds = prefs.getInt("pomodoro_focus", 25) * 60
        phaseDurationSeconds = focusSeconds
        val legacyScreen = intent.getStringExtra("legacy_screen")
        if (legacyScreen != null) {
            showShell()
            when (legacyScreen) {
                "practice" -> showPractice()
                "import" -> importOwnedMcqs()
                "arihant" -> showArihant()
                "limits" -> showLimits()
                "apps" -> showStudyApps()
                "plan" -> showPlanner()
                else -> showHome()
            }
        } else if (prefs.getBoolean("onboarding_v2", false)) { showShell(); showHome() } else showWelcome()
    }

    override fun onBackPressed() {
        if (activeTab == "Focus") {
            pauseTimer()
            showShell()
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showWelcome() {
        window.statusBarColor = 0xfff3faf6.toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(24), dp(28), dp(24)); setBackgroundColor(0xfff3faf6.toInt()) }
        root.addView(FocusProgressView(this).apply { progress = 1f }, LinearLayout.LayoutParams(-1, dp(210)))
        root.addView(TextView(this).apply { text = "Welcome to Study Sprint"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(0xff1c3d38.toInt()) })
        root.addView(TextView(this).apply { text = "Choose your path once, then get straight to studying."; textSize = 15f; gravity = Gravity.CENTER; setTextColor(0xff678178.toInt()); setPadding(0, dp(6), 0, dp(24)) })
        root.addView(TextView(this).apply { text = "Your exam"; textSize = 15f; setTextColor(0xff36564f.toInt()); setPadding(0, 0, 0, dp(6)) })
        val courses = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("CET", "JEE", "NEET").forEachIndexed { index, option -> courses.addView(RadioButton(this).apply { id = 3000 + index; text = option; textSize = 14f }, RadioGroup.LayoutParams(0, dp(48), 1f)) }
        courses.check(3000 + listOf("CET", "JEE", "NEET").indexOf(exam).coerceAtLeast(0))
        root.addView(courses)
        root.addView(TextView(this).apply { text = "Your class"; textSize = 15f; setTextColor(0xff36564f.toInt()); setPadding(0, dp(12), 0, dp(6)) })
        val classes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Class 11", "Class 12").forEachIndexed { index, option -> classes.addView(RadioButton(this).apply { id = 4000 + index; text = option; textSize = 14f }, RadioGroup.LayoutParams(0, dp(48), 1f)) }
        classes.check(if (grade == "11") 4000 else 4001)
        root.addView(classes)
        val name = EditText(this).apply { hint = "Your name"; setSingleLine(true); setPadding(dp(16), dp(12), dp(16), dp(12)); background = rounded(0xffffffff.toInt(), 14).apply { setStroke(dp(1), 0xffd8eee2.toInt()) } }
        name.setText(prefs.getString("profile_name", ""))
        root.addView(name, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(Button(this).apply {
            text = "Continue on this phone"; stylePrimary(this)
            setOnClickListener {
                exam = when (courses.checkedRadioButtonId) { 3001 -> "JEE"; 3002 -> "NEET"; else -> "CET" }
                grade = if (classes.checkedRadioButtonId == 4001) "12" else "11"
                prefs.edit().putString("profile_name", name.text.toString().trim()).putString("exam", exam).putString("grade", grade).putBoolean("onboarding_v2", true).apply()
                showShell(); showHome()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(22) })
        root.addView(Button(this).apply { text = "Sign in with Google · setup needed"; styleAction(this); setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Google sign-in setup").setMessage("A Google OAuth or Firebase project is required before this can securely sign in. You can continue on this phone now; your study data will remain local.").setPositiveButton("OK", null).show() } }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(9) })
        root.addView(TextView(this).apply { text = "Local profile only until Google sign-in is configured · No cloud sync"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(0xff678178.toInt()); setPadding(0, dp(14), 0, 0) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun showShell() {
        window.statusBarColor = 0xff3ea78b.toInt()
        window.navigationBarColor = 0xffffffff.toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfff3faf6.toInt()) }
        val header = LinearLayout(this).apply {
            setPadding(dp(20), dp(16), dp(20), dp(12)); gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff56b59b.toInt(), 0xff178575.toInt()))
        }
        header.addView(TextView(this).apply { text = "Study Sprint"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xffffffff.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        val selector = Spinner(this)
        selector.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, arrayOf("Class 11", "Class 12")) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = super.getView(position, convertView, parent).also { (it as TextView).setTextColor(0xffffffff.toInt()) }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        selector.setSelection(if (grade == "11") 0 else 1)
        selector.contentDescription = "Choose class"
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { val nextGrade = if (position == 0) "11" else "12"; if (nextGrade != grade) { grade = nextGrade; prefs.edit().putString("grade", grade).apply(); if (this@MainActivity::body.isInitialized) refreshForGradeChange() } }
        }
        header.addView(selector)
        val more = Button(this).apply {
            text = "More"; isAllCaps = false; textSize = 13f; contentDescription = "Open more study tools"
            setTextColor(0xffffffff.toInt()); background = rounded(0x33ffffff, 14); elevation = 0f
            setOnClickListener { showMore() }
        }
        header.addView(more, LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(6) })
        root.addView(header)
         val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(22)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(9)); background = rounded(0xffffffff.toInt(), 0) }
        val tabs = listOf(
            Triple("Home", R.drawable.nav_home, ::showHome),
            Triple("Syllabus", R.drawable.nav_syllabus, ::showSyllabus),
            Triple("Practice", R.drawable.nav_practice, ::showPractice),
            Triple("Plan", R.drawable.nav_plan, ::showPlanner),
            Triple("Focus", R.drawable.nav_focus, ::showFocus)
        )
        tabs.forEach { (title, iconId, action) ->
            val icon = ImageView(this).apply { setImageResource(iconId); setColorFilter(0xff678178.toInt()) }
            val label = TextView(this).apply { text = title; textSize = 11f; gravity = Gravity.CENTER; setTextColor(0xff678178.toInt()) }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; contentDescription = "Open $title"
                addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                setOnClickListener { selectTab(title); action() }
            }
            tabButtons[title] = item to label
            bottom.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        root.addView(bottom)
        selectTab(activeTab)
        setContentView(root)
    }

    private fun clear(title: String, subtitle: String = "") {
        if (title != "Pomodoro" && running) pauseTimer()
        body.removeAllViews()
        body.addView(TextView(this).apply { text = title; textSize = 27f; letterSpacing = 0.01f; setTextColor(0xff173b36.toInt()); setPadding(0, dp(16), 0, dp(5)) })
        if (subtitle.isNotBlank()) body.addView(TextView(this).apply { text = subtitle; textSize = 15f; setTextColor(0xff607b72.toInt()); setPadding(0, 0, 0, dp(12)) })
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 20): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
        background = rounded(0xffffffff.toInt(), 24).apply { setStroke(dp(1), 0xffdceee5.toInt()) }
        elevation = 0f; translationZ = 0f
    }
    private fun styleAction(button: Button) {
        button.isAllCaps = false; button.textSize = 15f; button.minHeight = dp(48); button.setTextColor(0xff137f70.toInt()); button.elevation = 0f
        button.setPadding(dp(18), dp(10), dp(18), dp(10)); button.background = rounded(0xffe5f5ef.toInt(), 18).apply { setStroke(dp(1), 0xffcce9dc.toInt()) }
    }
    private fun stylePrimary(button: Button) {
        button.isAllCaps = false; button.textSize = 15f; button.minHeight = dp(48); button.setTextColor(0xffffffff.toInt()); button.elevation = 0f
        button.setPadding(dp(18), dp(10), dp(18), dp(10)); button.background = rounded(0xff137f70.toInt(), 18)
    }
    private fun selectTab(title: String) {
        activeTab = title
        tabButtons.forEach { (name, tab) ->
            val selected = name == title
            val (item, label) = tab
            val color = if (selected) 0xff137f70.toInt() else 0xff678178.toInt()
            label.setTextColor(color)
            label.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            (item.getChildAt(0) as ImageView).setColorFilter(color)
            item.background = rounded(if (selected) 0xffe5f5ef.toInt() else 0xffffffff.toInt(), 16)
        }
    }
    private fun refreshForGradeChange() {
        when (activeTab) {
            "Home" -> showHome()
            "Syllabus" -> showSyllabus()
            "Practice" -> showPractice()
            "Plan" -> showPlanner()
            "Focus" -> showFocus()
        }
    }
    private fun addCard(view: View) {
        if (view is Button) styleAction(view)
        body.addView(view, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(9), 0, dp(9)) })
    }
    private fun section(text: String) { body.addView(TextView(this).apply { this.text = text.uppercase(); textSize = 13f; letterSpacing = 0.12f; setTextColor(0xff467d6d.toInt()); setPadding(dp(4), dp(22), 0, dp(7)) }) }
    private fun studyMap(): Map<String, List<String>> = SyllabusData.chapters(exam, grade, syllabus11, syllabus12)
    private fun chapterKey(subject: String, index: Int): String = if (exam == "CET") "${grade}_${subject}_$index" else "${exam}_${grade}_${subject}_$index"
    private fun daysLeft(): Long { val target = prefs.getLong("exam_date", System.currentTimeMillis() + 547L * 86400000L); return max(0, (target - System.currentTimeMillis()) / 86400000L) }
    private fun showHome() {
        selectTab("Home")
        val name = prefs.getString("profile_name", "").orEmpty().trim()
        clear(if (name.isBlank()) "Good day, scholar" else "Good day, $name", "A little progress every day adds up.")
        val examMillis = prefs.getLong("exam_date", System.currentTimeMillis() + 547L * 86400000L)
        addCard(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff56b59b.toInt(), 0xff11776c.toInt())).apply { cornerRadius = dp(28).toFloat() }
            addView(TextView(this@MainActivity).apply { text = "YOUR $exam TARGET"; textSize = 12f; letterSpacing = 0.14f; setTextColor(0xffd7f5e6.toInt()) })
            addView(TextView(this@MainActivity).apply { text = "${daysLeft()} days left"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xffffffff.toInt()); setPadding(0, dp(7), 0, dp(3)) })
            addView(TextView(this@MainActivity).apply { text = dateFormat.format(Date(examMillis)); textSize = 14f; setTextColor(0xffe9fff4.toInt()) })
            addView(Button(this@MainActivity).apply { text = "Change exam date"; isAllCaps = false; setTextColor(0xff0b6f61.toInt()); background = rounded(0xffffffff.toInt(), 14); setOnClickListener { pickExamDate() } }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(17) })
        })
        val studyMap = studyMap()
        val total = studyMap.values.sumOf { it.size }
        val completed = studyMap.entries.sumOf { (subject, chapters) -> chapters.indices.count { index -> prefs.getBoolean(chapterKey(subject, index), false) } }
        val percent = if (total == 0) 0 else completed * 100 / total
        section("Your progress")
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "Class $grade syllabus"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff1c3d38.toInt()) })
            addView(TextView(this@MainActivity).apply { text = "$completed of $total chapters complete"; textSize = 14f; setTextColor(0xff678178.toInt()); setPadding(0, dp(5), 0, dp(14)) })
            addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = total; progress = completed; progressTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt()) }, LinearLayout.LayoutParams(-1, dp(8)))
            addView(TextView(this@MainActivity).apply { text = "$percent% complete"; textSize = 13f; setTextColor(0xff137f70.toInt()); setPadding(0, dp(9), 0, 0) })
        })
        studyMap.forEach { (subject, chapters) ->
            val done = chapters.indices.count { index -> prefs.getBoolean(chapterKey(subject, index), false) }
            addCard(card().apply {
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this@MainActivity).apply { text = subject; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff284a43.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this@MainActivity).apply { text = "$done / ${chapters.size}"; textSize = 14f; setTextColor(0xff678178.toInt()) })
                addView(row)
            })
        }
        addCard(card().apply { addView(TextView(this@MainActivity).apply { text = "${prefs.getInt("focus_sessions", 0)} focus sessions"; textSize = 16f; setTextColor(0xff25734d.toInt()) }) })
        section("Jump back in")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "Practice MCQs"; stylePrimary(this); setOnClickListener { showPractice() } }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        actions.addView(Button(this).apply { text = "Start focus"; styleAction(this); setOnClickListener { showFocus() } }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        addCard(actions)
        val journal = prefs.getString("journal", "").orEmpty()
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "What you learned today"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff284a43.toInt()) })
            addView(TextView(this@MainActivity).apply { text = journal.ifBlank { "Capture a concept or question from class." }; textSize = 14f; setTextColor(0xff678178.toInt()); setPadding(0, dp(8), 0, dp(4)) })
            addView(Button(this@MainActivity).apply { text = "Open planner"; styleAction(this); setOnClickListener { showPlanner() } })
        })
    }
    private fun pickExamDate() { val c = Calendar.getInstance(); DatePickerDialog(this, { _, y, m, d -> val value = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis; prefs.edit().putLong("exam_date", value).apply(); showHome() }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show() }

    private fun showSyllabus() {
        selectTab("Syllabus")
        clear("$exam syllabus", "Class $grade · Select a subject and mark chapters done.")
        val data = studyMap()
        if (selectedSubject !in data) selectedSubject = "Physics"
        if (exam != "CET") addCard(TextView(this).apply { text = "NCERT textbook chapter tracker · Check the official $exam exam syllabus separately for test coverage."; textSize = 13f; setTextColor(0xff678178.toInt()); setPadding(dp(8), dp(7), dp(8), dp(7)) })
        val total = data.values.sumOf { it.size }
        val complete = data.entries.sumOf { (subject, chapters) -> chapters.indices.count { index -> prefs.getBoolean(chapterKey(subject, index), false) } }
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "Overall progress"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff1c3d38.toInt()) })
            addView(TextView(this@MainActivity).apply { text = "$complete of $total chapters completed"; textSize = 14f; setTextColor(0xff678178.toInt()); setPadding(0, dp(5), 0, dp(12)) })
            addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = total; progress = complete; progressTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt()) }, LinearLayout.LayoutParams(-1, dp(7)))
        })
        section("Subjects")
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        data.keys.forEach { subject ->
            val selected = subject == selectedSubject
            val button = Button(this).apply {
                text = subject; isAllCaps = false; textSize = 12f; minWidth = 0; minHeight = dp(48)
                setTextColor(if (selected) 0xffffffff.toInt() else 0xff137f70.toInt())
                background = rounded(if (selected) 0xff137f70.toInt() else 0xffe5f5ef.toInt(), 14)
                setOnClickListener { selectedSubject = subject; showSyllabus() }
            }
            tabs.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        addCard(tabs)
        val chapters = data[selectedSubject] ?: emptyList()
        val panel = card()
        val progressLabel = TextView(this).apply { textSize = 14f; setTextColor(0xff137f70.toInt()) }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = chapters.size; progressTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt()) }
        fun refreshProgress() {
            val done = chapters.indices.count { index -> prefs.getBoolean(chapterKey(selectedSubject, index), false) }
            progressLabel.text = "$done of ${chapters.size} chapters complete"
            progress.progress = done
        }
        panel.addView(TextView(this).apply { text = selectedSubject; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff1c3d38.toInt()) })
        panel.addView(progressLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        panel.addView(progress, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(14); bottomMargin = dp(10) })
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("All", "To do", "Done").forEach { option ->
            filters.addView(Button(this).apply {
                text = option; isAllCaps = false; textSize = 12f; minWidth = 0
                setTextColor(if (syllabusFilter == option) 0xffffffff.toInt() else 0xff137f70.toInt())
                background = rounded(if (syllabusFilter == option) 0xff137f70.toInt() else 0xffe5f5ef.toInt(), 12)
                setOnClickListener { syllabusFilter = option; showSyllabus() }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        panel.addView(filters, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        chapters.forEachIndexed { index, chapter ->
            val key = chapterKey(selectedSubject, index)
            val checked = prefs.getBoolean(key, false)
            if (syllabusFilter == "To do" && checked || syllabusFilter == "Done" && !checked) return@forEachIndexed
            panel.addView(CheckBox(this).apply {
                text = chapter; textSize = 15f; minHeight = dp(52); setTextColor(0xff36564f.toInt())
                isChecked = checked
                setOnCheckedChangeListener { _, isDone -> prefs.edit().putBoolean(key, isDone).apply(); if (syllabusFilter == "All") refreshProgress() else showSyllabus() }
            })
        }
        refreshProgress()
        addCard(panel)
    }
    private fun showPractice() {
        selectTab("Practice")
        val saved = loadOwnedQuestions()
        clear("MCQ practice", "Original questions plus your own saved question sets.")
        val examQuestions = questions.filter { it.subject in studyMap().keys }
        val original = Button(this).apply { text = "Start original practice · ${examQuestions.size} questions"; stylePrimary(this); setOnClickListener { runQuiz(examQuestions, 0, 0, "Original practice") } }
        addCard(card().apply { addView(original) })
        if (saved.isNotEmpty()) {
            val yours = Button(this).apply { text = "Practice my saved set · ${saved.size} questions"; stylePrimary(this); setOnClickListener { runQuiz(saved, 0, 0, "My saved set") } }
            addCard(card().apply { addView(yours) })
        }
        addCard(Button(this).apply { text = "Add or edit my 10 MCQs"; setOnClickListener { importOwnedMcqs() } })
        addCard(card().apply { addView(TextView(this@MainActivity).apply { text = "Your own questions are stored only on this phone. Review the answer and explanation after every attempt."; setTextColor(0xff678178.toInt()) }) })
    }

    private fun runQuiz(items: List<Mcq>, index: Int, score: Int, setName: String) {
        if (index >= items.size) {
            clear("Practice complete", "$setName · Score $score / ${items.size}")
            val again = Button(this).apply { text = "Practice this set again"; stylePrimary(this); setOnClickListener { runQuiz(items, 0, 0, setName) } }
            addCard(card().apply { addView(again) })
            addCard(Button(this).apply { text = "Back to practice"; setOnClickListener { showPractice() } })
            return
        }
        val q = items[index]
        clear("Question ${index + 1} of ${items.size}", q.subject)
        val panel = card()
        panel.addView(TextView(this).apply { text = q.text; textSize = 19f; setTextColor(0xff202942.toInt()) })
        val choices = mutableListOf<Button>()
        var selected = -1
        var checked = false
        var nextScore = score
        fun redrawChoices() {
            choices.forEachIndexed { optionIndex, button ->
                button.setTextColor(0xff36564f.toInt())
                button.background = rounded(if (optionIndex == selected) 0xffe8efff.toInt() else 0xffffffff.toInt(), 14).apply { setStroke(dp(1), if (optionIndex == selected) 0xff90a7f3.toInt() else 0xffdce1ea.toInt()) }
            }
        }
        q.options.forEachIndexed { optionIndex, option ->
            val choice = Button(this).apply {
                text = "${'A' + optionIndex}. $option"; isAllCaps = false; textSize = 15f; gravity = Gravity.START or Gravity.CENTER_VERTICAL; minHeight = dp(50)
                setOnClickListener { if (!checked) { selected = optionIndex; redrawChoices() } }
            }
            choices += choice
            panel.addView(choice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        val feedback = TextView(this).apply { visibility = View.GONE; setPadding(dp(4), dp(14), dp(4), dp(4)); textSize = 15f }
        panel.addView(feedback)
        val action = Button(this).apply { text = "Check answer"; stylePrimary(this) }
        action.setOnClickListener {
            if (selected < 0) { feedback.visibility = View.VISIBLE; feedback.setTextColor(0xffb42318.toInt()); feedback.text = "Choose an answer first."; return@setOnClickListener }
            if (!checked) {
                checked = true
                val correct = selected == q.answer
                if (correct) nextScore++
                choices.forEach { it.isEnabled = false }
                feedback.visibility = View.VISIBLE
                feedback.setTextColor(if (correct) 0xff027a48.toInt() else 0xffb42318.toInt())
                feedback.text = if (correct) "Correct. ${q.explanation}" else "Correct answer: ${'A' + q.answer}. ${q.explanation}"
                action.text = "Next question"
            } else runQuiz(items, index + 1, nextScore, setName)
        }
        panel.addView(action, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addCard(panel)
    }

    private fun importOwnedMcqs() {
        clear("My 10 MCQs", "Add all four options and the correct answer. The set stays private on this phone.")
        val existing = loadOwnedQuestions()
        val forms = mutableListOf<McqForm>()
        repeat(10) { number ->
            val previous = existing.getOrNull(number)
            val question = EditText(this).apply { hint = "Question ${number + 1}"; setText(previous?.text.orEmpty()); minLines = 2 }
            val options = List(4) { index -> EditText(this).apply { hint = "Option ${'A' + index}"; setText(previous?.options?.getOrNull(index).orEmpty()); setSingleLine(true) } }
            val answer = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Correct answer: A", "Correct answer: B", "Correct answer: C", "Correct answer: D")); setSelection(previous?.answer ?: 0) }
            forms += McqForm(question, options, answer)
            addCard(card().apply { addView(TextView(this@MainActivity).apply { text = "QUESTION ${number + 1}"; textSize = 13f; letterSpacing = 0.1f; setTextColor(0xff467d6d.toInt()) }); addView(question); options.forEach { addView(it) }; addView(answer) })
        }
        val save = Button(this).apply { text = "Save my 10 MCQs"; stylePrimary(this) }
        save.setOnClickListener {
            if (forms.any { form -> form.question.text.toString().trim().isEmpty() || form.options.any { it.text.toString().trim().isEmpty() } }) {
                Toast.makeText(this, "Fill every question and option before saving.", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            val values = forms.map { form -> Mcq("My saved MCQs", form.question.text.toString().trim(), form.options.map { it.text.toString().trim() }.toTypedArray(), form.answer.selectedItemPosition, "Saved from your own set.") }
            val savedSet = JSONArray()
            values.forEach { question ->
                savedSet.put(JSONObject().put("question", question.text).put("options", JSONArray(question.options)).put("answer", question.answer))
            }
            prefs.edit().putString("owned_mcqs_json", savedSet.toString()).apply()
            Toast.makeText(this, "Your 10 MCQs are saved.", Toast.LENGTH_SHORT).show()
            showPractice()
        }
        addCard(card().apply { addView(save) })
    }

    private fun loadOwnedQuestions(): List<Mcq> {
        val json = prefs.getString("owned_mcqs_json", null)
        if (json != null) return runCatching {
            val items = JSONArray(json)
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val choices = item.getJSONArray("options")
                Mcq("My saved MCQs", item.getString("question"), Array(choices.length()) { choices.getString(it) }, item.getInt("answer").coerceIn(0, 3), "Saved from your own set.")
            }
        }.getOrDefault(emptyList())
        return (prefs.getStringSet("owned_mcqs", emptySet()) ?: emptySet()).mapNotNull { encoded ->
            runCatching {
                val values = encoded.split("|").map { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }
                if (values.size != 6) null else Mcq("My saved MCQs", values[0], values.subList(1, 5).toTypedArray(), values[5].toInt().coerceIn(0, 3), "Saved from your own set.")
            }.getOrNull()
        }
    }
    private fun showPlanner() {
        selectTab("Plan")
        clear("Your daily plan", "Keep your next study step simple and clear.")
        val addTaskButton = Button(this).apply { text = "+ Add study task"; stylePrimary(this); setOnClickListener { addTask() } }
        addCard(card().apply { addView(addTaskButton) })
        section("Study tasks")
        val tasks = prefs.getStringSet("tasks", emptySet()) ?: emptySet()
        if (tasks.isEmpty()) addCard(card().apply { addView(TextView(this@MainActivity).apply { text = "Nothing planned yet. Add a task to get started."; textSize = 15f; setTextColor(0xff678178.toInt()) }) })
        tasks.sorted().forEach { task ->
            addCard(card().apply { addView(CheckBox(this@MainActivity).apply {
                text = task; textSize = 16f; minHeight = dp(48); setTextColor(0xff284a43.toInt())
                setOnCheckedChangeListener { _, checked -> if (checked) { prefs.edit().putStringSet("tasks", tasks - task).apply(); showPlanner() } }
            }) })
        }
        section("Class notes")
        val journal = EditText(this).apply {
            setText(prefs.getString("journal", "")); hint = "What did you learn in class today?"; minLines = 5; gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(0xfff7fcf9.toInt(), 14).apply { setStroke(dp(1), 0xffd8eee2.toInt()) }
        }
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "What I learned today"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff284a43.toInt()) })
            addView(journal, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(Button(this@MainActivity).apply { text = "Save note"; stylePrimary(this); setOnClickListener { prefs.edit().putString("journal", journal.text.toString()).apply(); Toast.makeText(this@MainActivity, "Note saved", Toast.LENGTH_SHORT).show() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        })
    }
    private fun addTask() { val input = EditText(this).apply { hint = "e.g. Revise electrostatics" }; AlertDialog.Builder(this).setTitle("Study task").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> val old = prefs.getStringSet("tasks", emptySet()) ?: emptySet(); prefs.edit().putStringSet("tasks", old + input.text.toString()).apply(); showPlanner() }.show() }

    private fun showFocus() {
        if (running) pauseTimer()
        selectTab("Focus")
        val focus = prefs.getInt("pomodoro_focus", 25)
        val breakTime = prefs.getInt("pomodoro_break", 5)
        window.statusBarColor = 0xff3ea78b.toInt()
        window.navigationBarColor = 0xff147d70.toInt()
        val focusBackdrop = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff58b69b.toInt(), 0xff198d78.toInt(), 0xff11766b.toInt()))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(15), dp(22), dp(25))
            this.background = focusBackdrop
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply {
            text = "‹"; textSize = 30f; isAllCaps = false; contentDescription = "Back to study dashboard"
            setTextColor(0xffffffff.toInt()); background = rounded(0x44ffffff, 100)
            setOnClickListener { pauseTimer(); showShell(); showHome() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            text = "Focus"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xffffffff.toInt())
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(Button(this).apply {
            text = "⚙"; textSize = 23f; isAllCaps = false; contentDescription = "Timer settings"
            setTextColor(0xffffffff.toInt()); background = rounded(0x44ffffff, 100)
            setOnClickListener { showFocusSettingsDialog() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header)
        content.addView(TextView(this).apply {
            text = if (isBreak) "Take a breath" else "Study"; textSize = 31f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xffffffff.toInt()); setPadding(dp(3), dp(28), 0, dp(4))
        })
        val intention = EditText(this).apply {
            hint = "I'll spend my time on…"; setSingleLine(true); textSize = 17f
            setTextColor(0xffffffff.toInt()); setHintTextColor(0xffd7f1e8.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xffabdfcf.toInt())
            setText(prefs.getString("focus_intention", ""))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { prefs.edit().putString("focus_intention", s.toString()).apply() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        content.addView(intention, LinearLayout.LayoutParams(-1, dp(53)))
        val orb = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xffe9ffd7.toInt(), 0xffa1edd5.toInt(), 0xffb6f3dc.toInt())).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(8), 0x66ffffff)
            }
            elevation = dp(10).toFloat()
        }
        val ring = FocusProgressView(this).apply { progress = if (isBreak) 1f else 1f - focusSeconds.toFloat() / phaseDurationSeconds.coerceAtLeast(1) }
        orb.addView(ring, FrameLayout.LayoutParams(-1, -1))
        val hero = FrameLayout(this).apply { addView(orb, FrameLayout.LayoutParams(dp(305), dp(305), Gravity.CENTER)) }
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(342)).apply { topMargin = dp(30); bottomMargin = dp(18) })
        val phase = TextView(this).apply {
            text = if (isBreak) "BREAK TIME" else "FOCUS TIME"; textSize = 13f; letterSpacing = 0.2f; gravity = Gravity.CENTER
            setTextColor(0xffd8f5e8.toInt())
        }
        content.addView(phase)
        val time = TextView(this).apply {
            text = formatTime(focusSeconds); textSize = 63f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER; setTextColor(0xffffffff.toInt())
        }
        content.addView(time, LinearLayout.LayoutParams(-1, dp(100)))
        val sessionCount = TextView(this).apply {
            text = "${prefs.getInt("focus_sessions", 0)} focus sessions · $focus min focus / $breakTime min break"
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xffdef8e9.toInt())
        }
        content.addView(sessionCount)
        val start = Button(this).apply {
            text = if (running) "Pause" else if (isBreak) "Start break" else "Start Focus"
            isAllCaps = false; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xff176c61.toInt()); background = rounded(0xffffffff.toInt(), 100)
            elevation = dp(4).toFloat()
            setOnClickListener {
                if (running) { pauseTimer(); text = "Resume Focus" }
                else { text = "Pause"; startTimer(time, phase, ring, sessionCount) }
            }
        }
        content.addView(start, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(31) })
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        footer.addView(Button(this).apply {
            text = "Reset"; isAllCaps = false; textSize = 14f
            setTextColor(0xffffffff.toInt()); background = rounded(0x33ffffff, 100)
            setOnClickListener {
                pauseTimer(); isBreak = false; focusSeconds = focus * 60; phaseDurationSeconds = focusSeconds
                time.text = formatTime(focusSeconds); phase.text = "FOCUS TIME"; ring.progress = 0f; start.text = "Start Focus"
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(7) })
        footer.addView(Button(this).apply {
            text = "Set timer"; isAllCaps = false; textSize = 14f
            setTextColor(0xffffffff.toInt()); background = rounded(0x33ffffff, 100)
            setOnClickListener { showFocusSettingsDialog() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(7) })
        content.addView(footer, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(13) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
    }

    private fun showFocusSettingsDialog() {
        val focusValue = prefs.getInt("pomodoro_focus", 25)
        val breakValue = prefs.getInt("pomodoro_break", 5)
        val dialog = Dialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
            background = rounded(0xffeef9f4.toInt(), 30)
        }
        sheet.addView(TextView(this).apply {
            text = "Timer"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xff173b36.toInt())
        })
        val focusLabel = TextView(this).apply {
            text = "$focusValue minutes focus"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xff173b36.toInt()); setPadding(0, dp(30), 0, dp(8))
        }
        sheet.addView(focusLabel)
        val focusSeek = SeekBar(this).apply {
            max = 35; progress = ((focusValue.coerceIn(5, 180) - 5) / 5)
            progressTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { focusLabel.text = "${5 + value * 5} minutes focus" }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        sheet.addView(focusSeek)
        val breakLabel = TextView(this).apply {
            text = "$breakValue minutes break"; textSize = 16f; setTextColor(0xff36564f.toInt()); setPadding(0, dp(22), 0, dp(7))
        }
        sheet.addView(breakLabel)
        val breakSeek = SeekBar(this).apply {
            max = 29; progress = breakValue.coerceIn(1, 30) - 1
            progressTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xff137f70.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { breakLabel.text = "${value + 1} minutes break" }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        sheet.addView(breakSeek)
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Triple("25 / 5", 25, 5), Triple("50 / 10", 50, 10)).forEach { (label, focus, rest) ->
            presets.addView(Button(this).apply {
                text = label; styleAction(this)
                setOnClickListener { focusSeek.progress = (focus - 5) / 5; breakSeek.progress = rest - 1 }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        }
        sheet.addView(presets, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        sheet.addView(TextView(this).apply {
            text = "App limits are separate from this timer; use More → App daily limits to configure them."
            textSize = 13f; setTextColor(0xff607b72.toInt()); setPadding(0, dp(19), 0, dp(8))
        })
        sheet.addView(Button(this).apply {
            text = "Save timer"; stylePrimary(this)
            setOnClickListener {
                dialog.dismiss()
                setTimerPreset(5 + focusSeek.progress * 5, breakSeek.progress + 1)
            }
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(15) })
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -2)
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -2)
    }

    private fun setTimerPreset(focus: Int, breakTime: Int) {
        pauseTimer(); isBreak = false; focusSeconds = focus * 60; phaseDurationSeconds = focusSeconds
        prefs.edit().putInt("pomodoro_focus", focus).putInt("pomodoro_break", breakTime).apply()
        showFocus()
    }

    private fun showCustomTimerDialog() {
        val focusInput = EditText(this).apply { hint = "Focus minutes"; inputType = 2; setText(prefs.getInt("pomodoro_focus", 25).toString()) }
        val breakInput = EditText(this).apply { hint = "Break minutes"; inputType = 2; setText(prefs.getInt("pomodoro_break", 5).toString()) }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(focusInput); addView(breakInput) }
        AlertDialog.Builder(this).setTitle("Custom Pomodoro").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
            val focus = focusInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 25
            val breakTime = breakInput.text.toString().toIntOrNull()?.coerceIn(1, 60) ?: 5
            setTimerPreset(focus, breakTime)
        }.show()
    }

    private fun pauseTimer() {
        if (running) focusSeconds = ((phaseEndElapsedMs - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
        running = false
        timerRunnable?.let(handler::removeCallbacks)
        timerRunnable = null
    }

    private fun startTimer(time: TextView, phase: TextView, ring: FocusProgressView, sessionCount: TextView) {
        timerRunnable?.let(handler::removeCallbacks)
        running = true
        phaseEndElapsedMs = SystemClock.elapsedRealtime() + focusSeconds * 1000L
        val callback = object : Runnable {
            override fun run() {
                if (!running) return
                focusSeconds = ((phaseEndElapsedMs - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                if (focusSeconds == 0) {
                    if (!isBreak) {
                        val completed = prefs.getInt("focus_sessions", 0) + 1
                        prefs.edit().putInt("focus_sessions", completed).apply()
                        sessionCount.text = "$completed focus sessions · ${prefs.getInt("pomodoro_focus", 25)} min focus / ${prefs.getInt("pomodoro_break", 5)} min break"
                    }
                    isBreak = !isBreak
                    focusSeconds = (if (isBreak) prefs.getInt("pomodoro_break", 5) else prefs.getInt("pomodoro_focus", 25)) * 60
                    phaseDurationSeconds = focusSeconds
                    phaseEndElapsedMs = SystemClock.elapsedRealtime() + focusSeconds * 1000L
                    phase.text = if (isBreak) "BREAK TIME" else "FOCUS TIME"
                    Toast.makeText(this@MainActivity, if (isBreak) "Focus complete. Break time!" else "Break complete. Focus time!", Toast.LENGTH_LONG).show()
                }
                time.text = formatTime(focusSeconds)
                ring.progress = if (isBreak) 1f else 1f - focusSeconds.toFloat() / phaseDurationSeconds.coerceAtLeast(1)
                handler.postDelayed(this, 250L)
            }
        }
        timerRunnable = callback
        handler.post(callback)
    }

    private inner class FocusProgressView(context: Context) : View(context) {
        var progress = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                contentDescription = "Focus progress ${(field * 100).toInt()} percent"
                invalidate()
            }
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = minOf(width, height) * 0.36f
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = radius * 0.13f
            brush.strokeCap = Paint.Cap.ROUND
            brush.color = 0xffdcefe4.toInt()
            canvas.drawCircle(width / 2f, height / 2f, radius, brush)
            brush.color = 0xff157c6d.toInt()
            val left = width / 2f - radius
            val top = height / 2f - radius
            canvas.drawArc(left, top, left + radius * 2f, top + radius * 2f,
                -90f, 360f * progress, false, brush)
        }
    }

    private fun showProfile() {
        clear("My profile", "Your study choices stay on this phone.")
        val name = EditText(this).apply {
            hint = "Your name"; setSingleLine(true); setText(prefs.getString("profile_name", ""))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(0xfff7fcf9.toInt(), 14).apply { setStroke(dp(1), 0xffd8eee2.toInt()) }
        }
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "Name"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff1c3d38.toInt()) })
            addView(name, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
            addView(Button(this@MainActivity).apply {
                text = "Save profile"; stylePrimary(this)
                setOnClickListener {
                    prefs.edit().putString("profile_name", name.text.toString().trim()).apply()
                    Toast.makeText(this@MainActivity, "Profile saved", Toast.LENGTH_SHORT).show(); showHome()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        })
        addCard(card().apply {
            addView(TextView(this@MainActivity).apply { text = "Class $grade · $exam"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff1c3d38.toInt()) })
            addView(TextView(this@MainActivity).apply { text = "Your $exam course was chosen during setup. You can still move between Class 11 and 12 at the top."; textSize = 14f; setTextColor(0xff678178.toInt()); setPadding(0, dp(8), 0, 0) })
            addView(TextView(this@MainActivity).apply { text = "${prefs.getInt("focus_sessions", 0)} focus sessions"; textSize = 16f; setTextColor(0xff25734d.toInt()); setPadding(0, dp(12), 0, 0) })
        })
        addCard(Button(this).apply { text = "Change exam date · ${daysLeft()} days left"; setOnClickListener { pickExamDate() } })
    }

    private fun formatTime(s: Int) = "%02d:%02d".format(s / 60, s % 60)
    private fun showStudyApps() {
        clear("Study apps", "Search trusted study tools online on Google Play.")
        val query = EditText(this).apply {
            hint = "Search study apps, e.g. flashcards or chemistry"; setSingleLine(true)
            background = rounded(0xffffffff.toInt(), 14).apply { setStroke(dp(1), 0xffd8dee9.toInt()) }
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        val search = Button(this).apply {
            text = "Search Google Play"; stylePrimary(this)
            setOnClickListener { openStudyAppSearch(query.text.toString()) }
        }
        addCard(card().apply { addView(query); addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }) })
        section("Quick searches")
        listOf("MHT-CET preparation", "HSC Board study", "Flashcards and revision", "Pomodoro focus timer", "Physics MCQ practice", "Mathematics practice").forEach { searchText ->
            addCard(Button(this).apply { text = searchText; setOnClickListener { openStudyAppSearch(searchText) } })
        }
        addCard(card().apply { addView(TextView(this@MainActivity).apply { text = "Search opens Google Play or your browser. Install only apps you trust and review their privacy details first."; setTextColor(0xff678178.toInt()) }) })
    }

    private fun openStudyAppSearch(text: String) {
        val query = text.trim().ifBlank { "MHT CET study apps" }
        val encoded = Uri.encode(query)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")))
        }
    }
    private fun showMore() {
        clear("More tools", "Offline-first study data stays on this phone.")
        addCard(Button(this).apply { text = "My profile"; setOnClickListener { showProfile() } })
        addCard(Button(this).apply { text = "Find study apps online"; setOnClickListener { showStudyApps() } })
        addCard(Button(this).apply { text = "HSC board checklist"; setOnClickListener { grade = "12"; showSyllabus() } })
        addCard(Button(this).apply { text = "Arihant practice log"; setOnClickListener { showArihant() } })
        addCard(Button(this).apply { text = "App daily limits"; setOnClickListener { showLimits() } })
    }

    private fun showArihant() {
        clear("Arihant practice log", "Log your own book practice only. No copyrighted questions are included.")
        val chapter = EditText(this).apply { hint = "Chapter" }
        val attempted = EditText(this).apply { hint = "Attempted"; inputType = 2 }
        val correct = EditText(this).apply { hint = "Correct"; inputType = 2 }
        addCard(chapter); addCard(attempted); addCard(correct)
        addCard(Button(this).apply {
            text = "Save practice log"
            setOnClickListener {
                val count = attempted.text.toString().toIntOrNull()
                val right = correct.text.toString().toIntOrNull()
                if (chapter.text.isBlank() || count == null || right == null || count <= 0 || right !in 0..count) {
                    Toast.makeText(this@MainActivity, "Enter a chapter and valid score", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val item = "${chapter.text}: $right/$count"
                val old = prefs.getStringSet("arihant", emptySet()) ?: emptySet()
                prefs.edit().putStringSet("arihant", old + item).apply()
                StudyData.events(this@MainActivity).recordPractice(count, right)
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                showArihant()
            }
        })
        (prefs.getStringSet("arihant", emptySet()) ?: emptySet()).forEach {
            addCard(TextView(this).apply { text = it; setPadding(16, 16, 16, 16) })
        }
    }

    private fun showLimits() {
        clear("App daily limits", "Choose limits for any installed launchable app. Search keeps the full list easy to use.")
        addCard(Button(this).apply { text = "1. Open Usage Access settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } })
        addCard(Button(this).apply { text = "2. Open Accessibility settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        section("Choose daily limits")
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val protected = Protection.packages(this)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in protected }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val search = EditText(this).apply { hint = "Search every installed app"; setSingleLine(true); background = rounded(0xffffffff.toInt(), 18); setPadding(dp(18), dp(13), dp(18), dp(13)) }
        addCard(search)
        val count = TextView(this).apply { textSize = 14f; setTextColor(0xff678178.toInt()); setPadding(dp(4), dp(4), 0, dp(4)) }
        body.addView(count)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val matches = apps.filter { info ->
                val name = info.loadLabel(packageManager).toString()
                query.isBlank() || name.contains(query, true) || info.activityInfo.packageName.contains(query, true)
            }
            count.text = "${matches.size} apps available"
            matches.forEach { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(packageManager).toString()
                val spinner = Spinner(this)
                val values = arrayOf("Off", "15 min", "30 min", "45 min", "60 min", "90 min", "120 min")
                val minutes = arrayOf(0, 15, 30, 45, 60, 90, 120)
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
                val stored = getSharedPreferences(StudyBlockerService.PREFS, MODE_PRIVATE).getInt("limit_$pkg", 0)
                spinner.setSelection(minutes.indexOf(stored).coerceAtLeast(0))
                spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { getSharedPreferences(StudyBlockerService.PREFS, MODE_PRIVATE).edit().putInt("limit_$pkg", minutes[position]).apply() }
                }
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@MainActivity).apply { text = label; textSize = 16f; setTextColor(0xff202942.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(spinner)
                }
                list.addView(card().apply { addView(row); addView(TextView(this@MainActivity).apply { text = pkg; textSize = 11f; setTextColor(0xff7c8498.toInt()); setPadding(0, dp(5), 0, 0) }) }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
    }
    private data class McqForm(val question: EditText, val options: List<EditText>, val answer: Spinner)
    data class Mcq(val subject: String, val text: String, val options: Array<String>, val answer: Int, val explanation: String)
}




















