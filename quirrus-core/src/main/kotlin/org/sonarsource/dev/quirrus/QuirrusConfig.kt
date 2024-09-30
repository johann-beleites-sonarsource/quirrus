package org.sonarsource.dev.quirrus

import java.nio.file.Path

object QuirrusConfig {
    val directory: Path = Path.of(
        System.getenv("HOME") ?: System.getenv("userprofile"), 
        ".quirrus"
    )
}
