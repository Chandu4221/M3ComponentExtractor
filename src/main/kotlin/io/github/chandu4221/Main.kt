package io.github.chandu4221

import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val EXCLUDED_COMPONENTS = setOf("MaterialTheme", "ProvideTextStyle")
private val SLOT_REGEX = Regex("""->\s*Unit[\s\)?]*$""")
private val SCOPE_RECEIVER_REGEX = Regex("""([A-Za-z0-9_]+Scope)\.\(""")
private val DSL_SCOPE_REGEX = Regex("""^(Lazy[A-Za-z0-9_]*Scope|AppBar[A-Za-z0-9_]*Scope)\.\(\)\s*->\s*Unit$""")

fun determineCategory(name: String, packageName: String): String {
    return when {
        // NAVIGATION: TopAppBars, NavigationBar, NavigationRail, Drawers, Tabs, Menus, Pagers
        name.contains("TopAppBar") || name.contains("NavigationBar") || name.contains("NavigationRail") ||
        name.contains("Drawer") || name.contains("Tab") || name.contains("Menu") || name.endsWith("Pager") ||
        name.contains("BottomAppBar") || name.contains("Breadcrumbs") || name.contains("Pagination") -> "NAVIGATION"

        // LAYOUT: Containers, Columns, Rows, Boxes, Scaffolds, Grids, Spacers, Dividers
        name in setOf("Scaffold", "BottomSheetScaffold", "Box", "BoxWithConstraints", "Column", "Row", "Spacer",
            "FlowColumn", "FlowRow", "ContextualFlowColumn", "ContextualFlowRow", "VerticalDivider", "HorizontalDivider") ||
        name.startsWith("Lazy") -> "LAYOUT"

        // SURFACE: Cards, Surfaces, BottomSheets, Dialogs
        name.contains("Card") || name == "Surface" || name.contains("Sheet") || name.contains("Dialog") ||
        name.contains("Popup") || name.contains("Tooltip") -> "SURFACE"

        // INPUT: Buttons, TextFields, Chips, Toggles, Sliders, Checkboxes, Radios, Pickers
        name.contains("Button") || name.contains("TextField") || name.contains("Chip") ||
        name.contains("Switch") || name.contains("Slider") || name.contains("Checkbox") ||
        name.contains("RadioButton") || name.contains("Picker") || name.contains("Segmented") -> "INPUT"

        // DISPLAY: Text, Icon, Image, Badges, Progress, Canvas, Labels
        name in setOf("Text", "BasicText", "Icon", "Image", "Badge", "BadgedBox", "Canvas", "Avatar", "ListItem") ||
        name.contains("ProgressIndicator") || name.contains("Indicator") -> "DISPLAY"

        else -> "DISPLAY"
    }
}

fun inferParameterType(name: String, rawType: String): String {
    val clean = rawType.removeSuffix("?").trim()
    return when {
        clean == "Boolean" -> "BOOLEAN"
        clean == "String" || clean == "CharSequence" -> "STRING"
        clean == "Int" || clean == "Long" -> "INT"
        clean == "Float" || clean == "Double" -> "FLOAT"
        clean == "Dp" -> "DP"
        clean == "TextUnit" -> "SP"
        clean == "Color" -> "COLOR"
        clean == "Shape" -> "SHAPE"
        clean == "Modifier" -> "MODIFIER"
        clean == "PaddingValues" -> "PADDING_VALUES"
        clean.contains("Alignment") -> "ALIGNMENT"
        clean.contains("Arrangement") -> "ARRANGEMENT"
        clean.contains("Colors") || clean.contains("Elevation") || clean.contains("Border") -> "STYLING"
        clean.contains("TextStyle") -> "TEXT_STYLE"
        clean.contains("InteractionSource") -> "INTERACTION"
        clean.contains("ImageVector") -> "IMAGE_VECTOR"
        clean.contains("WindowInsets") -> "WINDOW_INSETS"
        clean.contains("ContentScale") -> "CONTENT_SCALE"
        name.contains("Color", ignoreCase = true) -> "COLOR"
        name.contains("Shape", ignoreCase = true) -> "SHAPE"
        name.contains("Elevation", ignoreCase = true) -> "STYLING"
        name.contains("Border", ignoreCase = true) -> "STYLING"
        else -> "CUSTOM"
    }
}

fun inferSlotCardinality(slotName: String, slotScope: String?): String {
    return when {
        // Explicit single slots in Jetpack Compose
        slotName in setOf(
            "title", "label", "icon", "leadingIcon", "trailingIcon",
            "topBar", "bottomBar", "floatingActionButton", "snackbarHost",
            "badge", "thumb", "track", "handle", "confirmButton", "dismissButton",
            "navigationIcon", "header", "footer", "placeholder", "supportingText",
            "indicator"
        ) -> "SINGLE"

        // Multi-child container scopes
        slotScope in setOf("ColumnScope", "RowScope", "BoxScope", "LazyItemScope", "LazyListScope", "LazyGridScope") -> "MULTIPLE"
        slotName in setOf("content", "actions", "tabs", "items") -> "MULTIPLE"

        else -> "SINGLE"
    }
}

@OptIn(CompilerConfiguration.Internals::class, CoreEnvironmentDeprecation::class)
fun main() {
    val disposable = Disposer.newDisposable()
    try {
        val env = KotlinCoreEnvironment.createForProduction(
            disposable,
            CompilerConfiguration.create(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        val factory = KtPsiFactory(env.project)
        val sourceDir = File("build/extracted-sources")

        val catalog = parseCatalog(sourceDir, factory)

        val outputFile = File("compose-catalog.json")
        outputFile.writeText(json.encodeToString(catalog))

        println("Extracted ${catalog.totalCount} components to ${outputFile.absolutePath}")
    } finally {
        Disposer.dispose(disposable)
    }
}

fun parseCatalog(sourceDir: File, factory: KtPsiFactory): ComponentCatalog {
    data class ParsedFunction(
        val name: String,
        val packageName: String,
        val fn: KtNamedFunction
    )

    val allFunctions = sourceDir.walkTopDown()
        .filter { it.extension == "kt" && !it.path.contains("commonStubsMain") }
        .flatMap { file ->
            val ktFile = factory.createFile(file.name, file.readText())
            val pkg = ktFile.packageFqName.asString()

            ktFile.declarations.filterIsInstance<KtNamedFunction>().filter { fn ->
                val name = fn.name.orEmpty()
                val isDeprecated = fn.annotationEntries.any { it.shortName?.asString() == "Deprecated" }
                val isActual = fn.hasModifier(KtTokens.ACTUAL_KEYWORD)
                !isDeprecated &&
                    !isActual &&
                    fn.visibilityModifierTypeOrDefault() == KtTokens.PUBLIC_KEYWORD &&
                    fn.annotationEntries.any { it.shortName?.asString() == "Composable" } &&
                    name.firstOrNull()?.isUpperCase() == true &&
                    name !in EXCLUDED_COMPONENTS &&
                    (fn.typeReference == null || fn.typeReference?.text == "Unit")
            }.map { fn ->
                ParsedFunction(
                    name = fn.name.orEmpty(),
                    packageName = pkg,
                    fn = fn
                )
            }
        }
        .groupBy { it.packageName to it.name }

    val components = allFunctions.mapNotNull { (key, functionList) ->
        val pkg = key.first
        val name = key.second

        // Select the primary overload:
        // 1. Prefer non-experimental over experimental
        // 2. Prefer overload with highest parameter count (richest API)
        val sortedFns = functionList.map { it.fn }.sortedWith(
            compareBy<KtNamedFunction> { fn ->
                fn.annotationEntries.any { it.shortName?.asString().orEmpty().startsWith("Experimental") }
            }.thenByDescending { fn ->
                fn.valueParameters.size
            }
        )
        val bestFn = sortedFns.firstOrNull() ?: return@mapNotNull null

        val isExperimental = bestFn.annotationEntries.any {
            it.shortName?.asString().orEmpty().startsWith("Experimental")
        }

        val parameters = mutableListOf<ComponentParameter>()
        val callbacks = mutableListOf<ComponentCallback>()
        val slots = mutableListOf<ComponentSlot>()

        for (param in bestFn.valueParameters) {
            val paramName = param.name.orEmpty()
            val rawType = param.typeReference?.text.orEmpty()

            val isSlot = rawType.contains("@Composable") && SLOT_REGEX.containsMatchIn(rawType)
            val slotScope = if (isSlot) SCOPE_RECEIVER_REGEX.find(rawType)?.groupValues?.get(1) else null
            val dslMatch = if (!isSlot) DSL_SCOPE_REGEX.find(rawType) else null
            val isDslSlot = dslMatch != null
            val dslScope = dslMatch?.groupValues?.get(1)

            val effectiveScope = slotScope ?: dslScope

            when {
                isSlot || isDslSlot -> {
                    slots.add(
                        ComponentSlot(
                            name = paramName,
                            receiverScope = effectiveScope,
                            cardinality = inferSlotCardinality(paramName, effectiveScope),
                            hasDefault = param.hasDefaultValue(),
                            isComposable = true
                        )
                    )
                }
                paramName == "modifier" -> {
                    // Modifiers are handled via separate modifier pipeline in ComposeStudio
                }
                rawType.contains("-> Unit") || paramName.startsWith("on") || paramName == "onClick" -> {
                    callbacks.add(
                        ComponentCallback(
                            name = paramName,
                            signature = rawType,
                            hasDefault = param.hasDefaultValue()
                        )
                    )
                }
                else -> {
                    parameters.add(
                        ComponentParameter(
                            name = paramName,
                            type = inferParameterType(paramName, rawType),
                            rawKotlinType = rawType,
                            isNullable = rawType.contains("?"),
                            hasDefault = param.hasDefaultValue()
                        )
                    )
                }
            }
        }

        ComponentDefinition(
            id = name,
            displayName = name,
            packageName = pkg,
            category = determineCategory(name, pkg),
            tier = "STANDARD",
            isExperimental = isExperimental,
            parameters = parameters,
            callbacks = callbacks,
            slots = slots
        )
    }.sortedBy { it.id }

    return ComponentCatalog(
        composeMultiplatformVersion = "1.7.3",
        material3Version = "1.4.0",
        totalCount = components.size,
        components = components
    )
}