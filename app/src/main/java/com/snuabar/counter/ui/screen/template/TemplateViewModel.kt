package com.snuabar.counter.ui.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.domain.repository.TemplateRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val _showAddTemplateDialog = MutableStateFlow(false)
    val showAddTemplateDialog: StateFlow<Boolean> = _showAddTemplateDialog.asStateFlow()

    private val _newTemplateName = MutableStateFlow("")
    val newTemplateName: StateFlow<String> = _newTemplateName.asStateFlow()

    private val _selectedSensorType = MutableStateFlow(SensorType.VISION)
    val selectedSensorType: StateFlow<SensorType> = _selectedSensorType.asStateFlow()

    init {
        viewModelScope.launch {
            templateRepository.ensureBuiltinTemplates()
            templateRepository.getAllTemplates().collect { list ->
                _templates.value = list
            }
        }
    }

    fun createTemplate(name: String, sensorType: SensorType) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first()
            val template = Template(
                userId = userId,
                name = name,
                type = TemplateType.CUSTOM,
                sensorType = sensorType
            )
            templateRepository.createTemplate(template)
            _showAddTemplateDialog.value = false
            _newTemplateName.value = ""
        }
    }

    fun deleteTemplate(templateId: Long) {
        // TODO: implement delete
    }

    fun setShowAddTemplateDialog(show: Boolean) {
        _showAddTemplateDialog.value = show
    }

    fun setNewTemplateName(name: String) {
        _newTemplateName.value = name
    }

    fun setSelectedSensorType(sensorType: SensorType) {
        _selectedSensorType.value = sensorType
    }
}
