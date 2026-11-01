package com.abominableenterprise.cherkit.update.compose

/** An [UpdateFlow] whose answers the test sets. */
class FakeUpdateFlow(
    var status: UpdateStatus = available(42),
    var requestError: Exception? = null,
    var startResult: Boolean = true,
    var startError: Exception? = null,
) : UpdateFlow<UpdateStatus> {
    var requests = 0
    var starts = 0

    override suspend fun request(): UpdateStatus {
        requests++
        requestError?.let { throw it }
        return status
    }

    override fun status(info: UpdateStatus): UpdateStatus = info

    override fun startFlexible(info: UpdateStatus): Boolean {
        starts++
        startError?.let { throw it }
        return startResult
    }

    companion object {
        fun available(code: Int, flexible: Boolean = true) = UpdateStatus(true, flexible, code, downloaded = false)
        val none = UpdateStatus(false, false, 0, downloaded = false)
        fun downloaded(code: Int) = UpdateStatus(false, false, code, downloaded = true)
    }
}
