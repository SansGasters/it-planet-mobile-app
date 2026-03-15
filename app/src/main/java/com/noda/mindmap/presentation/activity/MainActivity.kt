package com.noda.mindmap.presentation.activity

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.noda.mindmap.databinding.ActivityMainBinding
import com.noda.mindmap.domain.model.Node
import com.noda.mindmap.presentation.viewmodel.NodaViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NodaViewModel by viewModels()

    private var openNotesNodeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.navBarSpacer.layoutParams.height = nav.bottom + 8
            binding.notesNavBarSpacer.layoutParams.height = nav.bottom + 8
            insets
        }

        setupBackHandler()
        setupCanvas()
        setupUI()
        observeViewModel()
    }

    // ─── Кнопка назад ─────────────────────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.notesPanel.visibility == View.VISIBLE -> closeNotes(save = true)
                    binding.searchPanel.visibility == View.VISIBLE -> closeSearch()
                    binding.bottomPanel.visibility == View.VISIBLE -> hideBottomPanel()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    // ─── Canvas ──────────────────────────────────────────────────────────────

    private fun setupCanvas() {
        binding.nodaCanvas.onNodeCreated = { node ->
            viewModel.saveNode(node)
            if (binding.notesPanel.visibility != View.VISIBLE)
                showBottomPanel(node, focusInput = true)
        }
        binding.nodaCanvas.onNodeSelected = { node ->
            viewModel.selectNode(node)
            // Don't disrupt notes panel — user may be editing
            if (binding.notesPanel.visibility != View.VISIBLE) {
                if (node != null) showBottomPanel(node, focusInput = false) else hideBottomPanel()
            }
        }
        binding.nodaCanvas.onNodeDoubleTapped = { node ->
            viewModel.selectNode(node)
            binding.root.postDelayed({ openNotes(node) }, 50)
        }
        binding.nodaCanvas.onConnectionCreated = { viewModel.addConnection(it) }
        binding.nodaCanvas.onNodesMergeRequested = { a, b -> showMergeDialog(a, b) }
        binding.nodaCanvas.onNodeLongPressed = { node ->
            viewModel.selectNode(node)
            if (binding.notesPanel.visibility != View.VISIBLE)
                showBottomPanel(node, focusInput = true)
        }
    }

    // ─── UI Setup ────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.btnMeditation.setOnClickListener { viewModel.toggleMeditationMode() }

        binding.btnSearch.setOnClickListener {
            if (binding.searchPanel.visibility == View.VISIBLE) closeSearch()
            else {
                binding.searchPanel.visibility = View.VISIBLE
                binding.searchInput.requestFocus()
                showKeyboard(binding.searchInput)
                viewModel.setSearchActive(true)
            }
        }

        binding.btnCloseSearch.setOnClickListener { closeSearch() }

        binding.nodaCanvas.setOnClickListener {
            if (binding.searchPanel.visibility == View.VISIBLE) closeSearch()
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.setSearchQuery(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        binding.btnSaveText.setOnClickListener { saveCurrentNodeText() }
        binding.editNodeText.setOnEditorActionListener { _, _, _ -> saveCurrentNodeText(); true }

        binding.btnNodeColor.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showRgbColorPicker("Цвет ноды", node.color) { color ->
                viewModel.updateNodeColor(node.id, color)
                binding.nodaCanvas.updateNodeColor(node.id, color)
                updateColorPreview(binding.btnNodeColor, color)
            }
        }

        binding.btnTextColor.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showRgbColorPicker("Цвет текста", node.textColor) { color ->
                viewModel.updateNodeTextColor(node.id, color)
                binding.nodaCanvas.updateNodeTextColor(node.id, color)
                updateColorPreview(binding.btnTextColor, color)
            }
        }

        binding.sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val node = viewModel.uiState.value.selectedNode ?: return
                val radius = 30f + progress * 1.2f
                viewModel.updateNodeRadius(node.id, radius)
                binding.nodaCanvas.updateNodeRadius(node.id, radius)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.btnOpenNotes.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            openNotes(node)
        }

        binding.btnDeleteNode.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            viewModel.deleteNode(node.id)
            binding.nodaCanvas.removeNode(node.id)
            hideBottomPanel()
        }

        binding.btnMoreOptions.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showNodeContextMenu(node)
        }

        binding.btnSaveNotes.setOnClickListener { closeNotes(save = true) }
        binding.btnCloseNotes.setOnClickListener { closeNotes(save = false) }
    }

    // ─── Заметки ───────────────────────────────────────────────────────────────

    private fun openNotes(node: Node) {
        openNotesNodeId = node.id
        // Берём актуальную ноду из uiState, а не переданный объект
        val actualNode = viewModel.uiState.value.nodes.find { it.id == node.id } ?: node
        val title = if (actualNode.text.isNotEmpty()) actualNode.text else "Без названия"
        binding.notesTitleLabel.text = "📝 $title"
        binding.editNotes.setText(actualNode.notes)
        binding.editNotes.setSelection(actualNode.notes.length)
        binding.notesPanel.visibility = View.VISIBLE
        binding.editNotes.postDelayed({
            binding.editNotes.requestFocus()
            showKeyboard(binding.editNotes)
        }, 150)
    }

    private fun closeNotes(save: Boolean) {
        if (save) {
            val nodeId = openNotesNodeId  // use stored id, not selectedNode
            if (nodeId != null) {
                val notes = binding.editNotes.text.toString()
                viewModel.updateNodeNotes(nodeId, notes)
            }
        }
        openNotesNodeId = null
        binding.notesPanel.visibility = View.GONE
        hideKeyboard()
    }

    // ─── Поиск ──────────────────────────────────────────────────────────────

    private fun closeSearch() {
        binding.searchPanel.visibility = View.GONE
        binding.searchResultsList.visibility = View.GONE
        binding.nodaCanvas.setHighlightedNodes(emptySet())
        hideKeyboard()
        viewModel.setSearchActive(false)
    }

    // ─── Observers ───────────────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.nodaCanvas.setNodes(state.nodes)
                binding.nodaCanvas.setConnections(state.connections)

                if (state.searchResults.isNotEmpty()) {
                    binding.nodaCanvas.setHighlightedNodes(state.searchResults.map { it.id }.toSet())
                    updateSearchResults(state.searchResults)
                } else {
                    binding.nodaCanvas.setHighlightedNodes(emptySet())
                    binding.searchResultsList.visibility = View.GONE
                }

                if (state.isMeditationMode) {
                    // Don't close notes even in meditation mode
                    if (binding.notesPanel.visibility != View.VISIBLE) hideBottomPanel()
                    binding.btnMeditation.text = "✕"
                    binding.hintText.visibility = View.GONE
                    binding.btnSearch.visibility = View.GONE
                } else {
                    binding.btnMeditation.text = "✦"
                    binding.btnSearch.visibility = View.VISIBLE
                    binding.hintText.visibility = if (state.nodes.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ─── Выбор цвета ─────────────────────────────────────────────────────────

    private fun showRgbColorPicker(title: String, initialColor: Int, onPick: (Int) -> Unit) {
        val dp = resources.displayMetrics.density

        var r = Color.red(initialColor)
        var g = Color.green(initialColor)
        var b = Color.blue(initialColor)

        val hsvBuf = FloatArray(3)
        fun syncHsv() = Color.colorToHSV(Color.rgb(r, g, b), hsvBuf)
        syncHsv()

        fun currentColor() = Color.rgb(r, g, b)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
            setBackgroundColor(Color.parseColor("#111126"))
        }

        val previewBg = GradientDrawable().apply { cornerRadius = 10f * dp; setColor(initialColor) }
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (44*dp).toInt()
            ).apply { bottomMargin = (10*dp).toInt() }
            background = previewBg
        }
        root.addView(preview)
        fun refreshPreview() { previewBg.setColor(currentColor()) }

        val squareSize = (200 * dp).toInt()
        lateinit var hueSlider: SeekBar
        val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val squareView = object : View(this) {
            var cursorX = hsvBuf[1] * squareSize
            var cursorY = (1f - hsvBuf[2]) * squareSize

            override fun onDraw(c: Canvas) {
                val hueColor = Color.HSVToColor(floatArrayOf(hsvBuf[0], 1f, 1f))
                // White→hue left to right
                squarePaint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), squarePaint)
                // Transparent→black top to bottom
                squarePaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), squarePaint)
                // Cursor ring
                squarePaint.shader = null
                squarePaint.style = Paint.Style.STROKE
                squarePaint.strokeWidth = 2.5f * dp
                squarePaint.color = Color.WHITE
                c.drawCircle(cursorX, cursorY, 9f * dp, squarePaint)
            }

            fun pick(ex: Float, ey: Float) {
                cursorX = ex.coerceIn(0f, squareSize.toFloat())
                cursorY = ey.coerceIn(0f, squareSize.toFloat())
                val newSat = cursorX / squareSize
                val newBri = 1f - cursorY / squareSize
                val c = Color.HSVToColor(floatArrayOf(hsvBuf[0], newSat, newBri))
                r = Color.red(c); g = Color.green(c); b = Color.blue(c)
                syncHsv()
                refreshPreview()
                invalidate()
            }

            init {
                setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) pick(e.x, e.y)
                    true
                }
            }
        }
        squareView.layoutParams = LinearLayout.LayoutParams(squareSize, squareSize).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (10*dp).toInt()
        }
        root.addView(squareView)

        // Hue slider
        root.addView(TextView(this).apply { text = "Оттенок"; textSize = 11f; setTextColor(0xAAFFFFFF.toInt()) })
        hueSlider = SeekBar(this).apply {
            max = 360; progress = hsvBuf[0].toInt()
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8*dp).toInt() }
            post {
                val cols = IntArray(361) { Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)) }
                progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols)
                    .apply { cornerRadius = 6f * dp }
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    val c = Color.HSVToColor(floatArrayOf(p.toFloat(), hsvBuf[1], hsvBuf[2]))
                    r = Color.red(c); g = Color.green(c); b = Color.blue(c)
                    syncHsv()
                    squareView.invalidate()
                    refreshPreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(hueSlider)

        // RGB слайдер
        fun addRgbSlider(label: String, initVal: Int, tint: Int, getVal: () -> Int, setVal: (Int) -> Unit) {
            root.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(0xAAFFFFFF.toInt()) })
            root.addView(SeekBar(this).apply {
                max = 255; progress = initVal
                progressTintList = ColorStateList.valueOf(tint)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (4*dp).toInt() }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                        setVal(p)
                        syncHsv()
                        hueSlider.progress = hsvBuf[0].toInt()
                        squareView.cursorX = hsvBuf[1] * squareSize
                        squareView.cursorY = (1f - hsvBuf[2]) * squareSize
                        squareView.invalidate()
                        refreshPreview()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }

        addRgbSlider("R", r, Color.RED, { r }) { r = it }
        addRgbSlider("G", g, Color.GREEN, { g }) { g = it }
        addRgbSlider("B", b, Color.BLUE, { b }) { b = it }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("Выбрать") { _, _ -> onPick(currentColor()) }
            .setNegativeButton("Отмена", null)
            .create()
            .show()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun updateSearchResults(results: List<Node>) {
        binding.searchResultsList.visibility = View.VISIBLE
        binding.searchResultsList.removeAllViews()
        results.take(5).forEach { node ->
            val item = TextView(this).apply {
                text = if (node.text.isNotEmpty()) node.text else "(без названия)"
                textSize = 14f; setTextColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, Color.red(node.color), Color.green(node.color), Color.blue(node.color)))
                    cornerRadius = 12f
                    setStroke(1, Color.argb(80, Color.red(node.color), Color.green(node.color), Color.blue(node.color)))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
                setOnClickListener {
                    binding.nodaCanvas.focusOnNode(node.id)
                    viewModel.selectNode(node)
                    showBottomPanel(node, focusInput = false)
                    closeSearch()
                }
            }
            binding.searchResultsList.addView(item)
        }
    }

    private fun showBottomPanel(node: Node, focusInput: Boolean) {
        binding.bottomPanel.visibility = View.VISIBLE
        binding.editNodeText.setText(node.text)
        binding.editNodeText.setSelection(node.text.length)
        updateColorPreview(binding.btnNodeColor, node.color)
        updateColorPreview(binding.btnTextColor, node.textColor)
        val progress = ((node.radius - 30f) / 1.2f).toInt().coerceIn(0, 100)
        binding.sizeSlider.progress = progress
        if (focusInput) {
            binding.editNodeText.postDelayed({
                binding.editNodeText.requestFocus()
                showKeyboard(binding.editNodeText)
            }, 150)
        }
    }

    private fun hideBottomPanel() {
        binding.bottomPanel.visibility = View.GONE
        hideKeyboard()
        viewModel.selectNode(null)
    }

    private fun saveCurrentNodeText() {
        val node = viewModel.uiState.value.selectedNode ?: return
        val text = binding.editNodeText.text.toString().trim()
        viewModel.updateNodeText(node.id, text)
        binding.nodaCanvas.updateNodeText(node.id, text)
        if (binding.notesPanel.visibility == View.VISIBLE) {
            binding.notesTitleLabel.text = "📝 ${text.ifEmpty { "Без названия" }}"
        }
        hideKeyboard()
    }

    private fun updateColorPreview(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.argb(100, 255, 255, 255))
        }
    }

    private fun showNodeContextMenu(node: Node) {
        val popup = PopupMenu(this, binding.btnMoreOptions)
        popup.menu.add(if (node.isFrozen) "🔥 Разморозить" else "❄ Заморозить")
        popup.setOnMenuItemClickListener {
            if (it.title.toString().contains("морозить", ignoreCase = true)) {
                viewModel.toggleFreeze(node.id)
                binding.nodaCanvas.toggleFreezeNode(node.id)
            }
            true
        }
        popup.show()
    }

    private fun showMergeDialog(a: Node, b: Node) {
        AlertDialog.Builder(this)
            .setTitle("✨ Объединить мысли?")
            .setMessage("\"${a.text.ifEmpty{"..."}}\"  +  \"${b.text.ifEmpty{"..."}}\"")
            .setPositiveButton("Объединить") { _, _ ->
                viewModel.mergeNodes(a, b)
                binding.nodaCanvas.removeNode(a.id)
                binding.nodaCanvas.removeNode(b.id)
                binding.nodaCanvas.resetMergeDialog()
            }
            .setNegativeButton("Отмена") { _, _ -> binding.nodaCanvas.resetMergeDialog() }
            .setOnCancelListener { binding.nodaCanvas.resetMergeDialog() }
            .show()
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.editNodeText.clearFocus()
        binding.searchInput.clearFocus()
    }
}
