package com.chen.memorizewords.domain.model.study.record

data class DailyStudyRecords(
    val date: String,  // 日期，格式如 "2026-01-07"
    val wordId: Long,  // 单词
    val word: String,  // 单词
    val definition: String,  // 单词释义
    val isNewWord: Boolean,  // 类别：是否新学，不是就是复习
)