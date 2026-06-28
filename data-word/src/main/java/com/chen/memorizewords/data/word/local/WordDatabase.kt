package com.chen.memorizewords.data.word.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.word.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.word.local.room.model.words.definition.WordDefinitionEntity
import com.chen.memorizewords.data.word.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.word.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.word.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.word.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.data.word.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.word.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.word.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.word.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.word.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.word.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.word.local.room.model.words.relation.WordTagEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.root.RootTagDao
import com.chen.memorizewords.data.word.local.room.model.words.root.root.RootTagEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.word.local.room.model.words.root.root.WordRootEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootexample.RootExampleEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootexample.RootExampleEntityDao
import com.chen.memorizewords.data.word.local.room.model.words.root.rootmeaning.RootMeaningEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootmeaning.RootMeaningEntityDao
import com.chen.memorizewords.data.word.local.room.model.words.root.rootvariant.RootVariantEntity
import com.chen.memorizewords.data.word.local.room.model.words.root.rootvariant.RootVariantEntityDao
import com.chen.memorizewords.data.word.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.word.local.room.model.words.root.rootword.RootWordEntity
import com.chen.memorizewords.data.word.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.word.local.room.model.words.word.WordEntity

@Database(
    entities = [
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
        RootExampleEntity::class,
        RootMeaningEntity::class,
        RootVariantEntity::class,
        RootWordEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(WordRoomConverters::class)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun wordDefinitionDao(): WordDefinitionDao
    abstract fun wordExampleDao(): WordExampleDao
    abstract fun wordFormDao(): WordFormDao
    abstract fun wordUserMetaDao(): WordUserMetaDao
    abstract fun wordRelationDao(): WordRelationDao
    abstract fun wordRootDao(): WordRootDao
    abstract fun rootTagDao(): RootTagDao
    abstract fun rootExampleEntityDao(): RootExampleEntityDao
    abstract fun rootMeaningEntityDao(): RootMeaningEntityDao
    abstract fun rootVariantEntityDao(): RootVariantEntityDao
    abstract fun rootWordDao(): RootWordDao
}
