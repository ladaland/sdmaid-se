package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.crumbsTo
import eu.darken.sdmse.common.files.core.local.isAncestorOf
import eu.darken.sdmse.common.files.core.local.isParentOf
import eu.darken.sdmse.common.files.core.local.removePrefix
import eu.darken.sdmse.common.files.core.local.startsWith
import eu.darken.sdmse.common.files.core.saf.*
import eu.darken.sdmse.common.files.core.saf.crumbsTo
import eu.darken.sdmse.common.files.core.saf.isAncestorOf
import eu.darken.sdmse.common.files.core.saf.isParentOf
import eu.darken.sdmse.common.files.core.saf.removePrefix
import eu.darken.sdmse.common.files.core.saf.startsWith
import okio.Sink
import okio.Source
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.*

fun APath.crumbsTo(child: APath): Array<String> {
    require(this.pathType == child.pathType)

    return when (pathType) {
        APath.PathType.RAW -> (this as RawPath).crumbsTo(child as RawPath)
        APath.PathType.LOCAL -> (this as LocalPath).crumbsTo(child as LocalPath)
        APath.PathType.SAF -> (this as SAFPath).crumbsTo(child as SAFPath)
    }
}

@Suppress("UNCHECKED_CAST")
fun <P : APath> P.childCast(vararg segments: String): P = child(*segments) as P

fun APath.asFile(): File = when (this) {
    is LocalPath -> this.file
    else -> File(this.path)
}

fun <P : APath, PL : APathLookup<P>, GT : APathGateway<P, PL>> P.walk(
    gateway: GT,
    filter: (PL) -> Boolean = { true }
): PathTreeFlow<P, PL, GT> {
    return PathTreeFlow(gateway, downCast(), filter)
}

/**
 * // FIXME not sure if this can be fixed?
 * APathLookup is a super of APath, but LocalPathLookup is not a super of LocalPath
 * The compiler allows us to pass a LocalPathLookup to a function that only takesk LocalPath
 */
internal fun <T : APath> T.downCast(): T = if (this is APathLookup<*>) {
    @Suppress("UNCHECKED_CAST")
    this.lookedUp as T
} else {
    this
}

suspend fun <T : APath> T.exists(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.exists(downCast())
}

suspend fun <T : APath> T.requireExists(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (!exists(gateway)) {
        throw IllegalStateException("Path doesn't exist, but should: $this")
    }
    return this
}

suspend fun <T : APath> T.requireNotExists(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        throw IllegalStateException("Path exist, but shouldn't: $this")
    }
    return this
}

suspend fun <T : APath> T.createFileIfNecessary(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(downCast()).fileType == FileType.FILE) {
            log(VERBOSE) { "File already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Exists, but is not a file: $this")
        }
    }
    try {
        gateway.createFile(downCast())
        log(VERBOSE) { "File created: $this" }
        return this
    } catch (e: Exception) {
        throw IllegalStateException("Couldn't create file: $this", e)
    }
}

suspend fun <T : APath> T.createDirIfNecessary(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(downCast()).isDirectory) {
            log(VERBOSE) { "Directory already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Exists, but is not a directory: $this")
        }
    }
    try {
        gateway.createDir(downCast())
        log(VERBOSE) { "Directory created: $this" }
        return this
    } catch (e: Exception) {
        throw IllegalStateException("Couldn't create Directory: $this", e)
    }
}

suspend fun <T : APath> T.deleteAll(gateway: APathGateway<T, out APathLookup<T>>) {
    if (gateway.lookup(downCast()).isDirectory) {
        gateway.listFiles(downCast()).forEach { it.deleteAll(gateway) }
    }
    if (gateway.delete(this)) {
        log(VERBOSE) { "File.release(): Deleted $this" }
    } else if (!exists(gateway)) {
        log(WARN) { "File.release(): File didn't exist: $this" }
    } else {
        throw FileNotFoundException("Failed to delete file: $this")
    }
}

suspend fun <T : APath> T.write(gateway: APathGateway<T, out APathLookup<T>>): Sink {
    return gateway.write(downCast())
}

suspend fun <T : APath> T.read(gateway: APathGateway<T, out APathLookup<T>>): Source {
    return gateway.read(downCast())
}

suspend fun <T : APath> T.createSymlink(gateway: APathGateway<T, out APathLookup<T>>, target: T): Boolean {
    return gateway.createSymlink(downCast(), target)
}

suspend fun <T : APath> T.setModifiedAt(gateway: APathGateway<T, out APathLookup<T>>, modifiedAt: Instant): Boolean {
    return gateway.setModifiedAt(downCast(), modifiedAt)
}

suspend fun <T : APath> T.setPermissions(
    gateway: APathGateway<T, out APathLookup<T>>,
    permissions: Permissions
): Boolean {
    return gateway.setPermissions(downCast(), permissions)
}

suspend fun <T : APath> T.setOwnership(gateway: APathGateway<T, out APathLookup<T>>, ownership: Ownership): Boolean {
    return gateway.setOwnership(downCast(), ownership)
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookup(gateway: APathGateway<P, PLU>): PLU {
    return gateway.lookup(downCast())
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookupFiles(gateway: APathGateway<P, PLU>): List<PLU> {
    return gateway.lookupFiles(downCast())
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookupFilesOrNull(gateway: APathGateway<P, PLU>): List<PLU>? {
    return if (exists(gateway)) gateway.lookupFiles(downCast()) else null
}

suspend fun <T : APath> T.listFiles(gateway: APathGateway<T, out APathLookup<T>>): List<T> {
    return gateway.listFiles(downCast())
}

suspend fun <T : APath> T.listFilesOrNull(gateway: APathGateway<T, out APathLookup<T>>): List<T>? {
    return if (exists(gateway)) gateway.listFiles(downCast()) else null
}

suspend fun <T : APath> T.canRead(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.canRead(downCast())
}

suspend fun <T : APath> T.canWrite(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.canWrite(downCast())
}

suspend fun <T : APath> T.isFile(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.lookup(downCast()).fileType == FileType.FILE
}

suspend fun <T : APath> T.isDirectory(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.lookup(downCast()).fileType == FileType.DIRECTORY
}

suspend fun <T : APath> T.mkdirs(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.createDir(downCast())
}

suspend fun <T : APath> T.tryMkDirs(gateway: APathGateway<T, out APathLookup<T>>): APath {
    if (exists(gateway)) {
        if (isDirectory(gateway)) {
            log(VERBOSE) { "Directory already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Directory exists, but is not a directory: $this").also {
                log(VERBOSE) { "Directory exists, but is not a directory: $this:\n${it.asLog()}" }
            }
        }
    }

    if (mkdirs(gateway)) {
        log(VERBOSE) { "Directory created: $this" }
        return this
    } else {
        throw IllegalStateException("Couldn't create Directory: $this").also {
            log(VERBOSE) { "Couldn't create Directory: ${it.asLog()}" }
        }
    }
}

//
//    fun isChildOf(parent: SDMFile, children: Collection<SDMFile?>): Collection<SDMFile> {
//        val filteredChildren: MutableCollection<SDMFile> = HashSet<SDMFile>()
//        for (child in children) {
//            if (isChildOf(parent, child)) filteredChildren.add(child)
//        }
//        return filteredChildren
//    }
//
//    fun <T : SDMFile?> isChildOf(parents: Collection<T>, children: Collection<T>): Collection<T> {
//        val filteredChildren: MutableCollection<T> = HashSet()
//        for (parent in parents) {
//            for (child in children) {
//                if (isChildOf(parent, child)) filteredChildren.add(child)
//            }
//        }
//        return filteredChildren
//    }

fun APath.isAncestorOf(descendant: APath): Boolean {
    if (this.pathType != descendant.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $descendant)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).isAncestorOf(descendant.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).isAncestorOf(descendant.downCast() as SAFPath)
        APath.PathType.RAW -> descendant.downCast().path.startsWith(this.downCast().path + "/")
    }
}

fun APath.isDescendantOf(ancestor: APath): Boolean {
    if (this.pathType != ancestor.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $ancestor)")
    }
    return ancestor.isAncestorOf(this)
}

/**
 * A parent is a DIRECT ancestor
 * See [isAncestorOf]
 */
fun APath.isParentOf(child: APath): Boolean {
    if (this.pathType != child.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $child)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).isParentOf(child.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).isParentOf(child.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().child(child.name) == child
    }
}

fun APath.isChildOf(parent: APath): Boolean {
    if (this.pathType != parent.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $parent)")
    }
    return parent.isParentOf(this)
}

fun APath.matches(other: APath): Boolean {
    if (this.pathType != other.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $other)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).path == (other.downCast() as LocalPath).path
        APath.PathType.SAF -> (this.downCast() as SAFPath).path == (other.downCast() as SAFPath).path
        APath.PathType.RAW -> other.downCast().path == this.downCast().path
    }
}

fun APath.containsSegments(vararg target: String): Boolean {
    return Collections.indexOfSubList(this.segments, target.toList()) != -1
}

fun APath.startsWith(prefix: APath): Boolean {
    if (this.pathType != prefix.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $prefix)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).startsWith(prefix.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).startsWith(prefix.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().path.startsWith(prefix.downCast().path)
    }
}

fun APath.removePrefix(prefix: APath): List<String> {
    if (this.pathType != prefix.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $prefix)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).removePrefix(prefix.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).removePrefix(prefix.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().segments.drop(prefix.downCast().segments.size)
    }
}

fun List<String>?.matches(other: List<String>?, ignoreCase: Boolean = false): Boolean {
    if (this == null) return other == null
    if (this.size != other?.size) return false
    return indices.all { this[it].equals(other[it], ignoreCase) }
}

fun List<String>?.isAncestorOf(other: List<String>?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size >= other.size) return false

    return indices.all { this[it].equals(other[it], ignoreCase) }
}

fun List<String>?.contains(other: List<String>?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false
    return if (ignoreCase) {
        Collections.indexOfSubList(this.map { it.lowercase() }, other.map { it.lowercase() }) != -1
    } else {
        Collections.indexOfSubList(this, other) != -1
    }
}

fun List<String>?.startsWith(other: List<String>?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false

    val thisCase = if (ignoreCase) this.map { it.lowercase() } else this
    val otherCase = if (ignoreCase) other.map { it.lowercase() } else other

    return when {
        thisCase.isEmpty() -> {
            otherCase.isEmpty()
        }
        otherCase.size == 1 -> {
            thisCase.first().startsWith(otherCase.first())
        }
        thisCase.size == otherCase.size -> {
            val match = otherCase.dropLast(1) == thisCase.dropLast(1)
            match && thisCase.last().startsWith(otherCase.last())
        }
        else -> {
            val match = otherCase.dropLast(1) == thisCase.dropLast(thisCase.size - otherCase.size + 1)
            match && thisCase[otherCase.size - 1].startsWith(otherCase.last())
        }
    }
}
