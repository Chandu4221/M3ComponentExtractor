package io.github.chandu4221

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModifierExtractorTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @OptIn(CompilerConfiguration.Internals::class, CoreEnvironmentDeprecation::class)
    @Test
    fun testParseModifiers() {
        val disposable = Disposer.newDisposable()
        try {
            val env = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration.create(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            val factory = KtPsiFactory(env.project)
            val sourceDir = File("build/extracted-sources")

            val catalog = parseModifiers(sourceDir, factory)

            println("Extracted ${catalog.totalCount} modifiers")
            assertTrue(catalog.totalCount > 50, "Expected at least 50 modifiers, found ${catalog.totalCount}")

            val byId = catalog.modifiers.associateBy { it.id }

            // Verify essential universal modifiers
            val padding = byId["padding"]
            assertNotNull(padding, "padding should exist")
            assertEquals("SPACING", padding.category)
            assertTrue(padding.isUniversal)
            assertTrue(padding.parameters.isNotEmpty())
            assertTrue(padding.overloads.size >= 2, "padding should have multiple overloads")

            val size = byId["size"]
            assertNotNull(size, "size should exist")
            assertEquals("SIZING", size.category)
            assertTrue(size.isUniversal)

            val fillMaxWidth = byId["fillMaxWidth"]
            assertNotNull(fillMaxWidth, "fillMaxWidth should exist")
            assertEquals("SIZING", fillMaxWidth.category)
            assertTrue(fillMaxWidth.isUniversal)

            val background = byId["background"]
            assertNotNull(background, "background should exist")
            assertEquals("DRAWING", background.category)

            val clickable = byId["clickable"]
            assertNotNull(clickable, "clickable should exist")
            assertEquals("ACTIONS", clickable.category)

            val graphicsLayer = byId["graphicsLayer"]
            assertNotNull(graphicsLayer, "graphicsLayer should exist")
            assertEquals("GRAPHICS", graphicsLayer.category)

            // Verify essential scope modifiers
            val rowScopeWeight = byId["RowScope.weight"]
            assertNotNull(rowScopeWeight, "RowScope.weight should exist")
            assertEquals("RowScope", rowScopeWeight.receiverScope)
            assertEquals("SIZING", rowScopeWeight.category)
            assertTrue(!rowScopeWeight.isUniversal)

            val colScopeWeight = byId["ColumnScope.weight"]
            assertNotNull(colScopeWeight, "ColumnScope.weight should exist")
            assertEquals("ColumnScope", colScopeWeight.receiverScope)
            assertEquals("SIZING", colScopeWeight.category)

            val boxScopeMatchParent = byId["BoxScope.matchParentSize"]
            assertNotNull(boxScopeMatchParent, "BoxScope.matchParentSize should exist")
            assertEquals("BoxScope", boxScopeMatchParent.receiverScope)
            assertEquals("SIZING", boxScopeMatchParent.category)

            val lazyAnimate = byId["LazyItemScope.animateItem"]
            assertNotNull(lazyAnimate, "LazyItemScope.animateItem should exist")
            assertEquals("LazyItemScope", lazyAnimate.receiverScope)
            assertEquals("ANIMATION", lazyAnimate.category)

            // Verify JSON serialization
            val jsonString = json.encodeToString(catalog)
            assertTrue(jsonString.isNotBlank())
            val decoded = json.decodeFromString<ModifierCatalog>(jsonString)
            assertEquals(catalog.totalCount, decoded.totalCount)
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
