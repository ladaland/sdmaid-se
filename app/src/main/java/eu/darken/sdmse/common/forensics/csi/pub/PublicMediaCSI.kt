package eu.darken.sdmse.common.forensics.csi.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.containsSegments
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.pub.SdcardCSI.Companion.LEGACY_PATH
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class PublicMediaCSI @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_MEDIA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var primary: DataArea? = null

        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PUBLIC_MEDIA }) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area

            if (area.path.isAncestorOf(target)) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = area.path,
                    isBlackListLocation = true
                )
            }
        }

        if (target.containsSegments(*BASE_SEGMENTS) && primary != null && LEGACY_PATH.isAncestorOf(target)) {
            return AreaInfo(
                dataArea = primary,
                file = target,
                prefix = LEGACY_PATH.child(*BASE_SEGMENTS),
                isBlackListLocation = true,
            )
        }

        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        val dirNameAsPkg = areaInfo.prefixFreePath.first()
        var hiddenDirAsPkg: String? = null

        if (pkgRepo.isInstalled(dirNameAsPkg.toPkgId())) {
            owners.add(Owner(dirNameAsPkg.toPkgId()))
        } else {
            hiddenDirAsPkg = cleanDirName(dirNameAsPkg)
            if (hiddenDirAsPkg != null && pkgRepo.isInstalled(hiddenDirAsPkg.toPkgId())) {
                owners.add(Owner(hiddenDirAsPkg.toPkgId()))
            }
        }

        if (owners.isEmpty()) {
            clutterRepo.match(areaInfo.type, listOf(dirNameAsPkg))
                .map { it.toOwners() }
                .flatten()
                .run { owners.addAll(this) }
        }

        // Fallback, no downside to assuming that dirname=pkgname for PUBLIC_MEDIA if there are no other owners
        if (owners.isEmpty()) {
            owners.add(Owner((hiddenDirAsPkg ?: dirNameAsPkg).toPkgId()))
        }

        return CSIProcessor.Result(
            owners = owners,
            hasKnownUnknownOwner = false
        )
    }

    private fun cleanDirName(name: String): String? = when {
        name.startsWith(".external.") -> {
            // rare modifier, seen it on my N5 with the .external.com.plexapp.android
            name.substring(10)
        }
        name.startsWith("_") || name.startsWith(".") -> {
            // usually used in public/Android/data to hide stuff from uninstall
            // some devs just don't know better
            name.substring(1)
        }
        else -> null
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicMediaCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Public", "Media")
        val BASE_SEGMENTS = arrayOf("Android", "media")
    }
}