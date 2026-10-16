package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class UpdateVersionPolicyTest {
 @Test fun sameVersionCannotBeInstalledAgain(){assertFalse(UpdateVersionPolicy.isNewer(102,102))}
 @Test fun downgradeCannotBeInstalled(){assertFalse(UpdateVersionPolicy.isNewer(102,103))}
 @Test fun newerReleaseIsAccepted(){assertTrue(UpdateVersionPolicy.isNewer(104,103))}
 @Test fun oldDownloadedApkCannotHideLatestRelease(){assertFalse(UpdateVersionPolicy.canReuse(102,100,104))}
 @Test fun installedDownloadedApkCannotBeResumed(){assertFalse(UpdateVersionPolicy.canReuse(102,102,104))}
 @Test fun matchingNewerCacheCanResume(){assertTrue(UpdateVersionPolicy.canReuse(104,103,104))}
 @Test fun cacheWithoutReleaseCheckStillRejectsReinstallation(){assertFalse(UpdateVersionPolicy.canReuse(102,102,null))}
}
