package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import javax.inject.Inject

class GetCurrentBusinessDateUseCase @Inject constructor(
    private val businessDateProvider: BusinessDateProvider
) {
    operator fun invoke(): String {
        return businessDateProvider.currentBusinessDate()
    }
}
