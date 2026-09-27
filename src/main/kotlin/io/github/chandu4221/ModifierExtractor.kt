package io.github.chandu4221

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File

private val EXCLUDED_MODIFIER_NAMES = setOf(
    "composed",
    "toolingGraphicsLayer",
    "modifierLocalConsumer",
    "modifierLocalProvider",
    "recalculateWindowInsets"
)

val MODIFIER_CATEGORIES = listOf(
    "SPACING",
    "WINDOW_INSETS",
    "SIZING",
    "DRAWING",
    "GRAPHICS",
    "ACTIONS",
    "FOCUS",
    "ALIGNMENT",
    "ANIMATION",
    "SCROLL",
    "POINTER",
    "SEMANTICS",
    "LAYOUT_OBSERVERS",
    "KEY_INPUT",
    "OTHER"
)

fun determineModifierCategory(name: String, receiverScope: String?): String {
    return when {
        // WINDOW_INSETS: windowInsets, statusBarsPadding, imePadding, etc.
        name.contains("WindowInsets") || name.endsWith("BarsPadding") || (name.startsWith("safe") && name.endsWith("Padding")) ||
            name in setOf("waterfallPadding", "displayCutoutPadding", "captionBarPadding", "mandatorySystemGesturesPadding", "systemGesturesPadding", "consumeWindowInsets") -> "WINDOW_INSETS"

        // SPACING: padding, offset, paddingFrom...
        name.contains("padding", ignoreCase = true) || name.contains("offset", ignoreCase = true) -> "SPACING"

        // ALIGNMENT: align, alignBy, alignByBaseline
        name == "align" || name.startsWith("alignBy") -> "ALIGNMENT"

        // SIZING: size, width, height, fillMax..., wrapContent..., aspectRatio, weight, matchParentSize, etc.
        name.contains("size", ignoreCase = true) || name.contains("width", ignoreCase = true) ||
            name.contains("height", ignoreCase = true) || name.startsWith("fill") || name.startsWith("wrap") ||
            name in setOf("aspectRatio", "weight", "matchParentSize", "defaultMinSize", "minimumInteractiveComponentSize") -> "SIZING"

        // DRAWING: background, border, shadow, drawBehind, drawWithCache, drawWithContent, paint
        name.startsWith("draw") || name in setOf("background", "border", "shadow", "paint") -> "DRAWING"

        // GRAPHICS: graphicsLayer, alpha, rotate, scale, clip, blur, transformable
        name in setOf("graphicsLayer", "alpha", "rotate", "scale", "clip", "clipToBounds", "blur", "transformable") -> "GRAPHICS"

        // ACTIONS: clickable, selectable, toggleable, draggable, swipeable, pullToRefresh
        name.contains("clickable", ignoreCase = true) || name.contains("selectable", ignoreCase = true) ||
            name.contains("toggleable", ignoreCase = true) || name.contains("draggable", ignoreCase = true) ||
            name in setOf("swipeable", "pullToRefresh") -> "ACTIONS"

        // FOCUS: focusable, focusRequester, focusProperties, focusTarget, onFocus...
        name.startsWith("focus") || name.contains("Focus") -> "FOCUS"

        // ANIMATION: animateContentSize, animateItem, animateBounds, basicMarquee
        name.startsWith("animate") || name == "basicMarquee" -> "ANIMATION"

        // SCROLL: scrollable, horizontalScroll, verticalScroll, nestedScroll, overscroll
        name.contains("scroll", ignoreCase = true) || name.contains("overscroll", ignoreCase = true) ||
            name == "clipScrollableContainer" -> "SCROLL"

        // POINTER: pointerInput, hoverable, pointerHoverIcon, stylusHoverIcon, dragAndDrop...
        name.startsWith("pointer") || name.contains("hover", ignoreCase = true) || name.startsWith("dragAndDrop") -> "POINTER"

        // KEY_INPUT: onKeyEvent, onPreviewKeyEvent, onRotaryScrollEvent, etc.
        name.contains("KeyEvent") || name.contains("RotaryScroll") || name.contains("InterceptKey") -> "KEY_INPUT"

        // SEMANTICS: semantics, clearAndSetSemantics, testTag, progressSemantics, sensitiveContent
        name.contains("semantics", ignoreCase = true) || name in setOf("testTag", "sensitiveContent", "progressSemantics") -> "SEMANTICS"

        // LAYOUT_OBSERVERS: onGloballyPositioned, onSizeChanged, onPlaced, layout, layoutId
        name.startsWith("on") || name in setOf("layout", "layoutId", "approachLayout", "zIndex") -> "LAYOUT_OBSERVERS"

        else -> "OTHER"
    }
}

fun inferModifierParamType(paramName: String, rawType: String): String {
    val clean = rawType.removeSuffix("?").trim()
    return when {
        clean == "Boolean" -> "BOOLEAN"
        clean == "String" || clean == "CharSequence" -> "STRING"
        clean == "Int" || clean == "Long" -> "INT"
        clean == "Float" || clean == "Double" -> "FLOAT"
        clean == "Dp" -> "DP"
        clean == "DpSize" -> "DP_SIZE"
        clean == "TextUnit" -> "SP"
        clean == "PaddingValues" -> "PADDING_VALUES"
        clean == "Color" -> "COLOR"
        clean == "Shape" -> "SHAPE"
        clean == "Alignment" || clean.startsWith("Alignment.") -> "ALIGNMENT"
        clean.contains("AlignmentLine") -> "ALIGNMENT"
        clean.contains("WindowInsets") -> "WINDOW_INSETS"
        clean.contains("InteractionSource") -> "INTERACTION"
        clean.contains("Offset") -> "OFFSET"
        clean.contains("Role") -> "ROLE"
        clean.contains("State") -> "STATE"
        clean.contains("Spec") -> "ANIMATION_SPEC"
        clean.contains("TextStyle") -> "TEXT_STYLE"
        clean.contains("ContentScale") -> "CONTENT_SCALE"
        clean.contains("-> Unit") || clean.contains("->") -> "CALLBACK"
        paramName.contains("Color", ignoreCase = true) -> "COLOR"
        paramName.contains("Shape", ignoreCase = true) -> "SHAPE"
        paramName.contains("Elevation", ignoreCase = true) -> "STYLING"
        paramName.contains("Border", ignoreCase = true) -> "STYLING"
        else -> "CUSTOM"
    }
}

private fun isValidModifierCandidate(fn: KtNamedFunction, name: String): Boolean {
    if (name.isEmpty() || name in EXCLUDED_MODIFIER_NAMES) return false
    if (name.first().isUpperCase()) return false
    val isDeprecated = fn.annotationEntries.any { it.shortName?.asString() == "Deprecated" }
    val isActual = fn.hasModifier(KtTokens.ACTUAL_KEYWORD)
    val isPublic = fn.visibilityModifierTypeOrDefault() == KtTokens.PUBLIC_KEYWORD
    return !isDeprecated && !isActual && isPublic
}

private fun isReturnsModifier(fn: KtNamedFunction, receiver: String?): Boolean {
    val returnType = fn.typeReference?.text
    if (returnType != null) {
        return returnType.contains("Modifier")
    }
    if (fn.hasBlockBody()) {
        return false
    }
    return receiver == "Modifier"
}

fun parseModifiers(sourceDir: File, factory: KtPsiFactory): ModifierCatalog {
    data class RawModifier(
        val name: String,
        val packageName: String,
        val receiverScope: String?,
        val fn: KtNamedFunction
    )

    val rawList = mutableListOf<RawModifier>()

    sourceDir.walkTopDown()
        .filter { it.extension == "kt" && !it.path.contains("commonStubsMain") }
        .forEach { file ->
            val ktFile = factory.createFile(file.name, file.readText())
            val pkg = ktFile.packageFqName.asString()

            // 1. Top-level functions
            ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { fn ->
                val name = fn.name.orEmpty()
                if (isValidModifierCandidate(fn, name)) {
                    val receiver = fn.receiverTypeReference?.text
                    if (receiver == "Modifier" && isReturnsModifier(fn, receiver)) {
                        rawList.add(RawModifier(name, pkg, null, fn))
                    } else if (receiver != null && receiver.endsWith("Scope") && isReturnsModifier(fn, receiver)) {
                        rawList.add(RawModifier(name, pkg, receiver, fn))
                    }
                }
            }

            // 2. Member functions of *Scope interfaces/classes
            ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { cls ->
                val scopeName = cls.name.orEmpty()
                if (scopeName.endsWith("Scope")) {
                    cls.declarations.filterIsInstance<KtNamedFunction>().forEach { fn ->
                        val name = fn.name.orEmpty()
                        if (isValidModifierCandidate(fn, name)) {
                            val receiver = fn.receiverTypeReference?.text
                            if (receiver == "Modifier" && isReturnsModifier(fn, receiver)) {
                                rawList.add(RawModifier(name, pkg, scopeName, fn))
                            }
                        }
                    }
                }
            }
        }

    val grouped = rawList.groupBy { (if (it.receiverScope != null) "${it.receiverScope}.${it.name}" else it.name) }

    val modifiers = grouped.map { (id, items) ->
        val first = items.first()
        val name = first.name
        val receiverScope = first.receiverScope
        val pkg = first.packageName

        val overloads = items.map { item ->
            val fn = item.fn
            val isExp = fn.annotationEntries.any { it.shortName?.asString().orEmpty().startsWith("Experimental") }
            val params = fn.valueParameters.map { p ->
                val pName = p.name.orEmpty()
                val rawType = p.typeReference?.text.orEmpty()
                ComponentParameter(
                    name = pName,
                    type = inferModifierParamType(pName, rawType),
                    rawKotlinType = rawType,
                    isNullable = rawType.contains("?"),
                    hasDefault = p.hasDefaultValue()
                )
            }
            Triple(isExp, params, fn)
        }.distinctBy { (_, params) ->
            params.map { it.name to it.rawKotlinType }
        }.sortedWith(
            compareBy<Triple<Boolean, List<ComponentParameter>, KtNamedFunction>> { it.first }
                .thenByDescending { it.second.size }
        )

        val best = overloads.first()
        val isExperimental = best.first
        val primaryParams = best.second

        ModifierDefinition(
            id = id,
            name = name,
            packageName = pkg,
            category = determineModifierCategory(name, receiverScope),
            receiverScope = receiverScope,
            isUniversal = receiverScope == null,
            isExperimental = isExperimental,
            parameters = primaryParams,
            overloads = overloads.map { ModifierOverload(parameters = it.second) }
        )
    }.sortedWith(
        compareBy<ModifierDefinition> { !it.isUniversal }
            .thenBy { it.category }
            .thenBy { it.id }
    )

    return ModifierCatalog(
        composeVersion = "1.8.1",
        totalCount = modifiers.size,
        categories = MODIFIER_CATEGORIES,
        modifiers = modifiers
    )
}
