package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType

data class ExecutorValidationResult(
    val executorType: ExecutorType,
    val isReady: Boolean,
    val summary: String,
    val detail: String? = null,
)
