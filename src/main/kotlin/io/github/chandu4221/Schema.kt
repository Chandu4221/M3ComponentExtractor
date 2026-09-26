package io.github.chandu4221

import kotlinx.serialization.Serializable

@Serializable
data class ComponentSchema(
    val name: String,
    val packageName: String,
    val category: String,
    val overloads: List<OverloadSchema>
)

@Serializable
data class OverloadSchema(
    val isExperimental: Boolean,
    val isDeprecated: Boolean,
    val parameters: List<ParameterSchema>
)

@Serializable
data class ParameterSchema(
    val name: String,
    val type: String,
    val isOptional: Boolean,
    val defaultValue: String?,
    val isSlot: Boolean,
    val slotScope: String? = null,
    val isDslSlot: Boolean = false,
    val dslScope: String? = null
)