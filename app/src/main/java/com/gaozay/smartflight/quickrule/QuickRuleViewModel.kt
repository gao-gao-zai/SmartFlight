package com.gaozay.smartflight.quickrule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.R
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickRuleState(
    val loading: Boolean = true,
    val target: QuickRuleTarget? = null,
    val scope: QuickRuleScope = QuickRuleScope.App,
    val mode: QuickRuleMode = QuickRuleMode.Auto,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorRes: Int? = null,
    val settings: UserSettings = UserSettings(),
) {
    val automationPaused: Boolean get() = !settings.isAutomationEffectivelyEnabled()
}

@HiltViewModel
class QuickRuleViewModel @Inject constructor(
    private val repository: QuickRuleRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val current = MutableStateFlow(QuickRuleState())
    val state = current.asStateFlow()
    private var initialized = false
    init { viewModelScope.launch { settings.settings.collect { value -> current.update { it.copy(settings = value) } } } }

    fun initialize(request: QuickRuleRequest) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            try {
                val target = repository.load(request)
                current.update { it.copy(loading = false, target = target, mode = target.appMode) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { current.update { it.copy(loading = false, errorRes = error.errorResource()) } }
        }
    }
    fun chooseScope(scope: QuickRuleScope) {
        val value = current.value
        if (value.saving || value.saved) return
        val target = value.target ?: return
        if (scope == QuickRuleScope.Activity && target.activityName == null) return
        current.update { it.copy(scope = scope, mode = if (scope == QuickRuleScope.App) target.appMode else target.activityMode, errorRes = null) }
    }
    fun chooseMode(mode: QuickRuleMode) { if (!current.value.saving && !current.value.saved) current.update { it.copy(mode = mode, errorRes = null) } }
    fun save() {
        val value = current.value
        val target = value.target ?: return
        if (value.saving || value.saved) return
        // Set the guard before scheduling, including rapid repeated taps.
        current.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                repository.save(target, value.scope, value.mode)
                current.update { it.copy(saving = false, saved = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { current.update { it.copy(saving = false, errorRes = error.errorResource()) } }
        }
    }
    private fun Exception.errorResource(): Int = (this as? QuickRuleException)?.messageRes ?: R.string.quick_rule_failed
}
