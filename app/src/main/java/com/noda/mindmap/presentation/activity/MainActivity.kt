package com.noda.mindmap.presentation.activity

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
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

    private val nodeColors = listOf(
        "#6C63FF", "#FF6584", "#43D9AD", "#F9CA24",
        "#FF9FF3", "#54A0FF", "#FF6B35", "#26de81"
    )
    private val textColors = listOf(
        "#FFFFFF", "#000000", "#FFD700", "#FF6584",
        "#43D9AD", "#6C63FF", "#F9CA24", "#54A0FF"
    )

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomPanel) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.navBarSpacer.layoutParams.height = nav.bottom + 8
            insets
        }

        setupCanvas()
        setupUI()
        observeViewModel()
    }

    private fun setupCanvas() {
        binding.nodaCanvas.onNodeCreated = { node ->
            viewModel.saveNode(node)
            showBottomPanel(node, focusInput = true)
        }
        binding.nodaCanvas.onNodeSelected = { node ->
            viewModel.selectNode(node)
            if (node != null) showBottomPanel(node, focusInput = false) else hideBottomPanel()
        }
        binding.nodaCanvas.onConnectionCreated = { viewModel.addConnection(it) }
        binding.nodaCanvas.onNodesMergeRequested = { a, b -> showMergeDialog(a, b) }
        binding.nodaCanvas.onNodeLongPressed = { node ->
            viewModel.selectNode(node)
            showBottomPanel(node, focusInput = true)
        }
    }

    private fun setupUI() {
        // Meditation toggle
        binding.btnMeditation.setOnClickListener { viewModel.toggleMeditationMode() }

        // Search toggle
        binding.btnSearch.setOnClickListener {
            val active = !viewModel.uiState.value.isSearchActive
            viewModel.setSearchActive(active)
            if (active) {
                binding.searchPanel.visibility = View.VISIBLE
                binding.searchInput.requestFocus()
                showKeyboard(binding.searchInput)
            } else {
                binding.searchPanel.visibility = View.GONE
                binding.nodaCanvas.setHighlightedNodes(emptySet())
                hideKeyboard()
            }
        }

        // Search input
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                viewModel.setSearchQuery(q)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Save text
        binding.btnSaveText.setOnClickListener { saveCurrentNodeText() }
        binding.editNodeText.setOnEditorActionListener { _, _, _ -> saveCurrentNodeText(); true }

        // Node color
        binding.btnNodeColor.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showColorPickerDialog("Цвет ноды", nodeColors) { hex ->
                val c = Color.parseColor(hex)
                viewModel.updateNodeColor(node.id, c)
                binding.nodaCanvas.updateNodeColor(node.id, c)
                updateColorPreview(binding.btnNodeColor, c)
            }
        }

        // Text color
        binding.btnTextColor.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showColorPickerDialog("Цвет текста", textColors) { hex ->
                val c = Color.parseColor(hex)
                viewModel.updateNodeTextColor(node.id, c)
                binding.nodaCanvas.updateNodeTextColor(node.id, c)
                updateColorPreview(binding.btnTextColor, c)
            }
        }

        // Size slider
        binding.sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val node = viewModel.uiState.value.selectedNode ?: return
                val radius = 30f + progress * 1.2f  // 30..150
                viewModel.updateNodeRadius(node.id, radius)
                binding.nodaCanvas.updateNodeRadius(node.id, radius)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Delete
        binding.btnDeleteNode.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            viewModel.deleteNode(node.id)
            binding.nodaCanvas.removeNode(node.id)
            hideBottomPanel()
        }

        // More options (freeze/context)
        binding.btnMoreOptions.setOnClickListener {
            val node = viewModel.uiState.value.selectedNode ?: return@setOnClickListener
            showNodeContextMenu(node)
        }
    }

    private fun saveCurrentNodeText() {
        val node = viewModel.uiState.value.selectedNode ?: return
        val text = binding.editNodeText.text.toString().trim()
        viewModel.updateNodeText(node.id, text)
        binding.nodaCanvas.updateNodeText(node.id, text)
        hideKeyboard()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.nodaCanvas.setNodes(state.nodes)
                binding.nodaCanvas.setConnections(state.connections)

                // Search results — highlight nodes
                if (state.searchResults.isNotEmpty()) {
                    val ids = state.searchResults.map { it.id }.toSet()
                    binding.nodaCanvas.setHighlightedNodes(ids)
                    updateSearchResults(state.searchResults)
                } else {
                    binding.nodaCanvas.setHighlightedNodes(emptySet())
                    binding.searchResultsList.visibility = View.GONE
                }

                // Meditation
                if (state.isMeditationMode) {
                    hideBottomPanel()
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

    private fun updateSearchResults(results: List<Node>) {
        binding.searchResultsList.visibility = View.VISIBLE
        binding.searchResultsList.removeAllViews()
        results.take(5).forEach { node ->
            val item = TextView(this).apply {
                text = if (node.text.isNotEmpty()) node.text else "(без названия)"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, Color.red(node.color), Color.green(node.color), Color.blue(node.color)))
                    cornerRadius = 12f
                    setStroke(1, Color.argb(80, Color.red(node.color), Color.green(node.color), Color.blue(node.color)))
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
                layoutParams = params
                setOnClickListener {
                    binding.nodaCanvas.focusOnNode(node.id)
                    viewModel.selectNode(node)
                    showBottomPanel(node, focusInput = false)
                }
            }
            binding.searchResultsList.addView(item)
        }
    }

    private fun showBottomPanel(node: Node, focusInput: Boolean) {
        binding.bottomPanel.visibility = View.VISIBLE
        binding.editNodeText.setText(node.text)
        binding.editNodeText.setSelection(node.text.length)

        // Update color previews
        updateColorPreview(binding.btnNodeColor, node.color)
        updateColorPreview(binding.btnTextColor, node.textColor)

        // Update size slider
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

    private fun updateColorPreview(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.argb(100, 255, 255, 255))
        }
    }

    private fun showColorPickerDialog(title: String, colors: List<String>, onPick: (String) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
        }
        var dialog: AlertDialog? = null
        colors.forEach { hex ->
            val btn = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64).apply { marginEnd = 12 }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    setStroke(2, Color.argb(80, 255, 255, 255))
                }
                setOnClickListener { onPick(hex); dialog?.dismiss() }
            }
            layout.addView(btn)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .create()
        dialog.show()
    }

    private fun showNodeContextMenu(node: Node) {
        val popup = PopupMenu(this, binding.btnMoreOptions)
        popup.menu.add(if (node.isFrozen) "🔥 Разморозить" else "❄ Заморозить")
        popup.setOnMenuItemClickListener { item ->
            when {
                item.title.toString().contains("заморозить", ignoreCase = true) ||
                item.title.toString().contains("Разморозить", ignoreCase = true) -> {
                    viewModel.toggleFreeze(node.id)
                    binding.nodaCanvas.toggleFreezeNode(node.id)
                }
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
    }
}
