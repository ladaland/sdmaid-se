package eu.darken.sdmse.common.forensics.csi.misc

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import javax.inject.Inject

@Reusable
class DataSDExt2CSI @Inject constructor(
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {
    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.DATA_SDEXT2

    override suspend fun identifyArea(target: APath): AreaInfo? =
        areaManager.currentAreas()
            .filter { it.type == DataArea.Type.DATA_SDEXT2 }
            .mapNotNull { area ->
                if (!area.path.isAncestorOf(target)) return@mapNotNull null

                AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = area.path,
                    isBlackListLocation = false
                )
            }
            .singleOrNull()

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DataSDExt2CSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Data", "SDExt2")
    }
}