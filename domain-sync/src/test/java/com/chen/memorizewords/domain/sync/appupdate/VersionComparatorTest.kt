package com.chen.memorizewords.domain.sync.appupdate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionComparatorTest {
    @Test
    fun `version code has priority over version name`() {
        assertTrue(
            VersionComparator.isRemoteNewer(
                current = AppVersion(versionName = "9.9.9", versionCode = 10),
                remote = AppVersion(versionName = "1.0.0", versionCode = 11)
            )
        )
    }

    @Test
    fun `version name is used when version code is equal`() {
        assertTrue(
            VersionComparator.isRemoteNewer(
                current = AppVersion(versionName = "1.2.9", versionCode = 10),
                remote = AppVersion(versionName = "1.3.0", versionCode = 10)
            )
        )
    }

    @Test
    fun `invalid version name does not create false update`() {
        assertFalse(
            VersionComparator.isRemoteNewer(
                current = AppVersion(versionName = "release", versionCode = 10),
                remote = AppVersion(versionName = "beta", versionCode = 10)
            )
        )
    }
}
