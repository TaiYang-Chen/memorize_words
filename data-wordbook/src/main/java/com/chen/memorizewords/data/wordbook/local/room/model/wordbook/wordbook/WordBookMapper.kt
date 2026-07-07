package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookContentPackage
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto

fun WordBookEntity.toDomain(isSelected: Boolean = false) = WordBook(
    id = id,
    title = title,
    category = category,
    imgUrl = imgUrl,
    description = description,
    totalWords = totalWords,
    contentVersion = contentVersion,
    contentPackage = toContentPackage(),
    isNew = isNew,
    isHot = isHot,
    isSelected = isSelected,
    isPublic = isPublic,
    createdByUserId = createdByUserId
)

fun WordBook.toEntity() = WordBookEntity(
    id = id,
    title = title,
    category = category,
    imgUrl = imgUrl,
    description = description,
    totalWords = totalWords,
    contentVersion = contentVersion,
    contentPackageUrl = contentPackage?.url,
    contentPackageSha256 = contentPackage?.sha256,
    contentPackageSizeBytes = contentPackage?.sizeBytes,
    contentPackageContentType = contentPackage?.contentType,
    contentPackageSchemaVersion = contentPackage?.schemaVersion,
    contentPackageVersion = contentPackage?.contentVersion,
    isNew = isNew,
    isHot = isHot,
    isPublic = isPublic,
    createdByUserId = createdByUserId
)

fun WordBookDto.toEntity(): WordBookEntity {
    return WordBookEntity(
        id = id,
        title = title,
        category = category,
        imgUrl = imgUrl,
        description = description,
        totalWords = totalWords,
        contentVersion = contentVersion,
        contentPackageUrl = contentPackage?.url,
        contentPackageSha256 = contentPackage?.sha256,
        contentPackageSizeBytes = contentPackage?.sizeBytes,
        contentPackageContentType = contentPackage?.contentType,
        contentPackageSchemaVersion = contentPackage?.schemaVersion,
        contentPackageVersion = contentPackage?.contentVersion,
        isNew = isNew,
        isHot = isHot,
        isPublic = isPublic,
        createdByUserId = createdByUserId
    )
}

private fun WordBookEntity.toContentPackage(): WordBookContentPackage? {
    val safeUrl = contentPackageUrl?.takeIf { it.isNotBlank() } ?: return null
    val safeSha256 = contentPackageSha256?.takeIf { it.isNotBlank() } ?: return null
    return WordBookContentPackage(
        url = safeUrl,
        sha256 = safeSha256,
        sizeBytes = contentPackageSizeBytes ?: 0L,
        contentType = contentPackageContentType.orEmpty(),
        schemaVersion = contentPackageSchemaVersion ?: 0,
        contentVersion = contentPackageVersion ?: contentVersion
    )
}
