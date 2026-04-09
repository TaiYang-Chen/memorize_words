package com.chen.memorizewords.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsEntity
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationEntity
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeDao
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeItemEntity
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeItemStateEntity
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeWordMetaEntity
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoriteEntity
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.local.room.model.wordbook.syncstate.WordBookSyncStateDao
import com.chen.memorizewords.data.local.room.model.wordbook.syncstate.WordBookSyncStateEntity
import com.chen.memorizewords.data.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.local.room.model.wordbook.words.WordBookItemEntity
import com.chen.memorizewords.data.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.local.room.model.words.word.WordEntity
import com.chen.memorizewords.data.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.local.room.model.words.definition.WordDefinitionEntity
import com.chen.memorizewords.data.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.data.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.local.room.model.words.relation.WordTagEntity
import com.chen.memorizewords.data.local.room.model.words.root.root.RootTagDao
import com.chen.memorizewords.data.local.room.model.words.root.root.RootTagEntity
import com.chen.memorizewords.data.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.local.room.model.words.root.root.WordRootEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootexample.RootExampleEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootmeaning.RootMeaningEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootvariant.RootVariantEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.local.room.model.words.root.rootword.RootWordEntity

@Database(
    entities = [

        // ========== Word ==========
        WordEntity::class,
        WordDefinitionEntity::class,
        WordExampleEntity::class,
        WordFormEntity::class,
        WordSynonymEntity::class,
        WordAntonymEntity::class,
        WordTagEntity::class,
        WordAssociationEntity::class,
        WordUserMetaEntity::class,
        WordFavoriteEntity::class,

        // ========== Root ==========
        WordRootEntity::class,
        RootMeaningEntity::class,
        RootVariantEntity::class,
        RootExampleEntity::class,
        RootTagEntity::class,
        RootWordEntity::class,

        // ========== WordBook ==========
        WordBookEntity::class,
        WordBookSyncStateEntity::class,
        WordBookItemEntity::class,

        // ========== Progress ==========
        WordBookProgressEntity::class,
        WordLearningStateEntity::class,
        WordStudyRecordsEntity::class,
        DailyStudyDurationEntity::class,
        CheckInRecordEntity::class,

        // ========== Practice ==========
        DailyPracticeDurationEntity::class,
        ExamPracticeWordMetaEntity::class,
        ExamPracticeItemEntity::class,
        ExamPracticeItemStateEntity::class,
        PracticeSessionRecordEntity::class,
        SyncOutboxEntity::class,

        // ========== Floating ==========
        FloatingWordDisplayRecordEntity::class
    ],
    version = 10
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // ================== Word ==================
    abstract fun wordDao(): WordDao
    abstract fun wordDefinitionDao(): WordDefinitionDao
    abstract fun wordExampleDao(): WordExampleDao
    abstract fun wordFormDao(): WordFormDao
    abstract fun wordRelationDao(): WordRelationDao
    abstract fun wordUserMetaDao(): WordUserMetaDao
    abstract fun wordFavoritesDao(): WordFavoritesDao

    // ================== Root ==================
    abstract fun wordRootDao(): WordRootDao
    abstract fun rootTagDao(): RootTagDao
    abstract fun rootWordDao(): RootWordDao

    // ================== WordBook ==================
    abstract fun wordBookDao(): WordBookDao
    abstract fun wordBookSyncStateDao(): WordBookSyncStateDao
    abstract fun wordBookItemDao(): BookWordItemDao

    // ================== Progress ==================
    abstract fun wordBookProgressDao(): WordBookProgressDao
    abstract fun wordLearningStateDao(): WordLearningStateDao
    abstract fun dailyStudyRecordsDao(): WordStudyRecordsDao
    abstract fun dailyStudyDurationDao(): DailyStudyDurationDao
    abstract fun checkInRecordDao(): CheckInRecordDao
    abstract fun dailyPracticeDurationDao(): DailyPracticeDurationDao
    abstract fun examPracticeDao(): ExamPracticeDao
    abstract fun practiceSessionRecordDao(): PracticeSessionRecordDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun floatingWordDisplayRecordDao(): FloatingWordDisplayRecordDao
}
