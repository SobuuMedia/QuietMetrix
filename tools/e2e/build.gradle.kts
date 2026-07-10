tasks.register<Exec>("e2eTest") {
    description = "Runs end-to-end contract tests against Ktor and PHP backends via Docker Compose"
    group = "verification"

    workingDir = file(".")

    val dockerRunning = try {
        val proc = ProcessBuilder("docker", "info").start()
        proc.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    if (!dockerRunning) {
        doFirst {
            logger.warn("Docker is not running — skipping e2eTest.")
            throw StopExecutionException("Docker is not running.")
        }
        isEnabled = false  // evaluated eagerly; we unconditionally let doFirst decide.
    }

    commandLine("bash", "run-e2e.sh")
}
