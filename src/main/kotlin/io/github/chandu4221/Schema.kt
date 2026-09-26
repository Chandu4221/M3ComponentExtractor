package io.github.chandu4221

import kotlinx.serialization.Serializable

@Serializable
data class ComponentCatalog(
    val composeMultiplatformVersion: String = "1.7.3",
    val material3Version: String = "1.4.0",
    val totalCount: Int,
    val components: List<ComponentDefinition>
)

@Serializable
data class ComponentDefinition(
    val id: String,
    val displayName: String,
    val packageName: String,
    val category: String,
    val tier: String = "STANDARD",
    val isExperimental: Boolean = false,
    val experimentalAnnotations: List<String> = emptyList(),
    val isDeprecated: Boolean = false,
    val deprecation: String? = null,
    val receiverScope: String? = null,
    val parameters: List<ComponentParameter> = emptyList(),
    val callbacks: List<ComponentCallback> = emptyList(),
    val slots: List<ComponentSlot> = emptyList()
)

@Serializable
data class ComponentParameter(
    val name: String,
    val type: String,
    val rawKotlinType: String? = null,
    val isNullable: Boolean = false,
    val hasDefault: Boolean = true
)

@Serializable
data class ComponentCallback(
    val name: String,
    val signature: String? = null,
    val hasDefault: Boolean = false
)

@Serializable
data class ComponentSlot(
    val name: String,
    val receiverScope: String? = null,
    val cardinality: String = "SINGLE",
    val takesParameter: Boolean = false,
    val parameterName: String? = null,
    val parameterType: String? = null,
    val hasDefault: Boolean = false,
    val isComposable: Boolean = true
)