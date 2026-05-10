package com.gaozay.smartflight.runtime

import kotlinx.coroutines.flow.first

suspend fun RuntimeStatusRepository.snapshotState(): RuntimeSnapshot = snapshot.first()
