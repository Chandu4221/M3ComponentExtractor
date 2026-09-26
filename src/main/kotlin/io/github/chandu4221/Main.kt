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
        name in setOf("Box", "BoxWithConstraints", "Column", "Row", "Spacer", "FlowColumn", "FlowRow", "ContextualFlowColumn", "ContextualFlowRow") -> "Layout"
        name.startsWith("Lazy") || name.endsWith("Pager") -> "LazyLayout"
        name in setOf("BasicText", "BasicTextField", "BasicSecureTextField", "ClickableText", "Text", "TextField", "OutlinedTextField", "SecureTextField", "OutlinedSecureTextField") -> "Text & Input"
        name in setOf("Canvas", "Image", "SelectionContainer", "DisableSelection", "BasicTooltipBox", "VerticalDragHandle") -> "Utility"
        else -> "Component"
    }
}

fun main() {
    val factory = createPsiFactory()
    val sourceDir = File("build/extracted-sources")

    val components = parseComponents(sourceDir, factory)

    val outputFile = File("compose_components_v1.4.0.json")
    outputFile.writeText(json.encodeToString(components))

    println("Extracted ${components.size} components to ${outputFile.absolutePath}")
}

@OptIn(CompilerConfiguration.Internals::class, CoreEnvironmentDeprecation::class)
fun createPsiFactory(): KtPsiFactory {
    val disposable = Disposer.newDisposable()
    val env = KotlinCoreEnvironment.createForProduction(
        disposable,
        CompilerConfiguration.create(),
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
    return KtPsiFactory(env.project)
}


fun parseComponents(sourceDir: File, factory: KtPsiFactory): List<ComponentSchema> {
    data class ParsedFunction(
        val name: String,
        val packageName: String,
        val overload: OverloadSchema
    )

    return sourceDir.walkTopDown()
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
                    overload = OverloadSchema(
                        isExperimental = fn.annotationEntries.any {
                            it.shortName?.asString().orEmpty().startsWith("Experimental")
                        },
                        isDeprecated = false,
                        parameters = fn.valueParameters.map { param ->
                            val rawType = param.typeReference?.text.orEmpty()
                            val isSlot = rawType.contains("@Composable") && SLOT_REGEX.containsMatchIn(rawType)
                            val slotScope = if (isSlot) SCOPE_RECEIVER_REGEX.find(rawType)?.groupValues?.get(1) else null
                            val dslMatch = if (!isSlot) DSL_SCOPE_REGEX.find(rawType) else null
                            val isDslSlot = dslMatch != null
                            val dslScope = dslMatch?.groupValues?.get(1)

                            ParameterSchema(
                                name = param.name.orEmpty(),
                                type = rawType,
                                isOptional = param.hasDefaultValue(),
                                defaultValue = param.defaultValue?.text,
                                isSlot = isSlot,
                                slotScope = slotScope,
                                isDslSlot = isDslSlot,
                                dslScope = dslScope
                            )
                        }
                    )
                )
            }
        }
        .groupBy { it.packageName to it.name }
        .map { (key, group) ->
            ComponentSchema(
                name = key.second,
                packageName = key.first,
                category = determineCategory(key.second, key.first),
                overloads = group.map { it.overload }
            )
        }
        .sortedBy { it.name }
        .toList()
}