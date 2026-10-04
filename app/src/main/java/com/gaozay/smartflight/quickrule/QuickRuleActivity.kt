package com.gaozay.smartflight.quickrule

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaozay.smartflight.R
import com.gaozay.smartflight.ui.QuickRuleDialog
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickRuleActivity : ComponentActivity() {
    private val viewModel: QuickRuleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        // Do not expose a rule editor over a secure lock screen through a third-party intent.
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { finish(); return }
        viewModel.initialize(intent.toQuickRuleRequest())
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.saved) {
                if (state.saved) {
                    setResult(RESULT_OK)
                    Toast.makeText(this@QuickRuleActivity, R.string.quick_rule_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            SmartFlightTheme(state.settings) {
                QuickRuleDialog(state, viewModel::chooseScope, viewModel::chooseMode, viewModel::save) { finish() }
            }
        }
    }

    override fun onStart() { super.onStart(); QuickRuleOverlayState.entered() }
    override fun onStop() { QuickRuleOverlayState.left(); super.onStop() }

    companion object {
        fun intent(context: Context, target: com.gaozay.smartflight.runtime.ForegroundAppInfo? = null): Intent =
            Intent(context, QuickRuleActivity::class.java).apply {
                action = QuickRuleContract.ACTION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                target?.let {
                    putExtra(QuickRuleContract.EXTRA_PACKAGE_NAME, it.packageName)
                    it.activityName?.let { name -> putExtra(QuickRuleContract.EXTRA_ACTIVITY_NAME, name) }
                }
            }
    }
}

internal fun Intent.toQuickRuleRequest(): QuickRuleRequest = try {
    val pkg = getStringExtra(QuickRuleContract.EXTRA_PACKAGE_NAME)
    val activity = getStringExtra(QuickRuleContract.EXTRA_ACTIVITY_NAME)
    val invalid = (hasExtra(QuickRuleContract.EXTRA_PACKAGE_NAME) && (pkg.isNullOrBlank() || pkg.length > 255 || pkg.any { it.isWhitespace() || it.isISOControl() })) ||
        (hasExtra(QuickRuleContract.EXTRA_ACTIVITY_NAME) && (pkg == null || activity.isNullOrBlank() || activity.length > 1000))
    QuickRuleRequest(pkg, activity, invalid)
} catch (_: RuntimeException) { QuickRuleRequest(invalid = true) }
