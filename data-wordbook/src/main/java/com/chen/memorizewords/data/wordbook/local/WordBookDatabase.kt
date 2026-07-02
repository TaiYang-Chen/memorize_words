package com.chen.memorizewords.data.wordbook.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate.WordBookSyncStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate.WordBookSyncStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.WordBookItemEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordTagEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootexample.RootExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootmeaning.RootMeaningEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootvariant.RootVariantEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.RootWordEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity

@Database(
    entities = [
        WordBookEntity::class,
        CurrentWordBookSelectionEntity::class,
        WordBookSyncStateEntity::class,
        WordBookItemEntity::class,
        WordBookProgressEntity::class,
        WordLearningStateEntity::class,
        WordEntity::class,
        WordDefinitionEntity::class,
        WordExampleEntity::class,
        WordFormEntity::class,
        WordUserMetaEntity::class,
        WordSynonymEntity::class,
        WordAntonymEntity::class,
        WordTagEntity::class,
        WordAssociationEntity::class,
        WordRootEntity::class,
        RootTagEntity::class,
        RootMeaningEntity::class,
        RootVariantEntity::class,
        RootExampleEntity::class,
        RootWordEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(WordBookRoomConverters::class)
abstract class WordBookDatabase : RoomDatabase() {
    abstract fun wordBookDao(): WordBookDao
    abstract fun currentWordBookSelectionDao(): CurrentWordBookSelectionDao
    abstract fun wordBookSyncStateDao(): WordBookSyncStateDao
    abstract fun wordBookItemDao(): BookWordItemDao
    abstract fun wordBookProgressDao(): WordBookProgressDao
    abstract fun wordLearningStateDao(): WordLearningStateDao
    abstract fun wordDao(): WordDao
    abstract fun wordDefinitionDao(): WordDefinitionDao
    abstract fun wordExampleDao(): WordExampleDao
    abstract fun wordFormDao(): WordFormDao
    abstract fun wordUserMetaDao(): WordUserMetaDao
    abstract fun wordRelationDao(): WordRelationDao
    abstract fun wordRootDao(): WordRootDao
    abstract fun rootTagDao(): RootTagDao
    abstract fun rootWordDao(): RootWordDao
}
