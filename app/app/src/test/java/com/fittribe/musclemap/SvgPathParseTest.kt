package com.fittribe.musclemap

import org.junit.Assert.assertTrue
import org.junit.Test

/** Every bundled figure path must parse quickly; the Profile heatmap parses them on the main thread. */
class SvgPathParseTest {
    @Test(timeout = 10_000)
    fun allBundledPathsParse() {
        for (gender in BodyGender.entries) for (side in BodySide.entries) {
            var count = 0
            for (part in BodyPathProvider.paths(gender, side)) for (svg in part.allPaths) {
                val start = System.nanoTime()
                SvgPathBuilder.build(svg)
                val ms = (System.nanoTime() - start) / 1_000_000
                assertTrue("$gender $side ${part.slug} took ${ms}ms (${svg.length} chars)", ms < 500)
                count++
            }
            assertTrue("$gender $side has no paths", count > 0)
        }
    }
}
