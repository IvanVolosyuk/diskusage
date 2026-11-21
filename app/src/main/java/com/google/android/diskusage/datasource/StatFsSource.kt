package com.google.android.diskusage.datasource

interface StatFsSource {
    @Deprecated("Use availableBlocksLong instead", ReplaceWith("availableBlocksLong"))
    val availableBlocks: Int
    val availableBlocksLong: Long
    val availableBytes: Long

    @Deprecated("Use blockCountLong instead", ReplaceWith("blockCountLong"))
    val blockCount: Int
    val blockCountLong: Long

    @Deprecated("Use blockSizeLing instead", ReplaceWith("blockSizeLong"))
    val blockSize: Int
    val blockSizeLong: Long
    val freeBytes: Long

    @Deprecated("Use freeBlocksLing instead", ReplaceWith("freeBlocksLong"))
    val freeBlocks: Int
    val freeBlocksLong: Long
    val totalBytes: Long
}