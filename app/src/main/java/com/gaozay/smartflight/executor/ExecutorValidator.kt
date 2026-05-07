package com.gaozay.smartflight.executor

interface ExecutorValidator {
    suspend fun validate(): ExecutorValidationResult
}
