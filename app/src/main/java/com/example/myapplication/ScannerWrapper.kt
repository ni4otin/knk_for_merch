package com.knk.scaner

interface ScannerWrapper {
    fun startScan(onResult: (String?) -> Unit)
}
