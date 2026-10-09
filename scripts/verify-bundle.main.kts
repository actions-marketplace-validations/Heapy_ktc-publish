#!/usr/bin/env kotlinr
// Verify the CI Maven Central bundle using the disposable key in GNUPGHOME.
import java.io.File
import java.util.zip.ZipFile

val root = __FILE__.canonicalFile.parentFile.parentFile
val archives = root.resolve("test/fixtures/central/build").walkTopDown().filter { it.isFile && it.extension == "zip" }.toList()
check(archives.isNotEmpty()) { "No Maven Central bundle generated" }
val output = File(requireNotNull(System.getenv("RUNNER_TEMP")) { "RUNNER_TEMP is required" }, "bundle-verification").canonicalFile
check(output.mkdir()) { "Verification directory already exists: $output" }
ZipFile(archives.first()).use { archive ->
    val entries = archive.entries().asSequence().toList()
    for (entry in entries) {
        val target = output.resolve(entry.name).canonicalFile
        check(target.toPath().startsWith(output.toPath())) { "Unexpected archive path: ${entry.name}" }
    }
    for (entry in entries) {
        val target = output.resolve(entry.name)
        if (entry.isDirectory) target.mkdirs()
        else {
            target.parentFile.mkdirs()
            archive.getInputStream(entry).use { source -> target.outputStream().use { source.copyTo(it) } }
        }
    }
}
val files = output.walkTopDown().filter { it.isFile }.toList()
check(files.any { it.extension == "pom" }) { "Missing POM" }
check(files.any { it.name.endsWith("-sources.jar") }) { "Missing sources" }
val signatures = files.filter { it.extension == "asc" }
check(signatures.isNotEmpty()) { "Missing signatures" }
for (signature in signatures) {
    val result = ProcessBuilder("gpg", "--batch", "--verify", signature.path, signature.path.removeSuffix(".asc")).inheritIO().start().waitFor()
    check(result == 0) { "Invalid signature: $signature" }
}
println("Verified ${archives.first()}: POM, sources and ${signatures.size} signatures")
