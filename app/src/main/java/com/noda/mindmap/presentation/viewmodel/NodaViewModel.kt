package com.noda.mindmap.presentation.viewmodel

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import com.noda.mindmap.domain.repository.NodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NodaUiState(
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val selectedNode: Node? = null,
    val isMeditationMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Node> = emptyList(),
    val isSearchActive: Boolean = false
)

@HiltViewModel
class NodaViewModel @Inject constructor(
    private val repository: NodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NodaUiState())
    val uiState: StateFlow<NodaUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    init {
        // Load all nodes + connections
        viewModelScope.launch {
            combine(
                repository.getNodes(),
                repository.getConnections()
            ) { nodes, connections ->
                _uiState.value.copy(nodes = nodes, connections = connections)
            }.collect { _uiState.value = it }
        }

        // Search with debounce
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList())
                    else repository.searchNodes(query)
                }
                .collect { results ->
                    _uiState.value = _uiState.value.copy(searchResults = results)
                }
        }
    }

    fun saveNode(node: Node) = viewModelScope.launch { repository.saveNode(node) }

    fun updateNodeText(nodeId: String, text: String) = viewModelScope.launch {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@launch
        repository.updateNode(node.copy(text = text))
    }

    fun updateNodeColor(nodeId: String, color: Int) = viewModelScope.launch {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@launch
        repository.updateNode(node.copy(color = color))
    }

    fun updateNodeTextColor(nodeId: String, textColor: Int) = viewModelScope.launch {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@launch
        repository.updateNode(node.copy(textColor = textColor))
    }

    fun updateNodeRadius(nodeId: String, radius: Float) = viewModelScope.launch {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@launch
        repository.updateNode(node.copy(radius = radius.coerceIn(30f, 150f)))
    }

    fun deleteNode(nodeId: String) = viewModelScope.launch { repository.deleteNode(nodeId) }

    fun addConnection(connection: Connection) = viewModelScope.launch {
        repository.addConnection(connection)
    }

    fun toggleFreeze(nodeId: String) = viewModelScope.launch {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@launch
        repository.updateNode(node.copy(isFrozen = !node.isFrozen))
    }

    fun mergeNodes(a: Node, b: Node) = viewModelScope.launch {
        val merged = Node(
            id = UUID.randomUUID().toString(),
            text = buildString {
                if (a.text.isNotEmpty()) append(a.text)
                if (a.text.isNotEmpty() && b.text.isNotEmpty()) append(" + ")
                if (b.text.isNotEmpty()) append(b.text)
            }.take(50),
            x = (a.x + b.x) / 2f,
            y = (a.y + b.y) / 2f,
            color = blendColors(a.color, b.color),
            radius = ((a.radius + b.radius) / 2f).coerceIn(30f, 150f)
        )
        repository.saveNode(merged)
        repository.deleteNode(a.id)
        repository.deleteNode(b.id)
    }

    fun selectNode(node: Node?) {
        _uiState.value = _uiState.value.copy(selectedNode = node)
    }

    fun toggleMeditationMode() {
        _uiState.value = _uiState.value.copy(isMeditationMode = !_uiState.value.isMeditationMode)
    }

    // Search
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else _uiState.value.searchQuery,
            searchResults = if (!active) emptyList() else _uiState.value.searchResults
        )
        if (!active) _searchQuery.value = ""
    }

    private fun blendColors(c1: Int, c2: Int) = Color.rgb(
        (Color.red(c1) + Color.red(c2)) / 2,
        (Color.green(c1) + Color.green(c2)) / 2,
        (Color.blue(c1) + Color.blue(c2)) / 2
    )
}
