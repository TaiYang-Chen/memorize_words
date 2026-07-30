package com.chen.memorizewords.feature.home.ui.profile

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.account.model.membership.MembershipCheckInReward
import com.chen.memorizewords.domain.account.model.membership.MembershipRedeemResult
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import com.chen.memorizewords.domain.account.usecase.membership.CheckInMembershipUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.RefreshMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.RedeemMembershipCodeUseCase
import com.chen.memorizewords.domain.practice.usage.EvaluationPolicy
import com.chen.memorizewords.domain.practice.usage.EvaluationTier
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.domain.practice.usage.ObservePracticeUsageUseCase
import com.chen.memorizewords.domain.practice.usage.PracticeUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsageRepository
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.domain.practice.usage.RefreshPracticeUsageUseCase
import com.chen.memorizewords.feature.home.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProMembershipViewModelTest {

    @Test
    fun `redeem clears code updates membership and refreshes usage`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val membershipRepository = FakeMembershipRepository()
                val practiceUsageRepository = FakePracticeUsageRepository()
                val viewModel = ProMembershipViewModel(
                    observeMembershipStatusUseCase = ObserveMembershipStatusUseCase(membershipRepository),
                    refreshMembershipStatusUseCase = RefreshMembershipStatusUseCase(membershipRepository),
                    checkInMembershipUseCase = CheckInMembershipUseCase(membershipRepository),
                    redeemMembershipCodeUseCase = RedeemMembershipCodeUseCase(membershipRepository),
                    observePracticeUsageUseCase = ObservePracticeUsageUseCase(practiceUsageRepository),
                    refreshPracticeUsageUseCase = RefreshPracticeUsageUseCase(practiceUsageRepository),
                    resourceProvider = FakeResourceProvider()
                )
                val events = mutableListOf<UiEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect { }
                }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiEvent.collect(events::add)
                }
                advanceUntilIdle()

                viewModel.onRedeemCodeChanged("ABCDE-FGHJK-LMNPQ-RSTUV")
                runCurrent()
                assertTrue(viewModel.uiState.value.redeemEnabled)

                viewModel.redeemCode()
                advanceUntilIdle()

                assertEquals(listOf("ABCDE-FGHJK-LMNPQ-RSTUV"), membershipRepository.redeemedCodes)
                assertTrue(membershipRepository.status.value.active)
                assertEquals("", viewModel.uiState.value.redeemCode)
                assertFalse(viewModel.uiState.value.redeemEnabled)
                assertEquals(2, practiceUsageRepository.refreshCount)
                assertEquals(
                    "${R.string.feature_home_membership_redeem_success}:30",
                    (events.last() as UiEvent.Toast).message
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeMembershipRepository : MembershipRepository {
        val status = MutableStateFlow(MembershipStatus(active = false))
        val redeemedCodes = mutableListOf<String>()

        override fun observeStatus(): Flow<MembershipStatus?> = status.asStateFlow()

        override suspend fun getCachedStatus(): MembershipStatus = status.value

        override suspend fun refreshStatus(): Result<MembershipStatus> = Result.success(status.value)

        override suspend fun checkIn(): Result<MembershipCheckInReward> =
            Result.success(
                MembershipCheckInReward(
                    granted = true,
                    grantDays = 1,
                    rewardDate = "2026-06-24",
                    membership = status.value
                )
            )

        override suspend fun redeem(code: String): Result<MembershipRedeemResult> {
            redeemedCodes += code
            val membership = MembershipStatus(
                active = true,
                validUntilAtMs = 1_782_759_540_000L,
                remainingDays = 30,
                totalGrantedDays = 30
            )
            status.value = membership
            return Result.success(MembershipRedeemResult(grantDays = 30, membership = membership))
        }
    }

    private class FakePracticeUsageRepository : PracticeUsageRepository {
        var refreshCount = 0

        override fun observe(): Flow<PracticeUsageState> =
            MutableStateFlow(PracticeUsageState.Unknown).asStateFlow()

        override suspend fun refresh(): Result<PracticeUsage> {
            refreshCount += 1
            return Result.success(
                PracticeUsage(
                    serverTimeMs = 0L,
                    ttsAvailable = true,
                    ttsUnlimitedDaily = false,
                    evaluation = EvaluationUsage(
                        tier = EvaluationTier.MEMBER,
                        dailyLimit = 200,
                        used = 0,
                        remaining = 200,
                        resetAtMs = 0L,
                        policy = EvaluationPolicy(freeDailyLimit = 20, memberDailyLimit = 200)
                    )
                )
            )
        }

        override suspend fun updateEvaluationUsage(usage: EvaluationUsage) = Unit

        override suspend fun clear() = Unit
    }

    private class FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String =
            if (formatArgs.isEmpty()) resId.toString() else "$resId:${formatArgs.joinToString()}"
    }
}
