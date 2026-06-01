package com.chen.memorizewords.data.study.reset

import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DataStudyUserScopedDataResetContributor @Inject constructor(
    private val database: StudyDatabase,
    private val studyPlanDataSource: StudyPlanDataSource
) : UserScopedDataResetContributor {
    override suspend fun clearUserScopedData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            studyPlanDataSource.clearStudyPlan()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStudyUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindUserScopedDataResetContributor(
        impl: DataStudyUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
